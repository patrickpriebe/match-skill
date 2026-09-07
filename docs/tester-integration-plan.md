# Match Skill - Tester Integration & Independent Validation Plan

Date: 2026-09-05  
Author: Tester Agent  
Project Root: `C:\Projects\match-skill`  
Status: In Progress — Full Integrated Validation Phase Active (Frontend & Backend Stable Released)  

---

## 1. Objective & Boundaries

### 1.1 Objective
Establish the independent verification strategy for **Match Skill**, validating simultaneous implementations from backend and frontend agents against documented architecture, API contracts, domain lifecycle rules, and specific integration adjustments **F1** (atomic timezone and availability windows) and **F2** (server-enforced double-blind feedback, exact error contracts, and accepted publication policy).

Execute a real browser + real Spring Boot integration check over local HTTP without fixture interception, closing the concrete testing gap where backend tests used MockMvc and frontend browser tests used fixture route interception.

### 1.2 Evidence & Inputs
This plan incorporates specifications and decisions from:
- `AGENTS.md`: Harness constraints, language standards, directory ownership, and definition of done.
- `docs/coordination-plan.md`: Multi-agent boundaries, baseline records, F1/F2 assignments, accepted F2 publication policy decision, and the newly released "Frontend checkpoint and integrated validation release" section.
- `docs/backend-integration-contracts.md`: Authoritative wire contracts for F1 and F2, exact error codes (`409 EXCHANGE_NOT_COMPLETED`, `403 NOT_A_PARTICIPANT`), complete `FeedbackResponse` schema, and ranking/aggregate publication rules.
- `docs/backend-modernization-plan.md` & `docs/backend-modernization-report.md`: Transaction boundaries, DTO projection inside transactions, pessimistic row locking, database aggregation, rate limiting, and documented residual risks.
- `docs/frontend-design-integration-plan.md`: Open Design screen adaptation, wire DTO adapters (`src/lib/api/`), real API defaults, and responsive design specifications.
- `docs/frontend-backend-adjustments.md`: Screen-driven requirements for F1 and F2.
- `docs/documentation.md` & `docs/test-plan.md`: System business rules, state machines, and rating algorithms.

### 1.3 Scope & Ownership
- **Tester Ownership**: Owns `docs/tester-integration-plan.md` and `docs/tester-integration-report.md`.
- **Exclusively Reserved Paths for Tester**:
  - `backend/src/test/java/com/matchskill/backend/e2e/`
  - `backend/src/test/resources/e2e/`
  - `frontend/tests/live/`
  - `frontend/playwright.live.config.ts`
- **Restricted Directories**: Tester does **not** edit existing production files or owner tests in `backend/` or `frontend/`, `AGENTS.md`, or other agents' documents.
- **Active Validation Scope**:
  1. Real Browser + Real Spring Boot over local HTTP without fixture interception (`**/api/**`).
  2. Discardable H2 database; rate limiter storage mocked only in test-only harness if live Redis is unavailable; real JWT filter chain and auth tokens.
  3. Core journeys: Register $\to$ Login $\to$ Session reload $\to$ Skills setup $\to$ Matches $\to$ Exchange request $\to$ Accept $\to$ Schedule $\to$ Complete $\to$ Feedback.
  4. Invariant checks: F1 atomic zone & windows; F2 server blindness, profile received feedback directionality, aggregate publication on dual submission, and legacy unpaired review exclusion.
  5. Content clipping inspection across screen viewports (320px, 375px, 768px, 1440px).

---

## 2. Baseline Status & Environmental Constraints

### 2.1 Environmental Constraints
- **Docker Unavailable**: Docker daemon (`desktop-linux` context) is unavailable in both execution contexts. Testcontainers, live PostgreSQL, and live Redis daemons cannot be launched.
- **Testing Approach**: Automated verification relies on:
  - Backend: JUnit 5, Mockito, Spring `@WebMvcTest` slice tests with mocked services, and lightweight H2 / in-memory repositories for transactional persistence and query projections.
  - Frontend: TypeScript compilation (`npm run build`), ESLint validation, and Vitest / React Testing Library component and adapter tests.
- **Concurrent Modification Shield**: Backend and frontend agents are editing code concurrently. Transient compile or test failures on files actively being edited must **not** be treated as confirmed regressions. Evaluations must occur against stable commits/checkpoints.

