# Deployment preparation and external verification

Updated: 2026-09-07. Owner: coordinator. No deployment has been performed.

## Current scope

The application remains a React/Vite SPA on Vercel, Spring Boot on Render, and
PostgreSQL hosted by Supabase. Redis is required for rate limiting. Supabase
does not replace the application's Spring authentication. Local validation
services are distinct from hosted production accounts.

Backend Docker/production configuration and frontend Vercel configuration are
prepared, with the isolated upgrade rehearsal below completed. Artifact paths and results are
recorded in `project-status.md` and the owners' pending-fixes reports.

## Render backend

Use the backend directory as the Docker build context with its Dockerfile.
Render supports Docker for Java services. Bind the application to all interfaces
and the supplied PORT; retain port 8080 as the local default. Avoid embedding
runtime credentials in image layers. These platform requirements were checked
against [Render Docker](https://render.com/docs/docker) and
[Render web services](https://render.com/docs/web-services).

Configure settings in the host's secret/environment controls, never in Vite:

| Setting | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=prod` | Explicitly activate application-prod.yml |
| `JDBC_DATABASE_URL` | Required full `jdbc:postgresql://...` URL for the selected Supabase endpoint |
| `DB_USER` / `DB_PASSWORD` | Required database identity, stored as secrets |
| `DB_SSLMODE` | Defaults to `verify-full`; provide the trusted root certificate through the JDBC sslrootcert setting when required |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Production defaults to validate after the reviewed schema rollout; do not override to update on hosted data |
| `REDIS_URL` | Required provider URL including authentication/TLS when needed; never the local validation Redis |
| `REDIS_DATABASE` | Explicit logical database integer, default 0; do not rely on a URL path to select it |
| `JWT_SECRET` | Unique environment-specific signing secret; minimum 32 characters as enforced by JwtProperties |
| `CORS_ALLOWED_ORIGINS` | Exact trusted frontend origins, without paths; include a preview only when intentionally allowed |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Optional pair for real Google login; configure both together |
| `GOOGLE_REDIRECT_URI` | When Google login is enabled, exact HTTPS backend `/api/auth/google/callback` URI registered with the provider |
| `OAUTH2_SUCCESS_REDIRECT_URI` | Exact frontend URL ending `/auth/callback` |

The production aliases above were confirmed by the backend owner and checked
against application-prod.yml. Verify startup against that profile.
Never pass the default development JWT secret to a hosted environment.

For an existing database, apply backend/deployment/01-expand-schema.sql,
02-create-indexes-concurrently.sql and 03-verify-schema.sql in the documented
sequence from backend/deployment/README.md before starting with schema validation.
These add the optional skills.identity_key and expand meeting_url to 2048 while
preserving existing IDs/slugs and values. Run concurrent indexes outside a
transaction. For a fresh database,
bootstrap its schema explicitly in a controlled isolated setup; `validate`
cannot create missing tables. Back up hosted data and review lock behavior before
DDL. No production DDL is executed by this task. Do not shrink the URL column
on rollback if longer values have been stored.

## Supabase database connection

Obtain the actual project's connection details from its Connect panel. A direct
connection suits persistent backends when network connectivity permits it;
session pooling is an alternative when needed. Confirm endpoint/user/port as a
unit, and verify TLS and connection limits for the selected project. Use an
appropriate direct/session connection for migrations rather than assuming a
transaction pooler supports every operation. Connection modes are described in
[Supabase's database connection guide](https://supabase.com/docs/guides/database/connecting-to-postgres).

Local PostgreSQL verification covers the database engine; it does not establish
Supabase connectivity, project policies, credentials, or hosted schema state.

## Vercel frontend

Set the project root to frontend/, install with `npm ci`, and retain the
`vercel.json` build command `node scripts/validate-hosted-env.mjs && npm run build`.
Publish dist/. The checked-in vercel.json provides
SPA deep links. Vite SPAs require a fallback to index.html for application paths;
see [Vite on Vercel](https://vercel.com/docs/frameworks/frontend/vite).

Set `VITE_API_BASE_URL` at build time to the actual HTTPS backend origin plus
`/api`. The local Vite proxy is not a production API proxy. No invented backend
host is embedded in deployment files. Every VITE variable is public browser
configuration; do not put JWT, database, Google client secrets or Redis tokens
there. The backend must allow the exact deployed frontend origin.

After hosted deployment is separately authorized, verify direct navigation and
refresh on /login, /skills/register, /profile/me and /exchanges/{real-id}; confirm
that API requests return JSON from the backend rather than SPA HTML. Rebuild
after changing build-time API configuration.

## Real Google OAuth verification

Real provider verification requires a web-application OAuth client, its secret,
an allowed test user if the consent app is in testing, and matching redirect
registration. Google requires the requested redirect URI to match the registered
URI; see [Google's web-server OAuth guide](https://developers.google.com/identity/protocols/oauth2/web-server).

Application-specific paths:

- Browser starts at the backend's `/api/auth/google`.
- Google returns to backend `/api/auth/google/callback`.
- Backend completes provider authentication and redirects to the configured
  frontend `/auth/callback`, which restores the session and removes the token
  from the visible URL.
- Local example only: Google callback `http://localhost:8080/api/auth/google/callback`
  and frontend return `http://localhost:5173/auth/callback`. Actual test harness
  ports differ; register the chosen URI exactly before using that harness.

Verify sign-in, consent denial, callback/session handling and onboarding using
an authorized test identity. A mock provider test cannot be reported as a real
Google exchange. At initial inspection no Google credentials were configured
in this process and no backend environment file was present. The user was asked
where authorized staging configuration exists; never paste secrets into chat.

## Reproducible isolated service checks

From the repository root:

```powershell
docker compose -f docker-compose.validation.yml up -d --wait
docker compose -f docker-compose.validation.yml ps
```

The validation project uses loopback-only PostgreSQL :15432 and Redis :16379.
Its PostgreSQL database/user are matchskill_validation; the password in the
compose file is deliberately a disposable local test credential. PostgreSQL
uses tmpfs and Redis has persistence disabled. There are no mounted production
datasets. On 2026-09-07 the engines reported PostgreSQL 15.19 and Redis 7.4.11.
See the tester closure report for exact test and browser runner commands.

After all workers have stopped using those services:

```powershell
docker compose -f docker-compose.validation.yml down
```

This removes only the named validation project. Do not run global Docker prune
or delete another project's containers/volumes.

## Completed isolated upgrade rehearsal

On 2026-09-07 the coordinator created `matchskill_smoke`, separate from the
tester's `matchskill_validation` database. Redis logical database 1 was reserved
for this rehearsal; tester traffic used database 0. No hosted data was used.

The preserved pre-fix JAR (SHA-256
`1479DCCB0BE6E4B3A504E7E77F8B4503622414856AA967F4789BC8C47ACAC8A9`)
created the legacy schema and two users, approved C++/Java skills, complementary
profiles and a scheduled exchange through real API calls. Only approval of these
disposable skills used SQL. The legacy server was stopped before migration.

Each rollout file was piped unchanged into a separate
`docker compose -f docker-compose.validation.yml exec -T postgres psql -X -v ON_ERROR_STOP=1 -U matchskill_validation -d matchskill_smoke`
session. Files 01, 02 and 03 all exited 0. File 02 executed outside a transaction.
The six expected indexes were valid and equivalent. Existing IDs, names, slugs,
exchange status and stored URL remained intact; both legacy identity keys were
initially null. The SQL hashes at validation were:

| File | SHA-256 |
| --- | --- |
| 01-expand-schema.sql | `313281BB8B83D45655D68A49B858E5BAF9EEFE1B8B16239602196B1CEAA3F889` |
| 02-create-indexes-concurrently.sql | `7810F1812BD0BDE12C36253E51DC180D46B03E9E2B4101C53DE7301E9B36A59F` |
| 03-verify-schema.sql | `4F57AEE1669FB6C90B960F03C8C10E92402B285333CAA5163A8E9B6E86773932` |

Image `matchskill-backend:closure-local`, image ID
`sha256:5708fcbc11b3267ce4677ac090011fe2eb2c01f0c7b7719c51d725e40b8d29d1`,
started as container `matchskill-closure-smoke` on loopback port 18091 with
`SPRING_PROFILES_ACTIVE=prod` and Hibernate schema validation. Java 21 and the
non-root image user were checked by the backend owner. The local PostgreSQL
connection explicitly used `DB_SSLMODE=disable`; this does not verify hosted
TLS. `REDIS_DATABASE=1` selected the reserved Redis database explicitly.

Observed through the Docker application's real HTTP API:

- The pre-upgrade JWT still authenticated, using the same disposable signing
  secret. The old scheduled exchange remained readable.
- Suggesting C++ twice preserved ID `e1e79894-dcc9-40e1-8a4d-c6a845761e14`
  and legacy slug `c`. C and C# received distinct IDs and slugs; no legacy row
  was renamed. SQL confirmed 64-character identity keys on the three names.
- Eight asynchronous suggestion requests for the same new skill were dispatched
  before awaiting their responses. All returned 201 with the same ID and slug;
  SQL confirmed exactly one row and one identity key in PostgreSQL. This adds
  sampled real-database collision coverage to the owner's H2 concurrency test;
  it does not establish behavior under every scheduling interleaving or load.
- A 2048-character meeting URL was saved and read back. A 2049-character URL
  returned 400 `VALIDATION_ERROR`; the stored 2048-character value remained
  unchanged. An initial PowerShell error-body inspection could not read the
  response; HttpClient confirmed the status and JSON without a code change.
- One invalid login returned 401 and created
  `ratelimit:login:172.18.0.1` in Redis database 1, counter 1 and TTL 59 seconds.
  There were no matching keys immediately before the probe. No database 0
  commands or global Redis reset were used in this rehearsal.

Local raw logs and a sanitized smoke-results.json were saved under
`%TEMP%/matchskill-closure-20260907`. That directory also contains disposable
authentication state; do not publish its full contents. The durable results are
recorded here. This small-dataset rehearsal does not establish migration lock
duration under production traffic, production scale, provider TLS or OAuth.

After the tester released ports 18089/4176 and validation services, the
coordinator saved the smoke container logs, stopped/removed only
`matchskill-closure-smoke`, and ran the validation compose project's `down`.
Its two disposable service containers and network were removed. Images,
preserved JARs and reports remain available; unrelated services were untouched.

## External inputs still required

- Authorized hosted frontend/backend URLs, Supabase connection configuration,
  Redis endpoint and environment ownership.
- Google OAuth test client and user-mediated consent for a real provider run.
- Intended GitHub repository/branch when publishing source; no repository is
  inferred or initialized automatically from this folder.
- Separate authorization for hosted deployment and production database changes.

Local code, tests, Docker image and configuration validation can proceed without
these inputs. Hosted service verification remains explicitly pending until the
required environment is supplied.
