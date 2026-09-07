\set ON_ERROR_STOP on

-- Verification only: application data/schema are unchanged. Temporary catalog
-- inspection objects disappear when this NEW psql -X session closes.
SET lock_timeout = '5s';
SET statement_timeout = '60s';
SET search_path = pg_catalog, public;

SELECT current_database() AS database_name, current_user AS executing_role,
       current_setting('server_version') AS server_version;

DO $verify_columns$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_attribute
        WHERE attrelid = to_regclass('public.exchanges') AND attname = 'meeting_url'
          AND attnum > 0 AND NOT attisdropped AND NOT attnotnull
          AND (atttypid = 'pg_catalog.text'::regtype
               OR (atttypid = 'pg_catalog.varchar'::regtype AND (atttypmod = -1 OR atttypmod >= 2048 + 4)))
    ) THEN
        RAISE EXCEPTION 'meeting_url must be nullable varchar(2048 or larger), unlimited varchar, or text';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_attribute
        WHERE attrelid = to_regclass('public.skills') AND attname = 'identity_key'
          AND attnum > 0 AND NOT attisdropped
          AND atttypid = 'pg_catalog.varchar'::regtype AND atttypmod = 64 + 4
          AND NOT attnotnull AND NOT atthasdef AND attgenerated = ''
    ) THEN
        RAISE EXCEPTION 'identity_key must be nullable varchar(64), without default/generation';
    END IF;
END;
$verify_columns$;

-- Temporary inspection objects exist only in this psql session.
CREATE TEMP TABLE rollout_expected_indexes (
    table_name text NOT NULL,
    index_name text NOT NULL,
    key_columns text[] NOT NULL,
    unique_index boolean NOT NULL
);

INSERT INTO rollout_expected_indexes VALUES
    ('user_skills', 'idx_user_skills_skill_direction', ARRAY['skill_id', 'direction'], false),
    ('availabilities', 'idx_availabilities_user', ARRAY['user_id'], false),
    ('exchanges', 'idx_exchanges_requester_status_created', ARRAY['requester_id', 'status', 'created_at'], false),
    ('exchanges', 'idx_exchanges_receiver_status_created', ARRAY['receiver_id', 'status', 'created_at'], false),
    ('feedback', 'idx_feedback_author', ARRAY['author_id'], false),
    ('skills', 'ux_skills_identity_key', ARRAY['identity_key'], true);

CREATE TEMP VIEW rollout_index_status AS
SELECT expected.table_name, expected.index_name AS requested_name,
       actual.oid AS actual_oid, actual.relname AS actual_name,
       pg_get_indexdef(i.indexrelid) AS actual_definition,
       i.indisvalid AND i.indisready AND i.indislive AS usable,
       (
           method.amname = 'btree'
           AND i.indisunique = expected.unique_index
           AND NOT i.indisexclusion
           AND i.indimmediate
           AND i.indnkeyatts = cardinality(expected.key_columns)
           AND i.indnatts = i.indnkeyatts
           AND i.indexprs IS NULL AND i.indpred IS NULL
           -- PostgreSQL 14 lacks this catalog field; missing means the older
           -- NULLS DISTINCT behavior. Newer NULLS NOT DISTINCT is incompatible.
           AND NOT coalesce((to_jsonb(i)->>'indnullsnotdistinct')::boolean, false)
           AND ARRAY(
               SELECT attribute.attname::text
               FROM unnest(i.indkey::smallint[]) WITH ORDINALITY AS index_key(attnum, ordinal_position)
               JOIN pg_attribute attribute
                 ON attribute.attrelid = i.indrelid AND attribute.attnum = index_key.attnum
               ORDER BY index_key.ordinal_position
           ) = expected.key_columns
           AND NOT EXISTS (
               SELECT 1 FROM unnest(i.indoption::smallint[]) AS index_option(value)
               WHERE index_option.value <> 0
           )
           AND NOT EXISTS (
               SELECT 1
               FROM unnest(i.indkey::smallint[], i.indcollation::oid[], i.indclass::oid[])
                    AS index_key(attnum, collation_oid, opclass_oid)
               JOIN pg_attribute attribute
                 ON attribute.attrelid = i.indrelid AND attribute.attnum = index_key.attnum
               JOIN pg_opclass opclass ON opclass.oid = index_key.opclass_oid
               WHERE index_key.collation_oid <> attribute.attcollation OR NOT opclass.opcdefault
           )
       ) AS equivalent
