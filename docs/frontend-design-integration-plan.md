# Frontend design integration plan

Date: 2026-09-05. Owner: frontend agent. Status: implementation complete and locally verified; stable pending independent live integration validation.

## Objective and evidence

Integrate the approved Open Design screens into the existing React 18 + TypeScript + Vite + React Router 6 + Tailwind 4 application. Retain Spring JWT authentication, the existing API module boundary, and deployment architecture. No Next.js or Clerk migration is needed. The source directory is read-only:
`C:/Users/Patrick/AppData/Roaming/Open Design/namespaces/release-stable-win/data/projects/3a6da6e9-49c1-4b27-ad68-b20c165ae73b`.

Reviewed AGENTS.md, coordination-plan.md, documentation.md, planning.md, test-plan.md; source brand-spec.md, brand-identity.html, match-skill-design-plan.md, React README, screen inventory, styles, pages and API interfaces; current frontend and Spring controllers/DTOs. Neither component currently has a Git repository. Preserve unrelated files and all backend work.

The existing app only routes login, OAuth callback, skills registration and a small home. Its default mock API masks incompatible assumptions: auth returns only a token; registration needs displayName/timeZone; skill rows wrap a skill; replacement uses offeredSkillIds/wantedSkillIds; matches, search and exchanges are paginated; matches are flat summaries; profiles use skillsOffered/skillsWanted. The design export adds useful screens but contains inert request buttons, direct mock feedback imports, incorrect participant directions, invented timeline dates, missing pagination and browser-zone scheduling mislabeled as account-zone time. These must be corrected during integration.

## Owned scope

Own frontend/ and this plan, frontend-backend-adjustments.md, frontend-design-integration-report.md. Do not modify backend/, AGENTS.md, other agents' documents, or the external design source. Reuse design components under components/design/ to avoid case-insensitive collisions with existing shadcn ui/button.tsx and related files. Keep the original API boundary under src/lib/api and the session context under src/context.

## Screen and route inventory

| Design source | Real route | Integration contract |
|---|---|---|
| login.html / LoginPage | /login | POST auth/login, auth/register; GET auth/me; Google redirect |
| Existing OAuth callback | /auth/callback | Accept issued token, clear URL, restore authenticated state |
| skill-registration.html | /skills/register; /skills alias | GET/PUT me/skills; paginated skills vocabulary; suggest pending term |
| home-matches.html | /home; /matches alias | GET matches, profiles for skill details; POST exchanges with selected skill |
| search.html | /search | GET search?skill=UUID with strength and pagination |
| profile.html | /profile/:id, /profile/me | GET users/:id and feedback; supported request flow |
| invitations.html | /invitations | Paginated exchanges, sent/received presentation and legal actions |
| invitations-accept-partial.html | Dialog within invitations | Requester offered skill choice; POST accept with skillFromRequester |
| exchange-details.html | /exchanges/:id | GET exchange, actual state and dates, supported transitions |
| scheduling.html | /scheduled/:id; /scheduled list | ACCEPTED-only scheduling; ISO instant; meeting URL; participant zones |
| history.html | /history | COMPLETED exchanges only; real totals; feedback status from API |
| feedback.html | /feedback/:id | COMPLETED-only, immutable rating; no fixture imports |
| availability.html | /availability | GET/PUT windows; time-zone update depends on backend adjustment |
| states.html | Shared screen states | Loading, empty, error, blocked states, no separate demo route |
| index.html / brand-identity.html | Reference only | Interlock mark, serif headings, neutral surfaces, restrained indigo |

## Ordered implementation

1. Save this plan and immediately send cross-owner API dependencies to the coordinator.
2. Adapt and import visual components, page compositions and styles; self-host the specified fonts. Retain existing framework and utility tokens.
3. Define explicit wire DTO adapters at src/lib/api; make real API the default, with mock mode opt-in only. Preserve token storage and fix OAuth/registration state refresh.
4. Wire routes and shared layout, then skills and matches/search/profile request selection with pagination and visible failures.
5. Integrate invitations, acceptance for both strengths, scheduling, exchange details, completed history and feedback against actual endpoints. Do not fabricate blind feedback, audit dates or ratings.
6. Integrate availability range editing, overlap validation and backend time-zone contract when available. Continue independent screens while backend works.
7. Run TypeScript/Vite build, lint, meaningful transport/flow tests, responsive/browser checks. Coordinate frontend test ownership via maestro.
8. Re-read concurrent backend DTO changes, document actual results and remaining dependencies, notify coordinator.

## Compatibility and dependencies

All HTTP routes remain /api-prefixed. Map real DTOs rather than changing backend contracts silently. Preserve legacy /home and /skills/register URLs. Do not send bare accept payloads: current AcceptExchangeRequest requires skillFromRequester for MUTUAL too. Adapt paginated results using page/size/total without silently truncating. Profile fetches can resolve missing summaries with a per-load deduplicated resolver; backend enrichment is desirable but not a release blocker. See frontend-backend-adjustments.md for blocking features.

## Risks and acceptance criteria

Risks: concurrent backend DTO changes, OAuth callback ordering, duplicate invites, daylight-saving ambiguity, cross-zone weekly previews, mock-only prototype behavior, long content on narrow screens, inaccessible custom dialogs/autocomplete.

Acceptance: build and tests pass; lint has no errors; real wire DTOs covered by tests; no production imports of fictional rating records; protected routes and onboarding work; each displayed mutation submits the correct payload and reports failure; pagination reaches later results; no false historical dates or overlap claims; scheduling explicitly uses the selected account zone and rejects invalid/ambiguous local times; keyboard and mobile navigation work; no horizontal overflow at 320/375/768/1440 where browser tooling is available. Live backend E2E and fixture-backed browser verification must be reported separately.

## Baseline validation

- npm run build: passed, Vite 6.4.3, 1604 modules.
- npm test: exit 1: `No test files found, exiting with code 1`.
- npm run lint: exit 1, 52 errors and 1 warning, mainly browser globals reported by no-undef; also unused `_password` in mock client.
- git -C frontend status --short / git -C backend status --short: `fatal: not a git repository (or any of the parent directories): .git`.

Final validation will be recorded in frontend-design-integration-report.md.

## Implementation updates

- Coordinator selected F1 optional timeZone plus windows and F2 GET exchange feedback; backend released both for local integration after 203 passing tests. Frontend uses these real contracts and bilateral publication semantics. F3/F4 use explicit profile enrichment and paginated open-exchange lookup.
- Production runtime now always uses the real API. Legacy mock files remain isolated and unimported rather than exposing an incomplete mixed mock/live mode.
- Visual imports live under components/design to preserve existing shadcn components. Source source-serif-4/public-sans variable font packages do not expose latin.css; use their installed package entry points and locally emitted subsets.
- Tests owned by frontend: src/lib/api/realApiClient.test.ts, src/lib/scheduling.test.ts, src/lib/availability.test.ts and tests/design-integration.spec.ts, with tests/fixtures.ts. Browser scenarios provide flow coverage; no separate src/flows.test.tsx was created. Independent tester should not edit these files.
- Coordinator checkpoint: build passed, 23 Vitest tests passed, 13 Playwright fixture scenarios passed, lint zero errors and 9 Fast Refresh warnings. The already-running final browser suite completed 14 scenarios after adding search/profile request coverage. See frontend-design-integration-report.md for exact evidence and limits; live Spring validation remains with the tester.
- Preserve the stable implementation while awaiting coordinator-routed findings. The tester exclusively owns new frontend/tests/live/ and frontend/playwright.live.config.ts; do not edit these paths or repeat green checks without changes.
