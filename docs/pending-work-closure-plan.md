# Pending work closure plan

Date: 2026-09-07. Owner: coordinator. Status: local implementation and checks
passed; hosted verification awaits external environment configuration.

## Objective and current evidence

Close the remaining implementation, validation and documentation gaps authorized
by the user. Retain the existing Spring/React/Supabase/Render/Vercel stack and
the current HTTP contracts except explicitly documented compatible extensions.

Previous saved evidence: backend 203 tests and build passed; an additional live
HTTP test passed; frontend 23 unit tests, 14 fixture browser scenarios and two
live browser/Spring journeys passed. Live validation used H2 and a mocked rate
limiter. Docker is now reachable (server 29.6.2), so isolated PostgreSQL/Redis
verification can replace that limitation. Existing datasets must remain intact.

Three visual findings in the tester report require browser reproduction rather
than relying on full-page screenshot positions. Backend has two confirmed input
issues: C/C++/C# slug collisions and meeting URLs exceeding varchar(255). Seven
npm advisories were recorded without package-level triage; verify current audit
data before selecting compatible dependency updates.

## Ownership

- Backend owns backend production code and existing owner tests, plus
  `docs/backend-pending-fixes-plan.md`, `docs/backend-pending-fixes-report.md`,
  and any backend SQL rollout instructions under backend/.
- Frontend owns frontend production code, dependencies and existing owner tests,
  plus `docs/frontend-pending-fixes-plan.md` and
  `docs/frontend-pending-fixes-report.md`.
- Tester owns existing reserved backend e2e Java/resources, frontend/tests/live/,
  frontend/playwright.live.config.ts, `docs/tester-closure-plan.md`, and
  `docs/tester-closure-report.md`. Coordinate new paths before editing.
- Coordinator owns this plan, docs/coordination-plan.md,
  docs/documentation.md,
  `docs/deployment-readiness.md`, `docs/project-status.md`, and
  `docker-compose.validation.yml` for isolated service provisioning.
- Before any implementation each owner saves its own scoped plan in docs/.

## Work sequence and acceptance

1. Backend: fix vocabulary identity without silently reassigning existing IDs
   or renaming existing slugs. Keep normalized text compatibility, distinguish
   symbol-bearing terms, reject meaningless suggestions, and define collision
   and legacy behavior. Cover C/C++/C#, punctuation, case/accent normalization,
   existing records and duplicate requests with regression tests. Avoid naive
   substring replacement that changes unrelated names.
2. Backend: support legitimate long meeting URLs with explicit validation aligned
   with storage (target 2048 characters), retain HTTPS/host checks, and document
   the additive schema rollout. Test persistence of >255, boundary limits,
   invalid inputs and rollback. Preserve all API shapes and stable errors.
3. Frontend: reproduce footer/navigation occlusion and tab spacing at mobile
   viewports with scrolling, hit testing and viewport screenshots. Fix actual
   interaction defects; document screenshot-only artifacts honestly. Remove
   Fast Refresh warnings through proper component/helper boundaries rather
   than disabling checks. Align scheduling URL validation with backend.
4. Frontend: inspect current npm audit, identify affected packages and production
   versus development reachability, apply supported compatible updates, and
   validate build/lint/unit/browser workflows. Avoid force upgrades and unrelated
   framework migrations. Document any remaining actionable advisory precisely.
   Prepare Vercel SPA/API configuration as needed without deploying.
5. Coordinator provisions isolated PostgreSQL/Redis containers on separate local
   ports using an explicit validation compose project. Inspect running services
   first. Use disposable data, local-only bindings and no production credentials.
6. Tester verifies updated backend against actual PostgreSQL and actual Redis
   Lua, including concurrency/atomicity, F1/F2 visibility and aggregates, long
   URLs, and rate-limit success/429/failure behavior. Test browser-to-Spring
   using those services and authenticated real requests. Never disable production
   authentication/rate limiting. Test-only fixture seeding is allowed and must
   be documented. Run when owners release stable changes; prepare harness first.
7. Coordinator reconciles contracts and setup documentation, verifies local
   deployment configuration and index rollout guidance, and checks whether
   Google OAuth test configuration is available without exposing secrets.
   Perform provider verification only in a user-authorized test account/environment;
   report missing account/configuration requirements precisely when unavailable.
   Preserve the pre-fix executable JAR in a task-specific temporary directory,
   bootstrap only the separate matchskill_smoke database with it, then validate
   additive SQL against legacy rows before testing the new Docker image. Record
   hashes and process IDs; never modify the tester's matchskill_validation data.
8. Tester reruns checks affected by corrections and independently inspects final
   visual evidence. Coordinator records evidence and remaining external blockers
   in docs/project-status.md. Do not describe missing external verification as
   a completed production deployment.

## Boundaries and risks

No deployments, production data operations, Git initialization, or destructive
resets. Each agent preserves other owners' files. New terminal handles after
restart must be rediscovered; coordinator is currently
`term_e84d9f20-a1ee-400b-ae5c-c187c85a90d4`.

Keep the previously accepted bilateral feedback publication rule. Ranking's
in-memory scale limit and fixed-week DST approximation remain explicit
architectural constraints; this closure must document their user-facing impact,
not claim they were eliminated by unrelated fixes. Google provider sign-in and
hosted Vercel/Render/Supabase verification may require external account input.
Do all available local preparation before asking for missing information.

## Validation results

Backend clean verify passed 311 tests in 28 suites, zero failures/errors/skips.
The coordinator independently checked XML totals excluding the tester's separate
9-case PostgreSQL/Redis suite, which also passed. Tester live browser JSON
records 6 passes with no failures/skips/flakes. These runs cover actual Redis
Lua, PostgreSQL persistence and sampled concurrent requests, not hosted services.

Frontend build and lint passed with zero warnings/errors, 28 Vitest and 2 Node
tests passed. Fixture browser runs cover 22 distinct scenarios across the
documented 18/8/3 executions; do not add overlapping counts. Both normal and
production npm audit artifacts report zero vulnerabilities.

Coordinator rehearsal passed all three SQL scripts against matchskill_smoke
bootstrapped by the pre-fix JAR. Six indexes, legacy data preservation, prod
Docker startup/schema validation, JWT continuity, symbol identities, URL2048
persistence/2049 rejection and real Redis database1 were verified. Eight parallel
same-name suggestions returned one identity and persisted one PostgreSQL row. See
[deployment-readiness.md](deployment-readiness.md) for hashes, commands and
the explicit local TLS exception. Initial PowerShell response-body inspection
failed; HttpClient confirmed the correct 400 VALIDATION_ERROR without a code fix.

Owner reports preserve resolved failure lines, including localized validator
assertions, explicit Redis database selection and frontend tab spacing. Tester
runners now use scoped TestConfiguration; standard component scanning cannot
import their mocked H2 rate limiter. Separate Maven runs must not overlap against
shared target/; the backend's final full build is the authoritative standard run.

External Google/provider/hosted verification could not proceed because no
authorized environment configuration was supplied. No deployment or production
mutation occurred. Final results and retained constraints are consolidated in
[project-status.md](project-status.md).

After tester release, its helper ports were confirmed inactive. The coordinator
saved Docker logs, stopped/removed only matchskill-closure-smoke, and ran the
validation compose project's down command. Disposable containers/network were
removed; image, JAR and reports were preserved. No unrelated service was changed.
