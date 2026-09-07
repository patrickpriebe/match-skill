# Frontend design integration report

Date: 2026-09-05. Owner: frontend agent.
Status: implementation complete; local frontend checks passed. Independent integration validation is with the coordinator/tester.

## Delivered behavior

Integrated the Open Design visual system and screens into the existing React 18, TypeScript, Vite, React Router 6 and Tailwind 4 application. Authentication remains Spring JWT with local credentials and the existing Google callback. No framework, authentication-provider, database or deployment migration was introduced. The external Open Design project was only read; backend files, AGENTS.md and other agents' plans/reports were not edited.

Visual components are under `frontend/src/components/design/`, avoiding Windows case collisions with the existing shadcn `components/ui/button.tsx` and related files. Existing utility tokens remain in globals.css; the imported design maps its colors to those tokens. Source Serif 4, Public Sans and JetBrains Mono are served from installed font packages. No runtime Google Fonts import remains. Desktop uses the approved sidebar and trade hierarchy; mobile uses five navigation destinations with availability reachable from the own profile.

| Screen/flow | Implemented behavior |
|---|---|
| Login and registration | Token-only auth responses are followed by authenticated auth/me; registration sends displayName and timeZone plus credentials; password size/requirements are communicated |
| OAuth callback | Consumes the issued token, removes it from the URL, establishes authenticated context and preserves onboarding routing |
| Skills registration/edit | Controlled vocabulary, pending suggestion badges, debounced keyboard picker, error handling, real replacement payload, session refresh before matches |
| Matches and search | Server ranking/pagination preserved, separate MUTUAL/PARTIAL sections, profiles enriched from real endpoints, actual request buttons with explicit approved skill selection |
| Profile | Actual skills, zone, availability, official reputation average/count and authorized feedback; self-profile links to skills and availability editing |
| Invitations | Sent/received views over paginated results; receiver-only accept/decline; requester skill selection supplied for either strength; terminal rows remain visible |
| Scheduled | Filters the actual exchange endpoint by SCHEDULED and opens the corresponding exchange |
| Exchange details | Actual participants, trade directions, current state, created/updated timestamps; no fabricated transition dates or actors; failed actions display errors |
| Scheduling | ACCEPTED-only UI, explicit account-zone conversion to an ISO instant, dual-zone echo, future time validation, rejected-link feedback, no invented availability overlap |
| Availability / F1 | Atomic timeZone and windows payload, actual account refresh, valid IANA options, range validation including mixed LocalTime precision and same-day overlap |
| History / F2 | COMPLETED-only pagination and official total; each exchange reads real own feedback state; shows private saved vs published status |
| Feedback / F2 | Real mine/theirs/counterpartSubmitted endpoint; immutable saved rating; private-until-paired explanation; published counterpart review shown only when returned |

Legacy `/home`, `/skills/register` and `/auth/callback` routes remain. `/matches` and `/skills` are aliases. New routes are `/search`, `/profile/:id`, `/profile/me`, `/invitations`, `/scheduled`, `/scheduled/:id`, `/exchanges/:id`, `/history`, `/feedback/:id`, and `/availability`.

## API compatibility

The production entry point is `src/lib/api/index.ts`; it always selects the real Spring client. Legacy prototype fixtures remain isolated under `src/lib/api/mock/` and are not imported by the application. `VITE_USE_MOCK_API` is no longer an application switch; `.env.example` documents the real API setup. `VITE_API_BASE_URL` still configures the API origin, with `/api` and the existing Vite proxy as the development default.

The adapters explicitly map token-only auth; UserSkillResponse.skill wrappers; offeredSkillIds/wantedSkillIds; flat MatchResponse summaries; profile skillsOffered/skillsWanted; paginated matches/search/exchanges; and array availability responses. Requests and transitions are not followed by fallible enrichment calls that could misreport a successful mutation as a failed one.

F1/F2 were connected only after the coordinator released the implemented contracts in `backend-integration-contracts.md`. Temporary fallbacks are removed. Published reputation always comes from the server, never from the length of a personalized feedback list. The latter may include the viewer's own unpublished review.

F3 uses a per-load deduplicated profile resolver for missing names, skills and zones. F4 checks real exchange pages before a request and offers the existing exchange when found. These fallbacks use real API data.

## Validation executed

All commands below ran explicitly from `C:/Projects/match-skill/frontend`.

| Command | Actual result |
|---|---|
| `npm run build` | Passed: TypeScript project build and Vite production bundle; 1637 modules; JS about 252 kB / 79.6 kB gzip, CSS about 45.3 kB / 9.9 kB gzip |
| `npm test` | Passed: 3 files, 23 tests |
| `npm run lint` | Exit 0: zero errors, 9 existing-pattern Fast Refresh warnings in imported design files that export helpers alongside components |
| `npm run test:browser` | Coordinator checkpoint: 13 fixture scenarios passed. The already-running final suite added search/profile request coverage and finished with 14 passed in 20.5 seconds, zero failures/skips/flaky results |

Unit/contract tests:

