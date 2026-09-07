# Backend improvement and frontend design integration coordination plan

Date: 2026-09-05
Project root: `C:\Projects\match-skill`

## Objective and evidence

Improve backend quality and integrate the new Open Design screens with the
existing application and API. The user authorized implementation after each
agent saves its own plan. Current project documentation is in
`docs/documentation.md`, with earlier findings in `docs/planning.md` and
validation guidance in `docs/test-plan.md`. Revalidate earlier findings against
the current source; do not assume that they remain unresolved.

The design source exists at
`C:\Users\Patrick\AppData\Roaming\Open Design\namespaces\release-stable-win\data\projects\3a6da6e9-49c1-4b27-ad68-b20c165ae73b`
and contains `react/`, `screens/`, `styles/`, and brand/design documentation.

## Ownership and sequence

1. Coordinator updates `AGENTS.md` to require a saved plan before code changes
   and records the current backend/frontend directory ownership.
2. Backend agent reads project Markdown documentation and inspects `backend/`
   and the existing frontend API usage. It saves
   `docs/backend-modernization-plan.md` before implementation.
3. Frontend agent reads project documentation, inspects the existing frontend,
   backend API contracts, and the design source. It saves
   `docs/frontend-design-integration-plan.md` before implementation.
4. Both agents implement independently within their owned directories. Preserve
   existing user work. Do not replace the application wholesale, introduce
   unrelated features, or change deployment architecture without evidence.
5. Frontend records any required backend adjustments in
   `docs/frontend-backend-adjustments.md` and returns that document to the
   coordinator. The coordinator assigns those changes to the backend agent.
6. Each agent runs appropriate builds and tests, updates its plan with actual
   outcomes, and writes a completion report. Remaining integration dependencies
   must be explicit; an unresolved dependency is not a completed feature.

## Backend assignment

Read the project Markdown documentation to understand the product and its
business rules, then trace the backend code and existing HTTP consumers.
Identify and implement evidence-backed improvements to structure,
maintainability, correctness, validation, data access, and performance. Look
for missing behavior and bottlenecks. Prioritize actual defects and measured
or demonstrable inefficiencies over speculative abstractions.

Revalidate `docs/planning.md`. Keep controller/service/repository boundaries
and DTOs explicit. Preserve routes, authentication behavior, response shapes,
and business semantics required by the current frontend. Document unavoidable
contract changes for the coordinator before changing a shared contract.
Plan schema changes and their compatibility; do not operate on production
data or deploy as part of this assignment.

Own `backend/`, `docs/backend-modernization-plan.md`, and
`docs/backend-modernization-report.md`. Read other surfaces as necessary.
Do not edit `frontend/`, `AGENTS.md`, or frontend-owned documents.
Report findings with file references, implemented changes, deferred risks,
test/build commands and results, and exact failing lines when checks fail.

## Frontend assignment

Use the supplied Open Design project as the source for the new screens and
visual direction. Read its design/brand documentation, inspect all generated
screens and React assets, then integrate the relevant screens into the actual
frontend stack. Preserve working routes, authentication/session handling,
forms, API integration, and the documented matching/exchange lifecycle.

Adapt generated markup, components, styles, and assets to the existing
application. Replace prototype/mock interactions with real supported flows.
Keep responsive behavior, accessibility, loading/empty/error states, and
validation coherent. Treat the external Open Design directory as read-only.

Own `frontend/`, `docs/frontend-design-integration-plan.md`,
`docs/frontend-design-integration-report.md`, and
`docs/frontend-backend-adjustments.md`. Do not edit `backend/` or `AGENTS.md`.
For each backend dependency, document the screen/flow, existing endpoint and
limitation, proposed request/response contract, compatibility requirements,
and acceptance criteria. Notify the coordinator when that document is ready;
continue screens that do not depend on those changes.

## Verification and reporting

Discover build/test commands from each project's actual configuration and
record baseline failures separately from regressions. Run relevant automated
checks; frontend validation also includes rendering and responsive inspection
when the available environment supports it. Do not claim a check passed if
it was not run. Preserve existing contracts across concurrent edits.

