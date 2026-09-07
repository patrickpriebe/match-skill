# Backend pending fixes report

Date: 2026-09-07. Owner: backend agent. Status: implementation, additive SQL,
local image and full clean verify completed. Coordinator confirmed isolated
PostgreSQL migration and real Redis/image smoke. Hosted deployment was not performed.

## Changes and compatible contracts

Skill suggestions now compare a normalized identity rather than treating an
occupied slug as proof of identity. `skills.identity_key` is an internal nullable
unique SHA-256 key; no response DTO exposes it. New C, C++ and C# suggestions
produce different IDs and slugs. Existing IDs, slugs, names and statuses remain
unchanged. A legacy C++ with slug `c` remains that row; a new C gets a deterministic
fallback slug, and suggesting C++ again returns the original ID/slug.

`POST /api/skills/suggest` retains its existing DTO shape and success status.
Meaningless names return HTTP 400 `INVALID_SKILL_NAME`. Ambiguous legacy rows,
occupied incompatible identity keys or exhausted concurrent retries return
HTTP 409 `SKILL_IDENTITY_CONFLICT`. Existing HTTP Bean Validation errors retain
`VALIDATION_ERROR`. No automatic alias, skill reassignment or legacy merge occurs.

Scheduling accepts allowed HTTPS meeting URLs up to 2048 UTF-16 units. Shared
`MeetingUrlConstraints.MAX_LENGTH` drives DTO `@Size`, domain validation and the
entity column length. HTTP input of length 2049 returns 400 `VALIDATION_ERROR`
before persistence; direct domain calls return `INVALID_MEETING_URL`. Allowlist,
HTTPS, request/response shapes and exchange state rules remain unchanged. There
is no truncation. The database column must be expanded before releasing this code.

The opt-in `prod` profile uses `ddl-auto=validate`, explicit datasource and Redis
configuration, required JWT/CORS/frontend callback settings and PostgreSQL TLS
`verify-full` by default. Required settings fail before datasource clients start.
`PORT` keeps local fallback 8080. The local OAuth success callback now matches
the frontend's `/auth/callback`; explicit overrides still win. Google remains
optional and requires both credentials plus its explicit backend callback in prod.
No runtime dependencies or application architecture were added.

## Exact identity normalization and concurrency

The implementation authority is `backend/src/main/java/com/matchskill/backend/util/Slugs.java`.
For a non-null name the exact sequence is:

1. `String.strip()`, then `toLowerCase(Locale.ROOT)`, then Unicode NFD normalization.
2. Iterate Unicode code points. Remove marks of types NON_SPACING_MARK,
   COMBINING_SPACING_MARK and ENCLOSING_MARK. Retain `Character.isLetterOrDigit`.
3. Encode `+` as `~2b` and `#` as `~23`. Every other run becomes one `-`, without
   leading/trailing separators. Require at least one retained letter or digit;
   otherwise canonical identity is empty and suggestion input is rejected.
4. SHA-256 the canonical string's UTF-8 bytes and store lowercase 64-digit hex.

No substring replacement or English-word expansion is used. C plus plus, C sharp,
C++ and C# remain distinct. Spacing around symbols is meaningful: `C ++` differs
from `C++`. Unicode letters/digits are supported, with the documented mark removal
and ROOT lowercase rules; this is not language-specific synonym recognition.

The preferred new slug is the canonical identity. If it exceeds 255 UTF-16 units,
or an unrelated legacy row occupies it, use up to 189 canonical units, without
splitting a surrogate pair, followed by `--` and the full digest. Existing public
slugs are never regenerated. Clients must use returned IDs/slugs unchanged.

Request-time lookup considers identity key, preferred/fallback/old ASCII slug,
and case-insensitive exact display name. Canonical names are compared again to
guard incompatible hash matches. A single matching unkeyed legacy row receives
only its identity key; multiple matching IDs cause a conflict. Arbitrarily
imported legacy slugs with different accent spellings can fall outside this
bounded lookup and require the offline audit below. No global reconciliation
is claimed from request-time lookup alone.

