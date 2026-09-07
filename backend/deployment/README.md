# Backend deployment preparation

Date: 2026-09-07. Owner: backend agent. These files prepare a reviewed rollout;
they do not connect to a database, deploy, or provision services automatically.
See `docs/backend-pending-fixes-plan.md` and the corresponding report at the
repository root for implementation scope and actual test results.

## Build and runtime configuration

Run the owner tests before publishing an image. From the backend directory:

```powershell
Set-Location -LiteralPath 'C:\Projects\match-skill\backend'
.\mvnw.cmd -B clean verify
if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed' }
docker build --tag matchskill-backend:closure-local .
if ($LASTEXITCODE -ne 0) { throw 'Backend image build failed' }
```

The Docker build uses separate Java 21 build/runtime stages and deliberately
skips tests inside the image build. The final image runs the application as
UID/GID 10001. Use `backend/` as the Render build context/root directory and
its `Dockerfile`; record the image digest used for validation and deployment.
The executable entrypoint accepts Spring command-line arguments.
[Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
and [Docker on Render](https://render.com/docs/docker) describe these platform
mechanisms.

Activate `SPRING_PROFILES_ACTIVE=prod` explicitly. The production profile
validates an existing schema and does not create or repair it. Native Spring
property overrides remain supported; use the following environment names
consistently in deployment configuration:

| Variable | Required value or behavior |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `PORT` | Render supplies this; local fallback is `8080`. The prod profile binds `0.0.0.0`. |
| `JDBC_DATABASE_URL` | Explicit `jdbc:postgresql://HOST:PORT/DATABASE` for the intended Supabase/PostgreSQL database; include provider-required connection options. |
| `DB_USER`, `DB_PASSWORD` | Database credentials supplied as secrets. Do not embed credentials in committed URLs or images. |
| `DB_SSLMODE` | Defaults to `verify-full`. Configure the provider CA when needed, for example `sslrootcert=/etc/secrets/database-ca.crt` in the JDBC URL. |
| `JWT_SECRET` | Required, nonblank and at least 32 characters. Generate a strong private value; the local development fallback is unavailable in prod. |
| `JWT_EXPIRATION_MINUTES` | Optional; existing default remains `1440`. |
| `REDIS_URL` | Required explicit Redis endpoint. Use `rediss://` for an external TLS endpoint; a provider's trusted private endpoint may use `redis://`. |
| `REDIS_DATABASE` | Optional database number, default `0`. Spring Boot does not select this from a `/N` path in `REDIS_URL`; configure it explicitly. |
| `CORS_ALLOWED_ORIGINS` | Required comma-separated exact frontend origins, including scheme and port where applicable; no path. |
| `OAUTH2_SUCCESS_REDIRECT_URI` | Required frontend URL, for example `https://FRONTEND_HOST/auth/callback`. The local default is `http://localhost:5173/auth/callback`. |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Optional together. Google sign-in is disabled unless both are configured. |
| `GOOGLE_REDIRECT_URI` | When Google is enabled in prod, set the exact registered backend callback: `https://BACKEND_HOST/api/auth/google/callback`. |

`verify-full` verifies the server certificate and hostname; configure trusted
certificates rather than disabling verification to address connection errors.
These are pgJDBC options, distinct from a libpq/psql connection string.
[pgJDBC SSL documentation](https://jdbc.postgresql.org/documentation/ssl/)
explains the verification modes. Render requires the service to bind the
provided port on `0.0.0.0`.
[Render port binding](https://render.com/docs/web-services#port-binding)

The API context path remains `/api`. Google authorization starts at
`/api/auth/google`; its backend callback and the frontend success callback are
different URLs. No global forwarded-header trust is enabled. Provider sign-in
requires a real authorized test account and registered redirect configuration;
local mock tests do not prove the provider flow.

There is no public health endpoint in this change. Use the platform's TCP
health check initially; do not select a protected API path that returns 401 as
an HTTP health check. TCP readiness does not prove Redis or a complete user
journey. Perform authenticated API smoke tests after startup.
[Render health checks](https://render.com/docs/health-checks)

## Existing database: additive schema rollout

Use an owner/migration connection to the reviewed application database, through
a direct or session-capable endpoint. The scripts use session-local temporary
objects and are not suitable for a transaction-pooling endpoint. Configure
libpq service entries and a protected password file outside this repository so
credentials do not appear in commands or logs. The examples below use the
explicitly prepared `matchskill_staging` service; choose the reviewed target
service separately for a production operation.

Before applying anything, confirm the database/role, PostgreSQL version,
application table ownership, backup/restore readiness, available storage for
index builds, active transactions and the planned migration window. Rehearse
against an isolated database containing representative legacy schema/data.
Do not use the tester-owned `matchskill_validation` database or the coordinator's
`matchskill_smoke` database without their assignment. These files assume ordinary,
nonpartitioned application tables in `public`.

Stop old application instances from serving skill suggestions before the new
writer is enabled. Old binaries still identify suggestions by the ambiguous
slug. Keep new suggestion writes disabled until all three scripts succeed.

Run each file in a new psql session, in this order:

```powershell
psql -X 'service=matchskill_staging' -v ON_ERROR_STOP=1 -f deployment/01-expand-schema.sql
if ($LASTEXITCODE -ne 0) { throw 'Schema expansion failed; stop rollout' }
psql -X 'service=matchskill_staging' -v ON_ERROR_STOP=1 -f deployment/02-create-indexes-concurrently.sql
if ($LASTEXITCODE -ne 0) { throw 'Index rollout failed; inspect before retry' }
psql -X 'service=matchskill_staging' -v ON_ERROR_STOP=1 -f deployment/03-verify-schema.sql
if ($LASTEXITCODE -ne 0) { throw 'Schema verification failed; do not enable new writer' }
```

`01-expand-schema.sql` checks that the base tables exist, takes bounded locks,
and atomically adds nullable `skills.identity_key varchar(64)` and widens
`exchanges.meeting_url` only when its existing varchar limit is below 2048.
An existing larger varchar, unlimited varchar or text column remains unchanged.
Unexpected types, nullability, defaults or generated identity definitions cause
a failure instead of an implicit conversion. No IDs, names, slugs, statuses,
URLs or feedback values are updated. The lock wait is capped at 5 seconds and
the statement timeout at 60 seconds. This short operation still takes exclusive
table locks; it is not a promise of zero disruption.
[PostgreSQL ALTER TABLE](https://www.postgresql.org/docs/current/sql-altertable.html)

`02-create-indexes-concurrently.sql` builds missing indexes sequentially in
autocommit mode, with a 5-second lock timeout and a 15-minute statement timeout.
Do not use `--single-transaction`, `-1`, a surrounding `BEGIN`, or paste the whole
file as one SQL command in a web console. psql's `\gexec` sends each generated
statement separately. Existing equivalent indexes under another name are
recognized; a name collision, invalid equivalent index or unexpected definition
stops the script. No `IF NOT EXISTS` masks incomplete work.

Concurrent index creation cannot run inside a transaction block. A failed build
can leave an invalid index, and an invalid unique index can still affect writes.
Inspect the exact index and dependencies before separately rebuilding it with
`REINDEX INDEX CONCURRENTLY` or dropping/recreating that reviewed index. The
scripts never drop indexes automatically. Retry from a new session only after
resolving the reported condition.
[PostgreSQL CREATE INDEX](https://www.postgresql.org/docs/current/sql-createindex.html)

The expected definitions are:

| Preferred index name | Table and keys | Uniqueness |
| --- | --- | --- |
| `idx_user_skills_skill_direction` | `user_skills(skill_id,direction)` | Nonunique |
| `idx_availabilities_user` | `availabilities(user_id)` | Nonunique |
| `idx_exchanges_requester_status_created` | `exchanges(requester_id,status,created_at)` | Nonunique |
| `idx_exchanges_receiver_status_created` | `exchanges(receiver_id,status,created_at)` | Nonunique |
| `idx_feedback_author` | `feedback(author_id)` | Nonunique |
| `ux_skills_identity_key` | `skills(identity_key)` | Unique, with multiple nulls permitted |

Existing primary/unique constraints, including feedback `(exchange_id,author_id)`
and user skills `(user_id,skill_id,direction)`, are retained. An equivalent
Hibernate-generated identity unique index is sufficient; creating a duplicate
index or attaching a second unique constraint is unnecessary.

`03-verify-schema.sql` rechecks column shape, identity-key format/duplicates,
index keys/order, method, uniqueness, immediacy, default operator classes and
collations, predicates/expressions, and valid/ready/live flags. It accepts an
equivalent valid index with a different name and reports that name. Its only
created objects are temporary inspection objects, discarded with the session.
[PostgreSQL pg_index](https://www.postgresql.org/docs/current/catalog-pg-index.html)
describes the catalog flags. Review query plans and statistics on representative
data; schedule `ANALYZE` after schema changes as appropriate.

Only then start the new image with `prod`, verify startup, and exercise login,
skills, matching, scheduling and bilateral feedback. Keep command output and
exact failures with the deployment record. Hibernate validation does not replace
the explicit index and semantic checks in these scripts.

## Empty database: bootstrap through reviewed staging DDL

The three rollout scripts intentionally fail when the base schema is absent.
`ddl-auto=validate` cannot bootstrap an empty production database.

1. Have the coordinator prepare a separate, newly empty, disposable PostgreSQL
   database and Redis instance/configuration. Do not reuse production, tester
   fixtures or existing application data. Record its host and database explicitly.
2. Use the same tested image digest and a secret environment file containing only
   those disposable connections and production-shaped configuration. Start it
   with the explicit override `--spring.jpa.hibernate.ddl-auto=create` solely
   against this empty staging database. This option can replace existing schema;
   never point it at a populated or production database.
3. Confirm successful startup, then stop this specifically assigned staging
   container. Export schema only with the matching PostgreSQL client tools:

```powershell
pg_dump 'service=matchskill_schema_export' --schema-only --schema=public --no-owner --no-privileges --file=target/staging-schema.sql
if ($LASTEXITCODE -ne 0) { throw 'Schema export failed' }
```

4. Review the exported tables, columns, constraints, indexes, ownership/grants,
   provider-specific requirements and any generated session settings. Compare
   against the intended production schema. Prepare a reviewed initial migration
   artifact; the export itself is not approval to apply unreviewed DDL.
5. Rehearse that initial migration in a second empty disposable database, then
   run `03-verify-schema.sql` and start the image with `prod` and no DDL override.
   Apply the reviewed initial migration to a new production database through
   the coordinator's normal rollout process. Existing databases use the additive
   path above instead of replaying a complete schema export.

The generated artifact is intentionally not committed as a guessed schema.
`pg_dump --schema-only` exports object definitions without table contents;
`--no-owner` and `--no-privileges` leave role/grant decisions for explicit review.
[PostgreSQL pg_dump](https://www.postgresql.org/docs/current/app-pgdump.html)

## Legacy skill identity audit and optional manual claims

Null identity keys are supported legacy data. A suggestion can lazily claim a
single matching legacy row without changing its public ID or slug. Ambiguous
legacy matches return `409 SKILL_IDENTITY_CONFLICT`; they are never merged.

The key is SHA-256 of the exact Java canonical identity encoded as UTF-8. The
normalizer uses NFD and `Locale.ROOT`, removes combining marks, retains Unicode
letters/digits, escapes `+` as `~2b` and `#` as `~23`, and collapses other runs
to separators. At least one letter/digit is required. Spacing remains meaningful
around symbols: `C ++` and `C++` are distinct. Do not infer language aliases.
Use `Slugs.normalizedIdentity`/`Slugs.identityKey` from the deployed version;
SQL `lower`, `unaccent`, substring substitution or hashing a public slug do not
reproduce this contract.

After column expansion, export a stable catalog snapshot to a local UTF-8 JSON
array. The query does not change data. Store exports/reports outside version
control and limit access to their intended reviewers.

```powershell
$env:PGCLIENTENCODING = 'UTF8'
psql -X 'service=matchskill_staging' -v ON_ERROR_STOP=1 -q -t -A -P pager=off -o target/skill-identity-input.json -c "SELECT coalesce(json_agg(json_build_object('id', id, 'name', name, 'slug', slug, 'identityKey', identity_key) ORDER BY id), '[]'::json) FROM public.skills"
if ($LASTEXITCODE -ne 0) { throw 'Identity export failed' }
.\mvnw.cmd -B test-compile dependency:build-classpath '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/test-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Audit tool compilation failed' }
```

Run the test-only CLI `com.matchskill.backend.tools.SkillIdentityAudit`. It reads
one local JSON array path (or `-` for stdin), has no database connection, and
writes UTF-8 JSON to stdout. This PowerShell example captures bytes as UTF-8
without the UTF-16 redirection used by older PowerShell versions:

```powershell
$auditClassPath = 'target\test-classes;target\classes;' + (Get-Content -LiteralPath target/test-classpath.txt -Raw).Trim()
$auditStart = New-Object System.Diagnostics.ProcessStartInfo
$auditStart.FileName = 'java'
$auditStart.Arguments = '-cp "' + $auditClassPath + '" com.matchskill.backend.tools.SkillIdentityAudit "target/skill-identity-input.json"'
$auditStart.UseShellExecute = $false
$auditStart.CreateNoWindow = $true
$auditStart.RedirectStandardOutput = $true
$auditStart.StandardOutputEncoding = [System.Text.Encoding]::UTF8
$auditProcess = [System.Diagnostics.Process]::Start($auditStart)
$auditOutput = $auditProcess.StandardOutput.ReadToEnd()
$auditProcess.WaitForExit()
[System.IO.File]::WriteAllText([System.IO.Path]::GetFullPath('target/skill-identity-audit.json'), $auditOutput, [System.Text.UTF8Encoding]::new($false))
$auditExitCode = $auditProcess.ExitCode
```

Exit 0 means a clean report, 1 means review findings, and 2 means malformed input.
The report contains `rows`, `counts` and `hasIssues`. Rows include original
`id/name/slug/identityKey`, `normalizedIdentity`, `status`, `proposedIdentityKey`
and `issues`. Only `READY` rows receive a proposed key. `KEYED` is already
consistent; `AMBIGUOUS_IDENTITY`, `INVALID_NAME`, `KEY_MISMATCH` and `KEY_COLLISION`
require review and never authorize a claim. The audit cannot detect a historical
alias that was never stored, or decide whether two different public IDs should
be merged.

Automatic bulk backfill is not part of this rollout. If the coordinator chooses
manual claims, pause all vocabulary writers, refresh the export/audit, and
review each READY row. After the unique index is valid, this psql template
claims one reviewed row only if its ID, name, slug and null key still match:

```sql
\set ON_ERROR_STOP on
\set reviewed_id 'REPLACE_WITH_READY_ROW_UUID'
\set reviewed_name 'REPLACE_WITH_EXACT_EXPORTED_NAME'
\set reviewed_slug 'REPLACE_WITH_EXACT_EXPORTED_SLUG'
\set reviewed_key 'REPLACE_WITH_PROPOSED_IDENTITY_KEY'
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
UPDATE public.skills SET identity_key = :'reviewed_key'
WHERE id = :'reviewed_id'::uuid
  AND name = :'reviewed_name' AND slug = :'reviewed_slug'
  AND identity_key IS NULL AND :'reviewed_key' ~ '^[0-9a-f]{64}$'
RETURNING id, name, slug, identity_key;
\if :ROW_COUNT
COMMIT;
\else
ROLLBACK;
\quit 3
\endif
```

Use psql's quoted-variable form shown above; preserve exact exported values and
escape quotes when setting variables. Zero updated rows or a uniqueness error
means stop and re-audit, not overwrite an existing key. Re-export and run the
audit plus `03-verify-schema.sql` after reviewed claims. psql supports explicit
error stopping, quoted variable substitution and per-statement execution.
[PostgreSQL psql](https://www.postgresql.org/docs/current/app-psql.html)

## Recovery and validation limits

An error in file 01 rolls its transaction back. File 02 can make partial progress
and leave invalid indexes; preserve successful work and inspect catalog state.
File 03 fails on unmet expectations instead of claiming rollout success. Review
timeouts rather than retrying continuously under load. PostgreSQL distinguishes
lock waits from whole-statement timeouts.
[Client timeout settings](https://www.postgresql.org/docs/current/runtime-config-client.html)

Application rollback keeps the widened URL column, nullable identity column and
valid indexes. Do not shrink or drop them: existing values may already depend on
the new capacity. Keep suggestions disabled if an old writer is restored;
backward-readable schema does not repair the old slug-identity behavior.

These scripts and commands require isolated PostgreSQL execution before a
production rollout is considered validated. Preparation alone does not verify
DDL lock timing, index build duration, TLS/credentials, hosted Render/Supabase
connectivity or Google sign-in. Record actual rehearsal commands, PostgreSQL
version, before/after schema, validity checks, test/build results and exact
failure lines in the backend/tester reports. No production SQL was executed
while authoring this runbook.
