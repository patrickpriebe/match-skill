# Project status

Updated: 2026-09-07. Owner: coordinator. Status: local implementation and validation
passed; hosted configuration and provider verification remain external work.

## Delivered before this closure

- Backend modernization and F1/F2 contracts: passing build, 203 tests.
- Frontend design integration: passing build, 23 Vitest tests, 14 fixture browser
  scenarios, zero lint errors and nine Fast Refresh warnings.
- Independent local integration: one live Spring HTTP test and two browser/Spring
  journeys passed, using H2 and mocked rate limiting. Reports dated 2026-09-05
  are historical evidence, not a fresh full run on the current edits.

## Current work

| Item | Owner | Status |
| --- | --- | --- |
| Skill identity and meaningless suggestions | Backend | Implemented; C/C++/C# distinct, legacy IDs/slugs preserved, explicit 400/409 contracts |
| Meeting URL storage/validation to 2048 | Backend/frontend | Implemented; boundary/rollback confirmed in PostgreSQL and Docker upgrade |
| Mobile occlusion/spacing reproduction and fixes | Frontend/tester | Tab spacing fixed; footer/navigation accessible; independent live checks passed |
| Dependency audit and Fast Refresh warnings | Frontend | Full and production npm audit zero; lint zero errors/warnings |
| Real PostgreSQL/Redis tests and browser integration | Tester | 9 live integration cases and 6 live browser cases passed |
| Docker image, environment/deep-link setup, schema rollout | Backend/frontend/coordinator | Image and legacy SQL rehearsal passed locally; Vercel config/guard prepared |
| General documentation reconciliation | Coordinator | Updated to match actual stack/contracts |
| Hosted Supabase/Render/Vercel and real Google sign-in | Coordinator/user | Environment configuration needed; no deployment |

PostgreSQL 15.19 and Redis 7.4.11 were provisioned in the isolated validation
compose project. They are distinct from existing development data.
After validation, the helper servers and disposable containers were shut down;
the tested image, preserved JAR and reports remain available.

## Final local evidence

- Backend `mvnw.cmd -B clean verify`: **311 tests in 28 suites**, zero failures,
  errors or skips. Coordinator independently summed the matching XMLs. This
  includes the H2 live HTTP test and 24 corrected deployment configuration cases;
  it excludes the separately executed PostgreSQL integration suite.
- Tester `PostgresRedisLiveIT`: **9 tests**, zero failures/errors/skips, actual
  PostgreSQL 15.19 and Redis 7.4.11. Covers rate counters/TTL/429, controlled
  fail-closed behavior, URL boundaries, F1 rollback, F2 publication, simultaneous
  same-author feedback, competing complete/cancel and availability replacement,
  and distinct symbol-bearing skill identities. These are bounded functional
  concurrency checks, not production load or all possible interleavings.
- Tester live browser JSON: **6 passes**, zero failures/skips/flakes, using real
  Spring/PG/Redis: two journeys and four responsive checks. The 2026-09-07
  17:43:01 UTC run lasted approximately 9.97 seconds.
- Frontend owner: build passed; lint zero errors/warnings; **28 Vitest + 2 Node
  tests**. Fixture browser runs were 18, then 8 focused, then 3 affected cases,
  covering **22 distinct scenarios**, not one combined run. Coordinator checked
  the retained 18- and 3-case JSON and both zero-vulnerability npm audit files.
- Coordinator upgraded a separate database created with the old JAR. All three
  SQL scripts passed in separate psql sessions; six indexes verified; legacy
  IDs/slugs/data preserved. Docker image started with `prod`/schema validation,
  accepted the old JWT, distinguished C/C++/C#, persisted URL2048, rejected2049
  without changing it, and incremented real Redis database1. Local PostgreSQL
  TLS was explicitly disabled for this disposable rehearsal.
  Eight concurrent HTTP suggestions for one new skill also returned the same
  ID/slug, with exactly one persisted PostgreSQL row/key.

Details: [backend report](backend-pending-fixes-report.md),
[frontend report](frontend-pending-fixes-report.md),
[tester report](tester-closure-report.md), and
[deployment rehearsal and setup](deployment-readiness.md).
The saved [closure plan](pending-work-closure-plan.md) records ownership.

## Remaining scope

Hosted Supabase/Render/Vercel connectivity, production-shaped TLS, SPA deep links
on the actual host, and real Google OAuth require the authorized environment
URLs/configuration and a test identity. None was supplied during this closure;
no hosted deployment, production SQL or GitHub publication was performed.
The folder has no Git metadata; a target repository is needed for publication.

Retained architectural constraints: matching ranks a bounded candidate set in
memory; recurring availability uses a fixed reference week for DST; independent
reads can observe different snapshots during concurrent feedback commits.
The UI still has no rescheduling flow, duplicate invitation preflight remains
best effort, and pending skill suggestions require approval before assignment.
These are documented product/scale boundaries, not failed closure tests.