### 2.2 Baseline Evidence
- **Frontend Baseline**:
  - `npm run build`: Success (Vite 6.4.3, 1604 modules compiled).
  - `npm test`: Exit code 1 (`No test files found, exiting with code 1`).
  - `npm run lint`: Exit code 1 (52 errors, 1 warning, predominantly browser globals `window`/`document` and unused variables in mock stubs).
- **Backend Baseline**:
  - `.\mvnw.cmd -B test`: 140 tests run. 1 failure, 7 errors in `RateLimiterServiceTest` and `RateLimitInterceptorTest` due to mock drift from Redis Lua script migration and offline Redis fail-closed behavior.

---

## 3. Contract & Regression Verification Matrix

### 3.1 Authentication & Profile Contract
| Endpoint | Method | Key Request Fields | Key Response Fields | Error Codes | Verification Focus |
|---|---|---|---|---|---|
| `/api/auth/register` | `POST` | `email`, `password`, `displayName`, `timeZone` | `token` | `400 VALIDATION_ERROR`, `409 EMAIL_ALREADY_EXISTS` | Validate email regex, password constraints, IANA timeZone validation via `@ValidTimeZone`. Verify response is token-only. |
| `/api/auth/login` | `POST` | `email`, `password` | `token` | `401 BAD_CREDENTIALS` | Verify credentials check; read-only transaction flag; bearer token generation. |
| `/api/auth/me` | `GET` | Header: `Authorization: Bearer <token>` | `id`, `email`, `displayName`, `bio`, `timeZone`, `skillsRegistered` | `401 UNAUTHORIZED` | Verify token parsing, claims extraction, return of current profile state and timezone. |
| `/api/users/{id}` | `GET` | Header: `Authorization: Bearer <token>` | `id`, `displayName`, `bio`, `timeZone`, `skillsOffered`, `skillsWanted` | `404 USER_NOT_FOUND` | Public profile retrieval; verify correct mapping of user skills and timezone. |

### 3.2 Skills Vocabulary, Registration & Pagination
| Endpoint | Method | Key Request Fields | Key Response Fields | Error Codes | Verification Focus |
|---|---|---|---|---|---|
| `/api/skills` | `GET` | `q` (string), `page` (int), `size` (int) | `PageResponse<SkillResponse>` (`items`, `page`, `size`, `total`) | None | Verify only `APPROVED` skills are returned. Case-insensitive prefix search. |
| `/api/skills/suggest` | `POST` | `name` (max 100 chars) | `SkillResponse` (`id`, `name`, `status=PENDING_REVIEW`) | `400 VALIDATION_ERROR` | Verify suggested skill starts as `PENDING_REVIEW`. Oversized name (>100) rejected with 400. |
| `/api/me/skills` | `GET` | Header: `Authorization: Bearer <token>` | `MySkillsResponse` (`offered: UserSkillResponse[]`, `wanted: UserSkillResponse[]`) | `401 UNAUTHORIZED` | Verify separation of offered and wanted skills. |
| `/api/me/skills` | `PUT` | `offeredSkillIds: UUID[]`, `wantedSkillIds: UUID[]` | `MySkillsResponse` | `400 SKILL_NOT_APPROVED`, `400 SKILL_NOT_FOUND` | **P0 Regression**: If any skill id in `offeredSkillIds` or `wantedSkillIds` has status `PENDING_REVIEW`, reject with 400 `SKILL_NOT_APPROVED`. Atomically replace set and mark `skillsRegistered = true`. |
| List Endpoints | `GET` | `page`, `size` | `PageResponse<T>` | `400 VALIDATION_ERROR` (if negative) | **P1 Regression**: Unbounded `size` clamped to `Math.min(size, 100)` (default 20). Negative `page` (<0) rejected or clamped. Offset calculation immune to integer overflow. |