Suggestion writes suspend any outer transaction. Each repository save/flush has
its own transaction; constraint failures are caught after that transaction has
rolled back. Up to three fresh attempts resolve the committed winner. Unique
identity/name/slug constraints arbitrate across processes. H2 tests exercised
eight concurrent same-identity requests. The independent PostgreSQL IT verifies
C/C++/C# distinctions sequentially. The coordinator additionally dispatched eight
PostAsync suggestions for one new identity before awaiting responses against the
unchanged prod image and PostgreSQL smoke database. All returned 201 with the
same ID/slug; SQL confirmed one row and one identity key. This provides a sampled
real-PostgreSQL race check on one application instance, not a multi-instance
load or stress test.

## SQL, legacy audit and rollout

Stable files are under `backend/deployment/`:

| File | Purpose |
| --- | --- |
| `01-expand-schema.sql` | Atomic nullable identity column and URL expansion, bounded locks, no row rewrites. |
| `02-create-indexes-concurrently.sql` | Six identity/query indexes; separate autocommit statements, validates equivalent existing indexes. |
| `03-verify-schema.sql` | Column/index/key checks, fails on incompatible or invalid state. |
| `README.md` | Exact runtime variables, sequencing, bootstrap, audit/manual claim and recovery commands. |

Run each SQL file in a new `psql -X` session through a direct/session-capable
connection. File 02 cannot run inside a transaction or transaction-pooling
endpoint. Equivalent valid Hibernate indexes are accepted; incompatible names
and invalid indexes stop execution. Timeouts and partial concurrent-index
failures require inspection, not blind retries. No script drops data or indexes.
File 01 preserves a larger/unlimited varchar or text URL column and its collation.

Stop old suggestion writers before enabling the new writer. Existing schema
must pass all three scripts first. Rollback retains the expanded column, nullable
identity column and valid indexes; keep suggestions disabled on an old binary
because its previous slug behavior remains incorrect. No mandatory slug renaming
or bulk identity backfill is required for deployment.

The test-source `com.matchskill.backend.tools.SkillIdentityAudit` reads a local
JSON catalog export using the exact Java normalizer and emits UTF-8 JSON to
stdout. It has no database/network access or file writes. Only unambiguous READY
rows receive a proposed key. Ambiguous identities, meaningless names, mismatched
keys and occupied keys require review. Exit codes: 0 clean, 1 findings, 2 invalid
input. The runbook describes a guarded optional manual claim with writers paused,
matching the exact ID/name/slug and null key. No collision backfill or automatic
reassociation is performed. Audit reports cannot reconstruct aliases never stored.

The coordinator alone owns migration rehearsal on `matchskill_smoke`, seeded
with the old JAR and legacy C++/scheduled URL records. The independent tester owns
`matchskill_validation`. This backend owner has not accessed either database.
The coordinator confirmed all three scripts exited 0 in separate psql sessions
on PostgreSQL 15.19, with six valid equivalent indexes and unchanged legacy IDs,
slugs and URL. See `docs/deployment-readiness.md` for their commands and evidence.

## Local image and runtime handoff

`backend/Dockerfile` uses Java 21 build/runtime stages and runs as UID/GID 10001.
The `.dockerignore` allows only build inputs and main sources, excluding tests,
exports and secret-file patterns. The image build deliberately skips tests.

- Tag: `matchskill-backend:closure-local`.
- Image digest: `sha256:5708fcbc11b3267ce4677ac090011fe2eb2c01f0c7b7719c51d725e40b8d29d1`.
- Entrypoint: `["java","-jar","/app/app.jar"]`.
- `docker run --rm --network none --entrypoint java ... -version` succeeded:
  Temurin OpenJDK `21.0.12+8-LTS`.
- Docker server: `29.6.2`. Application Maven version remains `0.1.0-SNAPSHOT`;
  the image digest identifies this checkpoint and was preserved after verification.
- Final host JAR: `backend/target/backend-0.1.0-SNAPSHOT.jar`, SHA-256
  `6a2e0150baf6f0e87212c18dc3a28faa613e670a85bc02982ad81bdffad16519`.
  This separately built host JAR and the Docker image have distinct artifact hashes.