FROM rollout_expected_indexes expected
JOIN pg_index i ON i.indrelid = to_regclass(format('public.%I', expected.table_name))
JOIN pg_class actual ON actual.oid = i.indexrelid
JOIN pg_am method ON method.oid = actual.relam;

DO $check_existing$
DECLARE
    expected record;
    named_relation oid;
BEGIN
    FOR expected IN SELECT * FROM rollout_expected_indexes LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_class c
            WHERE c.oid = to_regclass(format('public.%I', expected.table_name))
              AND c.relkind = 'r' AND NOT c.relispartition
        ) THEN
            RAISE EXCEPTION 'Expected ordinary table public.%; bootstrap/schema review is required',
                expected.table_name;
        END IF;

        IF EXISTS (
            SELECT 1 FROM unnest(expected.key_columns) AS requested(column_name)
            WHERE NOT EXISTS (
                SELECT 1 FROM pg_attribute attribute
                WHERE attribute.attrelid = to_regclass(format('public.%I', expected.table_name))
                  AND attribute.attname = requested.column_name
                  AND attribute.attnum > 0 AND NOT attribute.attisdropped
            )
        ) THEN
            RAISE EXCEPTION 'Missing index column on public.%; run/review 01 first', expected.table_name;
        END IF;

        named_relation := to_regclass(format('public.%I', expected.index_name));
        IF named_relation IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM rollout_index_status inspected
            WHERE inspected.requested_name = expected.index_name
              AND inspected.actual_oid = named_relation
              AND inspected.equivalent AND inspected.usable
        ) THEN
            RAISE EXCEPTION 'Name public.% exists but its definition/validity is incompatible; inspect, do not skip or replace blindly',
                expected.index_name;
        END IF;

        IF EXISTS (
            SELECT 1 FROM rollout_index_status inspected
            WHERE inspected.requested_name = expected.index_name
              AND inspected.equivalent AND NOT inspected.usable
        ) THEN
            RAISE EXCEPTION 'An equivalent index for public.% is invalid/not ready; inspect and recover it before continuing',
                expected.index_name;
        END IF;
    END LOOP;
END;
$check_existing$;

DO $check_identity$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.skills
        WHERE identity_key IS NOT NULL AND identity_key !~ '^[0-9a-f]{64}$'
    ) THEN
        RAISE EXCEPTION 'Non-null identity_key values must be lowercase SHA-256 hex; review data without rewriting IDs/slugs';
    END IF;
    IF EXISTS (
        SELECT identity_key FROM public.skills WHERE identity_key IS NOT NULL
        GROUP BY identity_key HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate non-null identity_key values exist; stop for manual identity review';
    END IF;
END;
$check_identity$;

DO $verify_indexes$
DECLARE
    expected record;
BEGIN
    FOR expected IN SELECT * FROM rollout_expected_indexes LOOP
        IF NOT EXISTS (
            SELECT 1 FROM rollout_index_status inspected
            WHERE inspected.requested_name = expected.index_name
              AND inspected.equivalent AND inspected.usable
        ) THEN
            RAISE EXCEPTION 'No valid equivalent index found for public.%', expected.index_name;
        END IF;
    END LOOP;
END;
$verify_indexes$;

SELECT requested_name, actual_name, usable, equivalent, actual_definition
FROM rollout_index_status
WHERE equivalent OR actual_name = requested_name
ORDER BY requested_name, actual_name;

-- Diagnostics only. Null keys are expected legacy rows, not a failed rollout.
SELECT count(*) AS skill_rows,
       count(*) FILTER (WHERE identity_key IS NULL) AS legacy_unclaimed_rows,
       count(*) FILTER (WHERE identity_key IS NOT NULL) AS claimed_rows
FROM public.skills;

-- Existing public identifiers are unchanged; use a controlled export if a
-- before/after comparison is needed. No SQL lower/unaccent backfill is valid.
SELECT count(*) AS symbol_bearing_legacy_names
FROM public.skills
WHERE identity_key IS NULL AND (position('+' IN name) > 0 OR position('#' IN name) > 0);

RESET lock_timeout;
RESET statement_timeout;
RESET search_path;