### 3.3 Matching Engine & Ordering Rules
| Test Scenario | Input Data & Setup | Expected Output & Ordering | Acceptance Criteria |
|---|---|---|---|
| **Strength: `MUTUAL` vs `PARTIAL`** | User A offers Java, wants React. User B offers React, wants Java (`MUTUAL`). User C offers React, wants Python (`PARTIAL`). | User B classified as `MUTUAL`; User C classified as `PARTIAL`. | Complementary skills in both directions return `MUTUAL`; one direction only returns `PARTIAL`. |
| **Strict Ranking Precedence** | Candidates: 1) `PARTIAL`, rating 5.0 (20 reviews), overlap 10h. 2) `MUTUAL`, rating 4.2 (10 reviews), overlap 2h. 3) `MUTUAL`, rating 4.9 (15 reviews), overlap 1h. 4) `MUTUAL`, rating 4.9 (15 reviews), overlap 5h. | Order: Candidate 4 $\to$ Candidate 3 $\to$ Candidate 2 $\to$ Candidate 1. | 1. `MUTUAL` strictly precedes `PARTIAL`. 2. Reputation average descending. 3. Rating count descending. 4. Overlap hours descending. 5. Stable user ID tie-break. |
| **Zero-Overlap Exclusion** | Viewer and candidate both have recorded availability windows, but overlap is 0 minutes. | Candidate excluded from `/api/matches`. | Candidates with 0 overlap are dropped if and only if both parties have configured availability. If either party has no availability, candidate is retained with overlap=0. |
| **Deliberate Skill Search** | `GET /api/search?skill={reactId}` | Returns all candidates offering React, ordered by standard ranking hierarchy. | Overlap tie-break preserved; candidate strength classified relative to viewer's wanted/offered lists. |
| **Self-Match Filter** | Current user offers skills that match own wanted skills. | Current user never appears in own matches or search results. | Verify `id != viewerId` predicate. |

### 3.4 Exchange State Machine & Concurrency Control
| Action Endpoint | Allowed Caller | Initial State | Next State | Error Cases & Codes | Concurrency / Locking Assertion |
|---|---|---|---|---|---|
| `POST /api/exchanges` | Requester | None | `REQUESTED` | `400` invalid skill, `409` duplicate active exchange | Requester creates exchange specifying `receiverId` and `skillFromReceiverId`. |
| `POST /api/exchanges/{id}/accept` | Receiver only | `REQUESTED` | `ACCEPTED` | `403 NOT_A_PARTICIPANT` (or `403 FORBIDDEN`), `400` missing `skillFromRequester`, `409 INVALID_STATE_TRANSITION` | Requires `skillFromRequester` (for both `PARTIAL` and `MUTUAL`). Row locked via `PessimisticLock` to prevent concurrent accept/decline race. |
| `POST /api/exchanges/{id}/decline` | Receiver only | `REQUESTED` | `DECLINED` | `403 NOT_A_PARTICIPANT`, `409 INVALID_STATE_TRANSITION` | Terminal state. No further transitions permitted. |
| `POST /api/exchanges/{id}/schedule` | Either participant | `ACCEPTED` or `SCHEDULED` | `SCHEDULED` | `403 NOT_A_PARTICIPANT`, `400 INVALID_MEETING_URL`, `400 INVALID_SCHEDULED_TIME`, `409 INVALID_STATE_TRANSITION` | Validates `scheduledAt` is in the future. Validates `meetingUrl` against allowlist (`zoom.us`, `meet.google.com`, `teams.microsoft.com`, `whereby.com`) and requires HTTPS. Supports rescheduling while in `SCHEDULED`. |
| `POST /api/exchanges/{id}/complete` | Either participant | `SCHEDULED` | `COMPLETED` | `403 NOT_A_PARTICIPANT`, `409 INVALID_STATE_TRANSITION` | Terminal state. Enables feedback submission window. |
| `POST /api/exchanges/{id}/cancel` | Either participant | `ACCEPTED` or `SCHEDULED` | `CANCELLED` | `403 NOT_A_PARTICIPANT`, `409 INVALID_STATE_TRANSITION` | Terminal state. Cannot cancel `COMPLETED` or `DECLINED` exchanges. |
| `POST /api/exchanges/{id}/feedback` | Either participant | `COMPLETED` | Unchanged (`COMPLETED`) | `403 NOT_A_PARTICIPANT`, `409 FEEDBACK_ALREADY_SUBMITTED`, `409 EXCHANGE_NOT_COMPLETED` | One submission per participant. Second submission returns `409 FEEDBACK_ALREADY_SUBMITTED`. |