Required prod variables: `SPRING_PROFILES_ACTIVE=prod`, `JDBC_DATABASE_URL`,
`DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (minimum 32 characters), `REDIS_URL`,
`CORS_ALLOWED_ORIGINS`, `OAUTH2_SUCCESS_REDIRECT_URI`. Optional/defaults:
`PORT=8080`, `DB_SSLMODE=verify-full`, `REDIS_DATABASE=0`,
`JWT_EXPIRATION_MINUTES=1440`. Google, when enabled: paired `GOOGLE_CLIENT_ID` /
`GOOGLE_CLIENT_SECRET` and explicit `GOOGLE_REDIRECT_URI` pointing at
`https://BACKEND_HOST/api/auth/google/callback`. Frontend success uses
`https://FRONTEND_HOST/auth/callback`.

Spring Boot 3.3 does not derive the Redis database from the URL path. Smoke must
set `REDIS_DATABASE=1` explicitly; REDIS_URL selects host/authentication/TLS.
The agreed probe is a POST to `/api/auth/login` with a disposable nonexistent
email and nonblank password. Expected 401 plus an increment of
`ratelimit:login:<remoteAddr>` and a positive TTL up to 60 seconds proves that
request traversed real Redis. Compare scoped SCAN/GET/TTL in DB1 before/after;
do not read/modify DB0 or tester keys. Existing rate-limit counters can instead
produce 429 and must be accounted for when interpreting the probe.

Coordinator smoke results on the unchanged image: startup with `prod` and
`ddl-auto=validate` succeeded; old JWT remained valid; old C++ retained slug `c`
and its ID; C/C# received distinct IDs. A 2048-unit URL persisted, while 2049
returned 400 `VALIDATION_ERROR` without changing the saved value. The nonexistent
login returned 401 and created DB1 `ratelimit:login:172.18.0.1` with count 1 and
TTL 59, from no matching keys before the request. DB0 was untouched. This smoke
used `DB_SSLMODE=disable` only for disposable local PostgreSQL; it does not prove
hosted certificate verification. These results are attributed to the coordinator,
not presented as database operations performed by this owner.