Both live terminals currently display `C:\` as their working directory.
Use `C:\Projects\match-skill` explicitly for project operations.
The project root is not a Git repository; inspect each component's repository
status before making Git assumptions. Do not initialize Git or discard work.

The coordinator receives handoffs and routes frontend backend-dependency
requests. Agents should write their reports to the files above and notify the
coordinator terminal when a dependency or completion report is ready.

## Integration handoff: F1 and F2

Status: assigned to backend on 2026-09-05 after reading the frontend plan and
`docs/frontend-backend-adjustments.md`. Backend must update its saved plan
before implementing these additions. Frontend owns its adapters and flow tests;
tester owns an independent validation plan/report and must coordinate any
additional test-file edits before writing inside frontend or backend.

- F1: extend `PUT /api/me/availability` with optional `timeZone` alongside
  `windows`. Omission preserves the existing zone. Validate and persist the
  zone and windows atomically; preserve the existing array response. Subsequent
  `GET /api/auth/me` and user profiles return the saved zone. Invalid input must
  not partially replace availability. Preserve local window clock times.
- F2: add participant-only `GET /api/exchanges/{id}/feedback` returning
  `{ mine: FeedbackResponse | null, theirs: FeedbackResponse | null,
  counterpartSubmitted: boolean }` for a COMPLETED exchange. `mine` is always
  available to its author; return no counterpart rating/comment before the
  viewer submits. Retain POST and its duplicate-submission 409 contract.
  Apply the visibility rule to profile feedback reads too. The Open Design
  plan's Q8 explicitly requires mutually blind feedback; frontend-only hiding
  cannot satisfy it. Backend must assess reputation aggregates and third-party
  reads as possible indirect disclosures, document its chosen publication
  policy and effects on ranking, and notify the coordinator before changing
  aggregate/publication semantics. Do not silently claim full blindness while
  exposing the hidden score through another read path.
- F3/F4: frontend continues with real, deduplicated profile enrichment and
  paginated exchange lookups. Additional API enrichment/filtering is deferred
  until F1/F2 and existing modernization are validated.

Backend records the adopted contracts and verification in
`docs/backend-integration-contracts.md` and notifies the coordinator as soon as
they are ready. Frontend incorporates those contracts and updates its own
adjustments document; neither agent overwrites the other's documents.

Baseline evidence reported by owners: frontend build passes, `npm test` has
no test files, lint has 52 errors; backend baseline has 140 tests with one
failure and seven errors in rate-limiting tests. Docker is unavailable in both
checked contexts. Distinguish mock/H2 verification from PostgreSQL/Redis or
live integration coverage in reports.

## F2 publication policy decision

Backend proposed the policy before changing queries; coordinator accepted it
on 2026-09-05 as necessary to enforce the design's mutually blind reviews.
The wire contract is specified in `docs/backend-integration-contracts.md`.

- An unpaired review is private to its author. Once both participants submit
  on a COMPLETED exchange, both reviews become public together.
- Public profile feedback and all reputation aggregates/ranking exclude
  unpaired reviews. An author can still read their own unpublished review.
- Existing unilateral reviews remain stored but stop affecting reputation
  until paired. There is no timeout release or deletion. Public review lists
  and reputation totals must use the same publication predicate.
- Frontend must distinguish saved feedback from published feedback and explain
  the pending state without displaying another user's hidden rating/comment.
  Counts must come from published aggregates, not the length of a personalized
  list that may include the viewer's own unpublished review.
- Tests must cover both participant directions, a third-party viewer, legacy
  unilateral reviews, profile/match/search aggregates, concurrent second
  submissions, duplicate conflicts, and publication after both reviews commit.
  Check both participants' profile endpoints: feedback is received by the
  profile owner, so choosing only the counterpart profile misses the review
  received by the current viewer.

At the time of this decision implementation and validation were pending; policy
acceptance alone did not make the endpoint available. Historical data already read externally cannot
be made private retroactively. The policy change has no destructive migration;
deployment remains outside this assignment.

## Backend integration checkpoint

2026-09-05: F1/F2 are implemented and locally validated. Coordinator read the
updated `docs/backend-integration-contracts.md`, independently summed the
Surefire XML reports (23 suites, 203 tests, zero failures/errors/skips), and
confirmed the executable JAR exists. Backend reports a successful
`./mvnw.cmd -B clean verify`. The final modernization report is still being
written; it was not present at this checkpoint.

Frontend is released to integrate the actual F1/F2 contracts and replace their
temporary fallbacks. Tester may now independently inspect the stable backend
contracts, implementation and existing test evidence, recording remaining
coverage gaps without editing backend/frontend files. Full integrated and
visual validation awaits the frontend's ready checkpoint.

Coverage includes actual Spring HTTP/JWT/H2 integration with mocked rate
storage. PostgreSQL locking/query plans, real Redis Lua behavior, and Google
OAuth provider exchanges remain unverified. This checkpoint does not authorize
deployment or claim end-to-end validation against those external services.

## Backend final handoff

The coordinator has now read `docs/backend-modernization-report.md`. Backend
implementation and its local verification are complete; independent review
and frontend integration remain in progress. The final report records the
203-test passing build and resolved baseline failures, with no owner crossover.

Tester should distinguish documented residual limitations from newly introduced
regressions: in-memory ranking, the fixed reference week for availability/DST,
multi-query reads with different concurrent snapshots, existing scheduling
semantics, production index rollout, and external-service verification. Check
their concrete effect on the integrated frontend and report any actual blocker
with reproducible evidence. Do not expand scope or alter implementation merely
to clear a generic checklist.

## Frontend checkpoint and integrated validation release

Frontend declared a stable implementation on 2026-09-05: F1/F2 use real API
adapters, build passes, 23 Vitest tests pass, lint reports zero errors and nine
Fast Refresh warnings. Coordinator verified the Playwright JSON report:
13 expected passes, zero unexpected failures, flakes or skips; last run passed.
The browser suite covers 11 authenticated screens at 320/375/768/1440 and axe
checks at 375/1440 using intercepted Spring-shaped fixture responses. The final
frontend report is still being written at this checkpoint.

Evidence clarification: BackendApiIntegrationTest uses actual Spring components,
JWT and H2 through MockMvc; it does not start a listening HTTP server. Existing
frontend browser tests intercept API calls. Neither independently proves the
browser-to-running-Spring integration.

Tester is now released to validate both stable surfaces and create the missing
local browser-to-Spring check. Update `docs/tester-integration-plan.md` first.
The following new paths are reserved exclusively for tester:

- `backend/src/test/java/com/matchskill/backend/e2e/`
- `backend/src/test/resources/e2e/`
- `frontend/tests/live/`
- `frontend/playwright.live.config.ts`

Existing production files, build configuration, and owner tests remain owned
by backend/frontend agents. Coordinate any additional required files before
editing. Use disposable local data and distinct available localhost ports;
do not connect the test harness to Supabase or other production services.
Test-only H2 and mocked rate storage are acceptable when explicitly reported;
the Spring server, JWT, HTTP requests and browser UI must be real for this check.
Never disable production rate limiting or auth to make the harness pass.
Stop only helper processes created by this validation after collecting results.

Prioritize registration/login/session reload, skills setup, matching/request,
accept/schedule/complete, atomic time-zone save, feedback private/public states,
participant authorization, and refreshed history. Do not intercept the backend
API with fixture responses in tests labelled live. Independently inspect visual
artifacts for clipped content as well as automated overflow/accessibility checks.
Report exact commands, reproducible failures and infrastructure substitutions.
Route application fixes back to their owners and rerun affected checks after
fixes; full completion requires resolving any confirmed integration blockers.

## Frontend final handoff

Coordinator read `docs/frontend-design-integration-report.md` and verified
`frontend/qa/design-integration/browser-results.json`: 14 expected passes,
zero unexpected failures, skips or flakes. This final run supersedes the
13-scenario browser checkpoint; the added scenario covers search/profile
requests. Frontend reports the same passing build, 23 Vitest tests and zero
lint errors with nine Fast Refresh warnings. Evidence includes 26 screenshots.

The implementation and owner tests remain stable; tester's live paths stay
reserved. F1/F2 are connected without temporary fallbacks. Full application
validation remains pending the tester's live browser/Spring results.

The report also distinguishes remaining scope: no rescheduling UI; best-effort
duplicate preflight without server-side pair uniqueness; profile enrichment
round trips; deployment deep links/API origin not verified. Installation
reported seven npm advisories (five moderate, one high, one critical), without
package-level assessment in this handoff. Do not represent the passing feature
tests as dependency-security clearance or deployment readiness.

## Pending-work closure checkpoint: 2026-09-07

This checkpoint supersedes the pending local-validation and dependency items
above. See [project-status.md](project-status.md) for the current consolidated
state and [pending-work-closure-plan.md](pending-work-closure-plan.md) for the
authorized follow-up scope and final evidence.

Backend clean verify passed 311 tests/28 suites. Tester separately passed 9
real PostgreSQL/Redis integration tests and 6 live browser tests. Frontend build,
lint (zero warnings), 28 Vitest and 2 Node cases passed; fixture browser evidence
covers 22 distinct scenarios across several affected runs. Normal and production
npm audit both report zero vulnerabilities after the documented dependency fixes.

The coordinator rehearsed additive SQL on legacy data and tested the resulting
Docker image with prod/schema validation and a separate real Redis database.
Existing identifiers/data and API compatibility were preserved. Owner plans and
reports contain actual failure corrections and validation boundaries. Do not
rerun green suites without a relevant code change or unresolved finding.

Hosted URLs/configuration, provider TLS/connectivity and real Google OAuth remain
external inputs. No hosted deployment, production SQL or GitHub publication was
performed. In-memory ranking, DST approximation, concurrent read snapshots,
best-effort duplicate-request preflight and the absence of rescheduling UI remain
explicit boundaries. Implementation ownership and tester path reservations stay
in force for future tasks.