---

## 4. Specific Integration Adjustments: F1 & F2 Specifications

### 4.1 F1: Atomic Account TimeZone & Availability Windows
- **Endpoint**: `PUT /api/me/availability`
- **Request Payload**:
  ```json
  {
    "timeZone": "America/New_York",
    "windows": [
      { "dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "12:00" },
      { "dayOfWeek": "WEDNESDAY", "startTime": "14:00", "endTime": "17:00" }
    ]
  }
  ```
- **Response**: `AvailabilityWindowResponse[]` (preserves backward-compatible array response, including `id`, `dayOfWeek`, `startTime`, `endTime`).
- **Core Rules & Verification Scenarios**:
  1. **Atomic Update**: `User.timeZone` and the user's `availability` window records must be validated and persisted within the same database transaction.
  2. **Validation Failure Atomicity**: If `timeZone` is invalid (blank, unsupported IANA zone id, or malformed) or any window violates business rules (`startTime >= endTime` or overlapping windows on the same day):
     - API returns `400 BAD_REQUEST` with the `{code, message}` envelope (`VALIDATION_ERROR` or `OVERLAPPING_AVAILABILITY_WINDOW`).
     - **Database Rollback**: Neither the timezone nor the availability windows are modified; both retain previous values.
  3. **Local Clock Time Preservation**: Availability windows represent recurring local clock intervals. Modifying the timezone does not shift or alter the saved local clock hours of the windows.
  4. **Optional TimeZone Omission**: If `timeZone` is omitted from the request body or passed as `null`, the existing `User.timeZone` remains unchanged, and windows are replaced normally.
  5. **State Propagation**: After a successful update with `timeZone: "America/New_York"`:
     - Immediate call to `GET /api/auth/me` returns `"timeZone": "America/New_York"`.
     - Immediate call to `GET /api/users/{myId}` returns `"timeZone": "America/New_York"`.

---

### 4.2 F2: Participant Feedback State & Accepted Publication Policy

#### 4.2.1 Wire Contract & Error Definitions
- **Endpoint**: `GET /api/exchanges/{id}/feedback`
- **Authentication**: Requires Bearer authentication.
- **Participant Access**: Caller must be a participant of the exchange (`requesterId` or `receiverId`). An unrelated third party receives **`403 NOT_A_PARTICIPANT`**.
- **Exchange Existence**: If the exchange id does not exist, returns **`404 EXCHANGE_NOT_FOUND`**.
- **Lifecycle Guard**: Exchange status must be `COMPLETED`. Any other lifecycle state (`REQUESTED`, `ACCEPTED`, `SCHEDULED`, `DECLINED`, `CANCELLED`) strictly returns **`409 EXCHANGE_NOT_COMPLETED`** (do not use 400 or 409 indistinctly).
- **Feedback Submission Contract**: `POST /api/exchanges/{id}/feedback` accepts `{ "rating": 1..5, "comment": "string" }`. Duplicate submission (sequential or race) strictly returns **`409 FEEDBACK_ALREADY_SUBMITTED`**.

#### 4.2.2 Complete FeedbackResponse Schema
Every feedback item (`mine` or `theirs`) adheres to the complete schema:
```json
{
  "id": "uuid",
  "exchangeId": "uuid",
  "authorId": "uuid",
  "rating": 5,
  "comment": "Thorough and insightful explanation of React hooks.",
  "createdAt": "2026-09-05T18:00:00Z"
}
```

#### 4.2.3 Response Envelope & Visibility State Machine
Response structure:
```json
{
  "mine": FeedbackResponse | null,
  "theirs": FeedbackResponse | null,
  "counterpartSubmitted": boolean
}
```

| Phase / Condition | Viewer Submitted? | Counterpart Submitted? | `mine` Field | `theirs` Field | `counterpartSubmitted` |
|---|---|---|---|---|---|
| **Phase 0** (Neither submitted) | No | No | `null` | `null` | `false` |
| **Phase 1A** (Viewer submitted, counterpart pending) | Yes | No | Viewer's `FeedbackResponse` | `null` | `false` |
| **Phase 1B** (Counterpart submitted, viewer pending) | No | Yes | `null` | **`null` (BLINDED)** | **`true`** |
| **Phase 2** (Both submitted) | Yes | Yes | Viewer's `FeedbackResponse` | Counterpart's `FeedbackResponse` | `true` |

