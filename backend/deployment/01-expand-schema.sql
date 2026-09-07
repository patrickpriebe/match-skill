\set ON_ERROR_STOP on

-- Run with psql -X against the reviewed application database. See README.md.
-- Existing IDs, names, slugs, statuses, URLs and feedback are never rewritten.
SELECT current_database() AS database_name, current_user AS executing_role,
       current_setting('server_version') AS server_version;

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
SET LOCAL search_path = pg_catalog, public;

DO $preflight$
DECLARE
    required_table text;
BEGIN
    FOREACH required_table IN ARRAY ARRAY[
        'users', 'skills', 'user_skills', 'availabilities', 'exchanges', 'feedback'
    ] LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relname = required_table
              AND c.relkind = 'r' AND NOT c.relispartition
        ) THEN
            RAISE EXCEPTION 'Expected ordinary table public.%; stop and review the bootstrap/schema instructions',
                required_table;
        END IF;
    END LOOP;
END;
$preflight$;

-- Fail promptly rather than wait behind an active workload. These short-lived
-- locks still block reads/writes; schedule the operation and retry after review.
LOCK TABLE public.skills, public.exchanges IN ACCESS EXCLUSIVE MODE;

DO $expand$
DECLARE
    existing_type oid;
    existing_limit integer;
    existing_not_null boolean;
    existing_default boolean;
    existing_generated "char";
    existing_collation_schema text;
    existing_collation_name text;
BEGIN
    SELECT attribute.atttypid, attribute.atttypmod, attribute.attnotnull,
           collation_namespace.nspname, collation_info.collname
      INTO existing_type, existing_limit, existing_not_null,
           existing_collation_schema, existing_collation_name
      FROM pg_attribute attribute
      LEFT JOIN pg_collation collation_info ON collation_info.oid = attribute.attcollation
      LEFT JOIN pg_namespace collation_namespace ON collation_namespace.oid = collation_info.collnamespace
     WHERE attribute.attrelid = 'public.exchanges'::regclass AND attribute.attname = 'meeting_url'
       AND attribute.attnum > 0 AND NOT attribute.attisdropped;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'public.exchanges.meeting_url is missing; this is not a base-schema bootstrap';
    END IF;
    IF existing_not_null THEN
        RAISE EXCEPTION 'meeting_url must remain nullable for unscheduled exchanges; review existing schema';
    END IF;
    IF existing_type = 'pg_catalog.varchar'::regtype THEN
        -- varchar typmod includes four bytes. -1 means unlimited varchar.
        IF existing_limit <> -1 AND existing_limit < 2048 + 4 THEN
            EXECUTE format(
                'ALTER TABLE public.exchanges ALTER COLUMN meeting_url TYPE varchar(2048) COLLATE %I.%I',
                existing_collation_schema, existing_collation_name
            );
        END IF;
    ELSIF existing_type <> 'pg_catalog.text'::regtype THEN
        RAISE EXCEPTION 'Unexpected meeting_url type %; no cast or destructive conversion was attempted',
            format_type(existing_type, existing_limit);
    END IF;

    SELECT atttypid, atttypmod, attnotnull, atthasdef, attgenerated
      INTO existing_type, existing_limit, existing_not_null, existing_default, existing_generated
      FROM pg_attribute
     WHERE attrelid = 'public.skills'::regclass AND attname = 'identity_key'
       AND attnum > 0 AND NOT attisdropped;
    IF NOT FOUND THEN
        ALTER TABLE public.skills ADD COLUMN identity_key varchar(64);
    ELSIF existing_type <> 'pg_catalog.varchar'::regtype OR existing_limit <> 64 + 4
          OR existing_not_null OR existing_default OR existing_generated <> '' THEN
        RAISE EXCEPTION 'Existing identity_key must be nullable varchar(64), without default/generation; review instead of overwriting';
    END IF;
END;
$expand$;

SELECT table_name, column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND ((table_name = 'exchanges' AND column_name = 'meeting_url')
       OR (table_name = 'skills' AND column_name = 'identity_key'))
ORDER BY table_name, column_name;

COMMIT;

-- The unique identity index is intentionally built in the next standalone file.
-- Do not enable the new suggestion writer until 02 and 03 both succeed.