The runbook follows official [Docker multi-stage guidance](https://docs.docker.com/build/building/multi-stage/),
[Render Docker hosting](https://render.com/docs/docker),
[Render port binding](https://render.com/docs/web-services#port-binding) and
[pgJDBC SSL verification](https://jdbc.postgresql.org/documentation/ssl/).
Render TCP health checks are documented; no public health endpoint or new global
forwarded-header trust was introduced.

## Actual validation and failure lines

Commands ran from `C:\Projects\match-skill\backend`. Logs are in `%TEMP%`.

| Check | Actual result | Log |
| --- | --- | --- |
| Skill/URL focused suite (six classes listed in plan) | 100 tests; 0 failures/errors/skips; BUILD SUCCESS | `matchskill-pending-focused.log` |
| Deployment/security focused suite (four classes) | Corrected run: 43 tests; 0 failures/errors/skips; BUILD SUCCESS | `matchskill-backend-deployment-config-final.log` |
| `mvnw.cmd -B test -Dtest=SkillIdentityAuditTest,DeploymentConfigurationTest` | 54 tests (30 + 24); 0 failures/errors/skips; BUILD SUCCESS | `matchskill-backend-audit-config.log` |
| `docker build --tag matchskill-backend:closure-local --file Dockerfile .` | Build and image export succeeded; tests intentionally skipped | `matchskill-backend-image-build.log` |
| Image metadata / isolated `java -version` | UID/GID, entrypoint and Java 21 verified | Recorded above |
| Final `mvnw.cmd -B clean verify` | 311 tests / 28 suites; 0 failures/errors/skips; BUILD SUCCESS, 24.698 seconds | `matchskill-backend-closure-verify.log` |
| PostgreSQL/Redis/legacy SQL rehearsal | Coordinator confirmed scripts 01/02/03 exit 0 and successful image/API/DB1 smoke | `docs/deployment-readiness.md` |
| Independent opt-in `PostgresRedisLiveIT` | Tester reports 9 tests passed; separate from the 311-test owner build | `docs/tester-closure-report.md` |
| Concurrent same-identity PostgreSQL smoke | Coordinator: 8 concurrent requests, all 201; one ID, slug, row and identity key | `%TEMP%/matchskill-closure-20260907/concurrent-skills-results.json` |

The backend owner read the sanitized concurrent-smoke JSON and confirmed its
eight 201 statuses, `distinctIds=1`, `distinctSlugs=1`, `databaseRows=1` and
`distinctDatabaseKeys=1`. Request dispatch and database assertions were performed
by the coordinator. This smoke is separate from Maven's 311-test total. No runtime
change or rebuild followed it; the recorded image/JAR checkpoint remains intact.

The 24 configuration tests appear in two focused green runs and must not be
double counted as distinct coverage. Focused evidence covers 173 distinct tests;
these are included in the final 311, not added to it. The final build passed all
24 configuration cases, including both Redis variants and the JWT locale case.

The full build finished at 2026-09-07 14:44:00 -03:00. The XML totals were computed
only for the 28 suite names listed by that build's `Running` lines: 311 tests,
zero failures/errors/skips. The shared Surefire directory also contained the
tester's separate 9-case PostgreSQL IT XML; counting every XML would incorrectly
attribute 320 cases to this command. Historical tester text claiming 262 green
while reporting failures is not the final owner baseline.

The exact 28 XMLs, build log and host JAR were copied to the owner evidence folder
`C:\Users\Patrick\AppData\Local\Temp\matchskill-backend-closure-20260907-144400`.
This preserves this run if another owner's subsequent Maven command replaces
the shared target directory. No other owner's artifacts were removed.

The initial configuration run had three failures, corrected before handoff.
`matchskill-backend-deployment-config.log` contains these actual lines:

```text
[ERROR] Tests run: 24, Failures: 3, Errors: 0, Skipped: 0 ... DeploymentConfigurationTest
shouldUseRedisUrlCredentialsDatabaseAndTlsWithoutOpeningAConnection(String, boolean)[1]
expected: 2
 but was: 0
shouldUseRedisUrlCredentialsDatabaseAndTlsWithoutOpeningAConnection(String, boolean)[2]
expected: 2
 but was: 0
shouldRetainJwtMinimumLengthValidationInProduction
default message [tamanho deve ser entre 32 e 2147483647]
"size must be between 32"
[ERROR] Tests run: 43, Failures: 3, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```

The Redis failures exposed a real configuration assumption; explicit
`REDIS_DATABASE` and assertions fix it. The JWT check was correctly rejecting
the short test secret, but the test expected English on a Portuguese JVM;
it now asserts the validation code independently of locale. Negative startup
tests intentionally log missing-setting warnings; those are expected assertions.

Owner persistence tests use H2, not PostgreSQL. `BackendApiIntegrationTest`
uses MockMvc: real JWT/controllers/services/JPA with simulated transport, not
a listening HTTP server; both its lifecycle and new skill contract cases passed.
The 311 total also includes the tester's separate `BackendLiveHttpTest` (one case),
which does use listening Tomcat/real HTTP but retains H2 and a mocked rate limiter.
Context-runner configuration tests construct clients without connecting to
PostgreSQL/Redis. No Google provider sign-in or hosted deployment was performed.

## Remaining validation and retained limitations

The coordinator released tester-owned e2e application isolation before the
successful full verification. Those paths were not edited by backend workers. The
new auditor is test-only and is excluded from the runtime image. No other agent's
plans, frontend files or AGENTS.md were changed.

The separate tester report covers PostgreSQL feedback/state/availability
concurrency and real Redis fixed-window Lua counters. The coordinator's rehearsal
adds legacy migration, prod-image and the eight-request PostgreSQL identity race
evidence described above. Larger production DDL lock/index timings, hosted TLS
credentials, Render/Supabase/Vercel connectivity and Google sign-in remain
external verification. The local image smoke does not establish hosted readiness.

Earlier architectural limits remain: matching ranks a bounded candidate set in
memory, and availability uses a fixed-week DST approximation. At scale, candidate
caps can omit otherwise relevant people; timezone transitions can require users
to confirm the concrete meeting time. This task does not claim to remove those
limits. Bilateral feedback publication and all F1/F2 contracts remain unchanged.