- **Phase 1B Invariant**: When the counterpart has submitted but the viewer has not, `theirs` MUST be `null`. The payload strictly hides the counterpart's `id`, `rating`, `comment`, and `createdAt`. The existence of counterpart feedback is revealed **only** through the boolean flag `counterpartSubmitted: true`.

---

#### 4.2.4 Coordinated Publication Policy & Anti-Leakage Rules

The policy agreed upon in `docs/coordination-plan.md` and `docs/backend-integration-contracts.md` dictates:

1. **Unilateral Review Privacy**:
   - A single submitted review remains private to its author.
   - It is never returned to the recipient or any third party.
2. **Dual-Submission Release Only**:
   - Both reviews publish together **only when both participants have submitted** on the `COMPLETED` exchange.
   - There is **no timeout-based unilateral release**. Unpaired reviews remain private indefinitely until the counterpart submits.
3. **Legacy / Historical Reviews**:
   - Existing historical one-sided reviews remain stored in the database but are excluded from public visibility and reputation/ranking calculations until paired.
4. **Profile Feedback Semantics (`GET /api/users/{id}/feedback`)**:
   - Returns an array `FeedbackResponse[]`.
   - **Directionality Rule**: Feedback displayed on `/api/users/{id}/feedback` represents reviews **RECEIVED** by user `{id}` (i.e. where `{id}` was a participant in an exchange and the author was the *other* participant).
   - **Content Returned**:
     1. Published reviews received by `{id}` (where both participants have submitted).
     2. Plus, if the authenticated caller has authored a private review for user `{id}`, that specific review is visible to its author.
     3. It **never** returns another person's unpublished review.
   - **Mandatory Bidirectional Testing**: Because feedback is received by the profile owner, tests must check the profiles of **both participants**:
     - When User A (requester) rates User B (receiver): User B is the recipient. User B's profile (`GET /api/users/{userB}/feedback`) must hide User A's review from User B and third parties until User B submits. (Checking only User A's profile would test the wrong endpoint).
     - When User B rates User A: User A is the recipient. Once User B submits, User A's profile shows User B's review to everyone, and User B's profile shows User A's review to everyone.
5. **Reputation Aggregates & Ranking (Elimination of Deduction Attack)**:
   - Profile `reputationAverage`, `reputationCount`, and the ranking scores in `GET /api/matches` and `GET /api/search`:
   - Computed **ONLY** from completed exchanges with **both** participant submissions.
   - A private, unilateral review alters neither `reputationAverage` nor `reputationCount`.
   - Consequently, mathematical deduction (subtracting aggregate delta before and after exchange completion) is completely impossible because the public aggregate remains invariant until both parties submit.
   - Atomic commit: upon the second participant's submission commit, both reviews become published, simultaneously updating both users' public profile reviews and reputation aggregates.

---

#### 4.2.5 F2 Test Verification Matrix
| Case ID | Viewer Role | Exchange State | Action / Query | Expected Status & Body |
|---|---|---|---|---|
| `F2-01` | Requester | `COMPLETED`, neither submitted | `GET /api/exchanges/{id}/feedback` | `200 OK`: `mine: null, theirs: null, counterpartSubmitted: false` |
| `F2-02` | Requester | `COMPLETED`, requester submitted only | `GET /api/exchanges/{id}/feedback` | `200 OK`: `mine: {rating, comment, ...}, theirs: null, counterpartSubmitted: false` |
| `F2-03` | Receiver | `COMPLETED`, requester submitted only | `GET /api/exchanges/{id}/feedback` | `200 OK`: `mine: null, theirs: null, counterpartSubmitted: true` (strictly blinded!) |
| `F2-04` | Receiver | `COMPLETED`, requester submitted only | `GET /api/users/{receiverId}/feedback` | `200 OK`: empty list (requester's review not leaked to recipient before recipient rates) |
| `F2-05` | Third Party | `COMPLETED`, requester submitted only | `GET /api/users/{receiverId}/feedback` | `200 OK`: empty list (unpaired review excluded from third-party read) |
| `F2-06` | Any | `COMPLETED`, requester submitted only | Check Receiver `reputationAverage` / `reputationCount` | Aggregates unchanged; zero reputation delta |
| `F2-07` | Receiver | `COMPLETED`, both submitted | `POST /api/exchanges/{id}/feedback` | `200 OK`: Receiver review created; dual publication triggered |
| `F2-08` | Requester | `COMPLETED`, both submitted | `GET /api/exchanges/{id}/feedback` | `200 OK`: `mine` (requester review), `theirs` (receiver review), `counterpartSubmitted: true` |
| `F2-09` | Receiver | `COMPLETED`, both submitted | `GET /api/exchanges/{id}/feedback` | `200 OK`: `mine` (receiver review), `theirs` (requester review), `counterpartSubmitted: true` |
| `F2-10` | Third Party | `COMPLETED`, both submitted | `GET /api/users/{receiverId}/feedback` | `200 OK`: contains requester's review (received by receiver) |
| `F2-11` | Third Party | `COMPLETED`, both submitted | `GET /api/users/{requesterId}/feedback` | `200 OK`: contains receiver's review (received by requester) |
| `F2-12` | Either | `COMPLETED`, already submitted | `POST /api/exchanges/{id}/feedback` | `409 FEEDBACK_ALREADY_SUBMITTED` |
| `F2-13` | Third Party | `COMPLETED` | `GET /api/exchanges/{id}/feedback` | **`403 NOT_A_PARTICIPANT`** |
| `F2-14` | Participant | `SCHEDULED` (or any non-completed) | `GET /api/exchanges/{id}/feedback` | **`409 EXCHANGE_NOT_COMPLETED`** |
| `F2-15` | Participant | Non-existent UUID | `GET /api/exchanges/{id}/feedback` | **`404 EXCHANGE_NOT_FOUND`** |

---

## 5. Frontend UI/UX, Flow & Responsive Validation

### 5.1 Screen Inventory & User Journey
| Route | Component / Page | Integrated Screen Requirements | Validation Checks |
|---|---|---|---|
| `/login` | `LoginPage` | Tabs for Login and Register. Register includes `displayName` and IANA `timeZone` selection. | Successful login stores token and redirects to `/home`. Invalid credentials display error alert. |
| `/skills/register` | `SkillRegistrationPage` | Autocomplete search for approved skills; chips for Offered/Wanted; suggest term dialog. | Submitting sets `skillsRegistered=true` and redirects. Suggest modal submits to `/api/skills/suggest`. |
| `/home` | `HomePage` | Match cards showing candidate name, avatar, bio, match strength badge (`MUTUAL` / `PARTIAL`), reputation, and skills. | Request Exchange button opens dialog to select offering skill. Empty state displayed when 0 matches. |
| `/search` | `SearchPage` | Search by specific skill id. Renders candidate cards with strength and pagination controls. | Paginating reaches next page. Changing skill query resets to page 0. |
| `/profile/:id` | `ProfilePage` | Header with user info, timezone, skills offered/wanted, feedback list and reputation display. | Displays published received feedback. Feedback count matches published aggregate, not local array length. |
| `/invitations` | `InvitationsPage` | Tabs for Received and Sent invitations. Actions: Accept, Decline. Accept Partial opens dialog to pick requester skill. | Accepting moves item from invitations to `/scheduled` or exchange details. Declining marks item as declined. |
| `/exchanges/:id` | `ExchangeDetailsPage` | Timeline/stepper of exchange lifecycle: `REQUESTED` $\to$ `ACCEPTED` $\to$ `SCHEDULED` $\to$ `COMPLETED`. | Actions correspond strictly to current status and participant role. Rescheduling supported. |
| `/scheduled/:id` | `SchedulingPage` | Schedule form: date/time picker with timezone indicator; meeting URL input with allowlist helper. | Rejects past dates, invalid URLs (non-https or unauthorized domains). Successful submit transitions to `SCHEDULED`. |
| `/history` | `HistoryPage` | List of `COMPLETED` exchanges. Displays real dates and feedback submission CTA button. | Shows "Leave Feedback" if viewer has not submitted; shows "Feedback Submitted" or rating once completed. |
| `/feedback/:id` | `FeedbackPage` | 1-5 star rating selector, comment textarea, submit button. | Submitting sends `POST /api/exchanges/{id}/feedback`. Form locks upon submission; prevents duplicate submission. |
| `/availability` | `AvailabilityPage` | Weekly schedule editor (Monday-Sunday time slots). Timezone selector (F1 integration). | Validates `startTime < endTime` and blocks overlapping slots on the same day before submitting. |

### 5.2 Responsive & Visual Verification Invariants
- **Breakpoints**: 320px (iPhone SE), 375px (mobile standard), 768px (tablet portrait), 1440px (desktop wide).
- **Strict Content Clipping Inspection ("Conteúdo Cortado")**:
  - Verification must actively inspect for **cropped or clipped content**, truncated text labels, squashed badges, clipped modal headers/actions, or overflowing tables.
  - Applying CSS `overflow-x: hidden` to conceal layout overflowing is **not** acceptable as a pass criterion; layout elements must wrap, flex, or adjust fluidly.
  - Typography, buttons, and badges must remain completely visible, readable, and touch-target accessible ($\ge 44 \times 44\text{px}$) across all viewports.
  - Dialog bodies on mobile must allow vertical scrolling without cutting off action buttons (Submit/Cancel).

---

## 6. Execution Commands & Test Tooling

### 6.1 Backend Verification Commands
Executed in `C:\Projects\match-skill\backend`:
```powershell
# 1. Core unit and business logic tests
.\mvnw.cmd test -Dtest="SlugsTest,AvailabilityOverlapTest,MeetingUrlValidatorTest,ReputationServiceTest,SkillServiceTest,UserSkillServiceTest,AvailabilityServiceTest,MatchServiceTest,ExchangeServiceTest"

# 2. Regression tests (GlobalExceptionHandler, ValidTimeZone, RateLimiting)
.\mvnw.cmd test -Dtest="GlobalExceptionHandlerTest,ValidTimeZoneValidatorTest,RateLimiterServiceTest,RateLimitInterceptorTest"

# 3. Owner F1 and F2 test suites (as delivered by backend agent)
.\mvnw.cmd test -Dtest="*Availability*,*Feedback*"

# 4. Full Maven test pass
.\mvnw.cmd clean test-compile test
```

### 6.2 Frontend Verification Commands
Executed in `C:\Projects\match-skill\frontend`:
```powershell
# 1. Typecheck and bundle verification
npm run build

# 2. Lint verification
npm run lint

# 3. Unit and adapter test execution
npm test -- --run
```

---

## 7. Definition of Done & Acceptance Criteria

An integrated phase is considered **DONE** only when all the following criteria are satisfied:
1. **Zero Failing Tests**: Both backend (`.\mvnw.cmd test`) and frontend test suites pass with 0 errors and 0 failures.
2. **Exact Contract & Error Code Compliance**:
   - `PUT /api/me/availability` atomically validates and saves `timeZone` and `windows`; rolls back completely on validation error.
   - `GET /api/exchanges/{id}/feedback` returns `mine`, `theirs`, and `counterpartSubmitted`; enforces `403 NOT_A_PARTICIPANT`, `404 EXCHANGE_NOT_FOUND`, and `409 EXCHANGE_NOT_COMPLETED`.
   - `POST /api/exchanges/{id}/feedback` returns `409 FEEDBACK_ALREADY_SUBMITTED` on duplicates.
   - `FeedbackResponse` contains `id`, `exchangeId`, `authorId`, `rating`, `comment`, `createdAt`.
   - Double-blind publication policy verified: unilateral review is private; public profile feedback and reputation aggregates update ONLY after both sides evaluate; bidirectional profile endpoints verified.
   - `PENDING_REVIEW` skills cannot be registered or matched.
   - Pagination clamp `size <= 100` enforced.
3. **Frontend Visual Quality**:
   - `npm run build` succeeds cleanly.
   - ESLint runs with 0 errors.
   - Responsive design verified at 320px, 375px, 768px, and 1440px with no clipped content or concealed overflow.
4. **Transparent Reporting**: Actual build outputs, test counts, and execution logs documented in `docs/tester-integration-report.md`. Any transient environmental limitation (e.g. missing Docker) explicitly stated without masking.
