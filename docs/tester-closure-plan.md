# Match Skill - Tester Closure Plan

**Date**: 2026-09-07  
**Owner**: Tester Agent  
**Status**: Completed  
**Project Root**: `C:\Projects\match-skill`  
**Live Validation Infrastructure**:
- PostgreSQL 15.19: `127.0.0.1:15432`, DB `matchskill_validation`, user `matchskill_validation`
- Redis 7.4.11: `127.0.0.1:16379` (no password)
- Test Server: Spring Boot on port `18089`, context path `/api`
- Test Frontend: Vite Dev Server on port `4176`, proxying `/api` to `http://127.0.0.1:18089`
**Reference Coordination Plan**: [`docs/pending-work-closure-plan.md`](pending-work-closure-plan.md)  
**Report Output Target**: [`docs/tester-closure-report.md`](tester-closure-report.md)  

---

## 1. Objective & Baseline Evidence

### 1.1 Objective
Execute independent, authoritative end-to-end and integration validation to close all pending work items authorized by the user:
1. **Real Infrastructure Verification**: Replace the previous test harness limitation (in-memory H2 database and MockBean rate limiter) with isolated, live **PostgreSQL** and live **Redis** (running actual Lua rate-limiting scripts), with real Spring Security JWT filters, real HTTP sockets, and headless browser tests.
2. **Backend Pending Fixes Verification**:
   - **Slugs & Vocabulary Identity**: Verify separate identity for `C`, `C++`, `C#`, rejection of punctuation-only suggestions, and case/accent normalization without breaking existing approved skills.
   - **Meeting URL 2048**: Verify persistence and validation of meeting URLs $> 255$ characters (up to 2048 characters) for Microsoft Teams / Zoom links, ensuring proper database schema alignment and validation rejection beyond 2048 characters.
3. **Frontend Fixes & Visual Defect Reproduction**:
   - Reproduce and verify visual issues using real browser viewports (320px, 375px, 768px, 1440px), scrolling, and hit-testing (distinguishing real interaction/occlusion bugs from full-page screenshot stitching artifacts).
   - Verify mobile bottom navigation tabbar clearance, `.reg-foot` onboarding clearance, and tab label/count spacing (`Received` vs count).
   - Check Fast Refresh clean build (0 warnings/errors) and dependency audit status.
4. **Core Invariant Regression**:
   - **F1**: Atomic account timezone and availability windows under real PostgreSQL pessimistic locking and transactional rollback.
   - **F2**: Double-blind feedback state machine, privacy, bilateral profile feedback, and reputation aggregation under real PostgreSQL.
   - **Redis Rate Limiting**: Token bucket enforcement, HTTP 429 `TOO_MANY_REQUESTS`, `Retry-After` header verification, and fail-closed handling on Redis outage.

### 1.2 Baseline Evidence & Limitations Acknowledgment
- **Previous Checkpoint Baseline**:
  - Backend: 203 unit/slice tests passed in Surefire (`0 failures, 0 errors`).
  - Backend Live Socket: 1 test in [`backend/src/test/java/com/matchskill/backend/e2e/BackendLiveHttpTest.java`](../backend/src/test/java/com/matchskill/backend/e2e/BackendLiveHttpTest.java) passed over TCP socket.
  - Frontend Vitest: 23 unit/adapter tests passed.
  - Frontend Playwright: 14 fixture-based mock tests passed; 2 live browser tests passed in [`frontend/tests/live/live-browser.spec.ts`](../frontend/tests/live/live-browser.spec.ts).
- **Explicit Scope Limitation of Prior Baseline**:
  - The previous live execution was executed against **ephemeral H2** and a **mocked RateLimiterService** (`Mockito.mock(RateLimiterService.class)` in [`backend/src/test/java/com/matchskill/backend/e2e/LiveServerRunner.java`](../backend/src/test/java/com/matchskill/backend/e2e/LiveServerRunner.java)).
  - It did **NOT** prove PostgreSQL dialect compatibility, real pessimistic row locking semantics on Postgres, or Redis Lua token bucket atomicity.
  - This closure plan formally upgrades the validation target to live PostgreSQL and Redis services without compromising existing H2 fallbacks.

---

## 2. Directory Ownership & Boundaries

In strict compliance with [`AGENTS.md`](../AGENTS.md) and coordinator instructions:
- **Exclusively Owned Tester Paths**:
  - `docs/tester-closure-plan.md` (this plan)
  - `docs/tester-closure-report.md` (final execution report)
  - `backend/src/test/java/com/matchskill/backend/e2e/` (dedicated live socket test classes)
  - `backend/src/test/resources/e2e/` (test-only configurations and properties)
  - `frontend/tests/live/` (Playwright live browser specs)
  - `frontend/playwright.live.config.ts` (Playwright live configuration)
- **Protected Paths (Zero Edits by Tester)**:
  - `backend/src/main/` (owned by Backend Agent)
  - `backend/src/test/java/com/matchskill/backend/` outside `e2e/` (owned by Backend Agent)
  - `frontend/src/` (owned by Frontend Agent)
  - `frontend/package.json` (owned by Frontend Agent)
  - `AGENTS.md` and coordination documents (owned by Coordinator)
- **Production Safeguards**:
  - No connections to production Supabase/Render/Vercel.
  - Never alter production authentication rules or disable rate limiting in production code.
  - All tests use disposable data in isolated containers on dedicated local ports.

---

## 3. Test Scenarios & Verification Strategy