- `frontend/src/lib/api/realApiClient.test.ts`: 15 cases covering token-only auth, registration fields, skill mapping and writes, pagination/enrichment, acceptance payloads, later-page open exchanges, network/server errors, F1 payload and F2 complete/null fields and 403/404/409 preservation.
- `frontend/src/lib/scheduling.test.ts`: 5 cases covering account-zone UTC conversion, fractional offsets, invalid input, DST gap/fold rejection, and both participant trade directions.
- `frontend/src/lib/availability.test.ts`: 3 cases covering adjacent mixed-precision windows, overlap by day, and blank/malformed/reversed ranges.

Browser tests are in `frontend/tests/design-integration.spec.ts`, with controlled Spring-shaped HTTP responses in `frontend/tests/fixtures.ts`. They exercise the actual React application and real fetch adapter in headless installed Microsoft Edge. `playwright.config.ts` starts and stops its own Vite process on port 4173. No other agent terminal was stopped or interrupted.

Browser coverage: registration and onboarding, explicit request skill selection, search/profile requests, existing-exchange recovery, modal Tab trapping/Escape, acceptance payload, scheduled filtering, scheduling with retained inputs after a 409, F1 zone save/session refresh, F2 private saved/refresh/published states, OAuth callback restoration, failed vocabulary lookup, and match pagination. This is fixture-backed browser integration, not a running Spring/database end-to-end test.

At the coordinator's stable checkpoint, the recorded results were 23 Vitest tests, 13 Playwright fixture scenarios and 9 Fast Refresh warnings, with no live Spring browser validation. The final 14-scenario run was already in progress before the instruction to freeze the version; no green checks were repeated after that instruction. The saved JSON reflects this final run.

Responsive and accessibility checks: 11 application screens at 320, 375, 768 and 1440 px with no horizontal overflow. Login also checked at 320, 375 and 1440 px. Automated axe WCAG A/AA checks passed for all 11 screens at 375 and 1440, login at the three widths, and dark-mode home. The modal's native dialog and explicit focus loop prevent focus escaping the dialog. Tabs, choices and ratings support keyboard controls. These automated checks do not claim a complete screen-reader audit.

Saved evidence: `frontend/qa/design-integration/` contains the browser JSON report and 26 screenshots, including `home-1440.png`, `home-375.png`, `home-dark.png`, `login-1440.png`, `availability-375.png`, `scheduled-accepted-1440.png`, and `feedback-completed-375.png`. Visually inspected desktop home/login/scheduling, mobile availability/feedback and dark home for layout and fidelity to the supplied design.

## Baseline failures and resolved integration failures

Before implementation, build passed but tests and lint did not:

```text
No test files found, exiting with code 1
26:31  error  'HTMLDivElement' is not defined  no-undef
52 errors, 1 warning
```

ESLint's JavaScript no-undef rule was applied to TypeScript DOM identifiers; TypeScript now owns that check. Unused legacy mock password extraction is explicitly recognized by the underscore convention. Production types and fixtures remain type checked.

During adaptation, compile checks caught the imported Surface ReactNode boolean typing and required loading children; those were corrected. Installed variable-font packages did not contain the initially assumed subset path:

```text
[vite]: Rollup failed to resolve import "@fontsource-variable/source-serif-4/latin.css"
```

The actual installed package entry points replaced that path. A first browser fixture intercepted Vite module URLs containing `/api/`; restricting interception to pathname `/api/` fixed the test harness. A keyboard test then exposed focus leaving a dialog:

```text
tests/design-integration.spec.ts:66
Expected: true
Received: false
```

The dialog now loops focus explicitly and the repeated interaction test passed. A subsequent JSX edit was caught by the build before final verification:

```text
Tabs.tsx: Unexpected token, expected "}" (34:12)
```

It was corrected; final builds and browser runs pass. One successful repeated browser run also logged a Vite proxy `AggregateError [ECONNREFUSED]` during feedback-page teardown; no Spring server was running, and all assertions passed. This is retained as evidence that these runs are not live backend validation.

## Remaining limits and handoff

- Independent tester/coordinator owns integrated validation against a running Spring instance. This frontend agent did not claim PostgreSQL, Redis or real Google-provider coverage. Backend's separately reported 203 passing tests are not counted as frontend tests.
- F3 list enrichment still requires profile requests. Requests are deduplicated within a load, not globally cached. A backend summary contract could remove those round trips later.
- F4 preflight is best effort: current ExchangeService does not enforce pair uniqueness atomically, so simultaneous requests from separate sessions can still create duplicates. No unsupported conflict guarantee is claimed.
- Recurring availability is displayed in each owner's zone; the UI does not claim exact cross-zone weekly overlap. Account-zone meeting conversion rejects ambiguous/nonexistent local times instead of silently picking a DST offset.
- The approved UI has no rescheduling flow. The current backend accepts scheduling from SCHEDULED too; this existing server capability was not removed or exposed as a new UI feature.
- The installed dependency tree reported 7 npm audit advisories during installation (5 moderate, 1 high, 1 critical). No forced or unrelated dependency migration was performed in this design integration task.
- Deployment was not performed. Vercel API-origin configuration and SPA deep-link handling remain environment validation items for the coordinator.

The coordinator released the tester for independent integrated validation. `frontend/tests/live/` and `frontend/playwright.live.config.ts` are reserved exclusively for the tester, together with its backend test-only harness; the frontend owner will not edit those paths. The current application and existing tests remain stable pending findings routed through the coordinator. No live Spring validation is claimed by this report, and no backend feature is represented as complete merely because a screen was drawn.