### 3.1 PostgreSQL & Redis Live Backend Integration
- **Harness Component**: [`backend/src/test/java/com/matchskill/backend/e2e/LivePostgresRedisServerRunner.java`](../backend/src/test/java/com/matchskill/backend/e2e/LivePostgresRedisServerRunner.java) and [`backend/src/test/java/com/matchskill/backend/e2e/PostgresRedisLiveIT.java`](../backend/src/test/java/com/matchskill/backend/e2e/PostgresRedisLiveIT.java):
  - Spring Boot test application configured with:
    - `spring.datasource.url`: `jdbc:postgresql://127.0.0.1:<PORT>/<DB>`
    - `spring.data.redis.host`: `127.0.0.1`, `spring.data.redis.port`: `<REDIS_PORT>`
    - `spring.jpa.hibernate.ddl-auto`: `validate` or `update` (testing additive migration)
    - Real `RateLimiterService` bean backed by real `StringRedisTemplate` executing `rate_limit.lua`.
- **Target Invariants**:
  1. **PostgreSQL Compatibility**: UUID mapping, JSON/Enum conversion, pessimistic locks (`PESSIMISTIC_WRITE` on `users` and `exchanges`), and sequence generation function cleanly without H2-specific idiosyncrasies.
  2. **Redis Rate Limiter Live Execution**:
     - Multiple rapid requests to `/api/auth/login` consume real tokens in Redis.
     - Upon exceeding threshold $N$, backend returns HTTP `429 TOO_MANY_REQUESTS` with standard `{ "code": "RATE_LIMITED", "message": ... }` and `Retry-After` header.
     - Fail-closed verification: when Redis is unreachable or returns error, rate limiter fails safely according to backend design.
  3. **F1 Atomic Timezone & Availability**:
     - Concurrent or transactional writes to `PUT /api/me/availability` acquire row lock on `users`.
     - An invalid timezone rolls back availability window deletions cleanly.
  4. **F2 Double-Blind Feedback & Dual Publication**:
     - Real PostgreSQL tables `feedbacks` and `exchanges`.
     - Requester feedback remains hidden from receiver and public profile queries until receiver also submits.
     - Dual submission atomically publishes both reviews and updates `users` reputation cache.
  5. **Meeting URL 2048 Characters**:
     - Save and retrieve meeting URLs with lengths 256–2048 characters (e.g. realistic Microsoft Teams URLs with extensive query parameters).
     - Verify database does not throw truncation error.
     - Verify strings $> 2048$ characters are rejected with `400 BAD_REQUEST`.
  6. **Slugs & Vocabulary**:
     - Slugs for `C`, `C++`, and `C#` are generated with distinct, non-colliding identifiers.
     - Suggestions with punctuation-only strings (e.g. `###`, `+++`) are rejected with `400 BAD_REQUEST`.

---

## 4. Implementation Steps & Sequence

1. **Step 1: Document Tester Closure Plan** (This document saved in `docs/tester-closure-plan.md`).
2. **Step 2: Await Coordinator Service Provisioning**:
   - Coordinator provisions isolated Docker containers for PostgreSQL and Redis and provides local ports/credentials.
3. **Step 3: Prepare Test Harness in Reserved Paths**:
   - Create [`backend/src/test/java/com/matchskill/backend/e2e/LivePostgresRedisServerRunner.java`](../backend/src/test/java/com/matchskill/backend/e2e/LivePostgresRedisServerRunner.java) and [`backend/src/test/java/com/matchskill/backend/e2e/PostgresRedisLiveIT.java`](../backend/src/test/java/com/matchskill/backend/e2e/PostgresRedisLiveIT.java).
   - Ensure existing H2-based [`backend/src/test/java/com/matchskill/backend/e2e/BackendLiveHttpTest.java`](../backend/src/test/java/com/matchskill/backend/e2e/BackendLiveHttpTest.java) and [`backend/src/test/java/com/matchskill/backend/e2e/LiveServerRunner.java`](../backend/src/test/java/com/matchskill/backend/e2e/LiveServerRunner.java) remain functional as fallback.
   - Update [`frontend/playwright.live.config.ts`](../frontend/playwright.live.config.ts) and add test cases in [`frontend/tests/live/`](../frontend/tests/live/).
4. **Step 4: Independent Execution When Owners Release Fixes**:
   - Execute backend test suite against PostgreSQL + Redis.
   - Execute live browser suite via Playwright with real PostgreSQL + Redis backend.
   - Execute responsive viewport hit-testing and inspect screenshots.
5. **Step 5: Synthesize and Publish Closure Report**:
   - Save full results, exact commands, test counts, timings, and findings in [`docs/tester-closure-report.md`](tester-closure-report.md).
   - Notify coordinator via Orca terminal command.

---

## 5. Acceptance Criteria (Definition of Done)

The tester closure verification is complete when:
1. **Real Postgres & Redis Passes**:
   - Spring Boot boots with real PostgreSQL and real Redis.
   - Real Redis Lua rate limiter enforces limits, returns HTTP 429 and `Retry-After`.
   - Long meeting URLs ($> 255$ chars up to 2048 chars) persist cleanly in PostgreSQL.
   - Distinct slug generation for `C`, `C++`, `C#` verified; punctuation-only suggestions rejected.
   - F1 atomic rollback and F2 double-blind feedback verified against live PostgreSQL.
2. **Live Browser Passes**:
   - Playwright live suite passes 100% against real Spring Boot + PostgreSQL + Redis (zero route mocks).
3. **Visual & Responsive Quality Verified**:
   - Viewport-based interactive hit-testing passes for mobile viewports ($320\text{px}, 375\text{px}$).
   - Occlusion / clipping defects re-tested and results honestly categorized.
4. **Transparent Documentation**:
   - All execution commands, stdout/stderr summaries, test counts, and operational limitations recorded in [`docs/tester-closure-report.md`](tester-closure-report.md).
   - No edits outside tester-reserved directories.
