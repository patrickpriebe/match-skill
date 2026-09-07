# Match Skill - Independent Tester Closure Report

**Date**: 2026-09-07  
**Author**: Tester Agent  
**Status**: Completed  
**Project Root**: `C:\Projects\match-skill`  
**Reference Plan**: [`docs/tester-closure-plan.md`](tester-closure-plan.md)  
**Coordination Plan**: [`docs/pending-work-closure-plan.md`](pending-work-closure-plan.md)  

---

## 1. Executive Summary & Verdict

The independent test harness has been completely executed against **real, isolated PostgreSQL 15.19 and Redis 7.4.11 services** provisioned via Docker (`docker-compose.validation.yml`). Previous harness limitations (in-memory H2 database, mocked `RateLimiterService`) were verified with real persistence, a real Redis fixed-window Lua rate limiter (`INCR` + `EXPIRE`), real Spring Security filters, and headless browser sessions over HTTP sockets.

Validation exercised specific, sampled concurrency and boundary scenarios. Multi-query reads with differing concurrent snapshots, in-memory candidate ranking, and fixed-week DST approximations remain documented architectural constraints, rather than solved global invariants.

### Summary Verdict Table

| Validation Surface | Execution Target | Test Suite | Result | Highlights |
|---|---|---|---|---|
| **Backend Owner Full Clean Verify** | Official Maven Verify (Backend Agent) | Maven Surefire (28 suites) | **311 / 311 PASS** (0 failures, 0 errors, 0 skips) | Official backend verify recorded as backend evidence. Resolves all 24 `DeploymentConfigurationTest` cases (locale independent & explicit `REDIS_DATABASE`). Excludes opt-in ITs. |
| **Tester Historical Partial Check** | Intermediate check during harness isolation | Maven Surefire (partial run) | **262 PASS / 2 FAIL** (reported to owner) | Historical intermediate run where 2 `DeploymentConfigurationTest` failures (locale `pt_BR` and Redis path parsing) were identified and escalated to backend owner. Resolved in backend's 311-test verify. |
| **Live Postgres + Redis Integration** | PostgreSQL 15.19 & Redis 7.4.11 (Docker) | [`backend/src/test/java/com/matchskill/backend/e2e/PostgresRedisLiveIT.java`](../backend/src/test/java/com/matchskill/backend/e2e/PostgresRedisLiveIT.java) | **9 / 9 PASS** (0 failures, 0 skips; 7.14s) | Real fixed-window Lua rate limiter (`INCR`+`EXPIRE`), 2048-char URLs, F1 atomic rollback, F2 double-blind feedback, 3 sampled PostgreSQL locking disputes (simultaneous feedback 201/409 + single row, simultaneous F1 replacement, complete vs cancel dispute), C/C++/C# slug identity, Redis fail-closed. |
| **Live Backend HTTP Socket** | Embedded Tomcat + H2 | [`backend/src/test/java/com/matchskill/backend/e2e/BackendLiveHttpTest.java`](../backend/src/test/java/com/matchskill/backend/e2e/BackendLiveHttpTest.java) | **1 / 1 PASS** (0 failures) | Full user lifecycle, mutual matching, exchange state transitions, feedback publication over real HTTP socket. |
| **Live Browser E2E Journeys** | Real Spring Boot (port 18089) + PG + Redis + Vite (port 4176) | [`frontend/tests/live/live-browser.spec.ts`](../frontend/tests/live/live-browser.spec.ts) | **2 / 2 PASS** (0 failures) | Live registration, session reload, onboarding, availability persistence, exchange lifecycle, bilateral feedback without API route mocks. |
| **Responsive & Visual Hit-Testing** | Real Spring Boot + Headless Edge (320px & 375px) | [`frontend/tests/live/responsive-visual.spec.ts`](../frontend/tests/live/responsive-visual.spec.ts) | **4 / 4 PASS** (0 failures) | Zero horizontal scroll on 320px, no footer occlusion on 375px onboarding, bottom nav clearance verified. |
| **Combined Live Browser Total** | Port 4176 | Playwright ([`frontend/playwright.live.config.ts`](../frontend/playwright.live.config.ts)) | **6 / 6 PASS** (8.4s) | 100% passing live browser execution with zero mock route interceptions (start 17:43:01, 6 expected, 0 unexpected/skips/flaky). |

---

## 2. Validation Infrastructure & Environmental Status

### 2.1 Services Provisioned

- **PostgreSQL 15.19**:
  - Container: `matchskill-validation-postgres-1` (image `postgres:15-alpine`)
  - Binding: `127.0.0.1:15432`
  - Database: `matchskill_validation`
  - Username: `matchskill_validation`
  - Password: `local-validation-only` (disposable testing credentials)
  - Storage: Isolated tmpfs volume
- **Redis 7.4.11**:
  - Container: `matchskill-validation-redis-1` (image `redis:7-alpine`)
  - Binding: `127.0.0.1:16379`
  - Auth: None (local disposable testing instance, db0)
- **Application Helper Servers**:
  - Spring Boot Live Test Server: `http://127.0.0.1:18089/api` (**TERMINATED / INACTIVE**)
  - Frontend Vite Dev Server: `http://127.0.0.1:4176` (**TERMINATED / INACTIVE**)
- **Container Teardown Status**:
  - All tester executions are complete. Both ports `18089` and `4176` are verified free and inactive.
  - The validation Docker containers (`matchskill-validation-postgres-1` and `matchskill-validation-redis-1`) are released and ready for Maestro teardown (`docker compose -f docker-compose.validation.yml down`).

### 2.2 Test Harness Clean Isolation & Bean Pollution Elimination

In previous runs, test runners declared `@SpringBootApplication(scanBasePackages = "com.matchskill.backend")`. In Spring Boot, this caused test-only configurations (specifically `@Primary RateLimiterService inMemoryTestRateLimiter()`) to be picked up by component scanning during other test suites, contaminating real tests with Mockito mocks.

**Remediation Applied**:
1. In [`backend/src/test/java/com/matchskill/backend/e2e/LiveServerRunner.java`](../backend/src/test/java/com/matchskill/backend/e2e/LiveServerRunner.java) and [`backend/src/test/java/com/matchskill/backend/e2e/LivePostgresRedisServerRunner.java`](../backend/src/test/java/com/matchskill/backend/e2e/LivePostgresRedisServerRunner.java), removed `@SpringBootApplication` and `@ConfigurationPropertiesScan` from top-level classes.
2. Embedded test configurations as static nested `@TestConfiguration` classes (`LocalH2ServerConfig` and `LocalPostgresRedisServerConfig`), which are strictly ignored by standard `@ComponentScan`.
3. Created [`backend/src/test/java/com/matchskill/backend/e2e/PostgresRedisLiveIT.java`](../backend/src/test/java/com/matchskill/backend/e2e/PostgresRedisLiveIT.java) using purely scoped `@DynamicPropertySource` without static JVM `System.setProperty` pollution, named with `*IT` suffix so that standard `mvn test` excludes it while explicit runs (`-Dtest=PostgresRedisLiveIT`) execute against real services with explicit failure if offline (no silent `@EnabledIf` skips).
4. Asserted bean authenticity in tests via `assertThat(Mockito.mockingDetails(rateLimiterService).isMock()).isFalse()`.
5. Unified `frontend/playwright.live.config.ts` to derive both `baseURL` and `webServer.url` from port `4176`, set `reuseExistingServer: false`, and clear disposable IP rate-limit keys in Redis prior to test runs.

---

## 3. Deep Technical Findings: Real PostgreSQL & Redis Verification

Executed in [`backend/src/test/java/com/matchskill/backend/e2e/PostgresRedisLiveIT.java`](../backend/src/test/java/com/matchskill/backend/e2e/PostgresRedisLiveIT.java) (**9 tests, 0 failures, 0 skips, time: 7.14s**):

### 3.1 Real Redis Fixed-Window Lua Rate Limiting Verification (`INCR` + `EXPIRE`)
- **Production Bean Authenticity**: Verified that `RateLimiterService` is the real Spring bean backed by `StringRedisTemplate`.
- **Fixed-Window Counter Execution**:
  - Cleared Redis key `ratelimit:login:127.0.0.1`.
  - Sent 5 consecutive login requests with invalid credentials: each returned HTTP `401 UNAUTHORIZED`.
  - Verified Redis key counter after each request ($1 \to 5$) via `redisTemplate.opsForValue().get(...)`, exercising the atomic Lua script (`INCR` followed by `EXPIRE` on first key creation).
  - Verified Redis key TTL via `redisTemplate.getExpire(...)`: strictly positive and $\le 60\text{s}$.
- **Rate Limit Enforcement**:
  - The 6th request was blocked by the Lua script, returning HTTP `429 TOO_MANY_REQUESTS` with JSON body:
    ```json
    { "code": "RATE_LIMITED", "message": "Too many requests, try again later" }
    ```
  - Response header `Retry-After: 60` was present and verified.
  - Counter in Redis advanced to `6`.
- **Fail-Closed Controlled Offline Test**:
  - Tested controlled connection to offline Redis port `16380`.
  - `RateLimiterService.tryConsume` threw `ApiException` with HTTP `503 SERVICE_UNAVAILABLE` and code `RATE_LIMIT_UNAVAILABLE`, proving strict fail-closed behavior under storage failure.

### 3.2 Meeting URL 2048 Character Support in PostgreSQL
- Confirmed PostgreSQL schema column: `meeting_url | character varying(2048)`.
- Validated baseline URL: `https://teams.microsoft.com/l/meetup-join/19_test_meeting` (HTTP 200).
- Validated extended URL with 1000 characters: HTTP 200, successfully saved and retrieved from PostgreSQL.
- Validated exact boundary URL with 2048 characters: HTTP 200, successfully saved and retrieved.
- Validated boundary violation URL with 2049 characters: Rejected with HTTP `400 BAD_REQUEST` by bean validation `@Size(max = 2048)`.

### 3.3 F1 Atomic Availability & Timezone Persistence in PostgreSQL
- User configured with `timeZone: "UTC"` and `MONDAY 09:00 - 12:00`.
- Attempted atomic update with invalid timezone `"Invalid/Zone_Name"` and new windows.
- Request rejected with HTTP `400 BAD_REQUEST`.
- Queried `GET /api/me/availability`: MONDAY window remained intact in PostgreSQL, proving transactional rollback.

### 3.4 F2 Double-Blind Feedback & Dual Publication in PostgreSQL
- Alice (requester) and Bob (receiver) complete an exchange.
- Alice submits feedback (rating 5, "Great Java lesson") $\to$ HTTP 201.
- Bob inspects exchange feedback: `mine: null, theirs: null, counterpartSubmitted: true`.
- Bob inspects his own profile feedback: 0 reviews returned.
- Third party Charlie inspects Bob's profile: 0 reviews returned, reputation count = 0, reputation average = 0.0.
- Bob submits feedback (rating 4, "Quick learner") $\to$ HTTP 201.
- Immediate dual publication:
  - Bob's received reviews (Alice's rating 5) published to third party Charlie.
  - Alice's received reviews (Bob's rating 4) published to third party Charlie.
  - Bob's reputation aggregate updated: count = 1, average = 5.0.
  - Alice's reputation aggregate updated: count = 1, average = 4.0.

### 3.5 PostgreSQL Concurrency: Simultaneous Feedback Submissions (Same Author)
- **Scenario**: Alice (exchange participant) executes 2 simultaneous `POST /exchanges/{id}/feedback` requests on a `COMPLETED` exchange across 2 concurrent threads synchronized via `CountDownLatch`.
- **Pessimistic Row Lock Dispute**: Both requests execute `exchangeRepository.findByIdForUpdate(exchangeId)`, acquiring a `PESSIMISTIC_WRITE` row lock in PostgreSQL.
- **Result**:
  - The winning thread saves feedback and commits: HTTP `201 CREATED`.
  - The second thread awaits lock release, reads `feedbackRepository.existsByExchangeIdAndAuthorId` (now `true`), and is cleanly rejected: HTTP `409 CONFLICT` (`FEEDBACK_ALREADY_SUBMITTED`).
  - Exactly **1 row** exists in the PostgreSQL `feedback` table for `(exchange_id, author_id)`.

### 3.6 PostgreSQL Concurrency: Simultaneous F1 Availability Replacements
- **Scenario**: User executes 2 simultaneous `PUT /me/availability` requests across 2 concurrent threads:
  - Thread A sends `MONDAY 09:00 - 12:00`.
  - Thread B sends `FRIDAY 14:00 - 17:00`.
- **Pessimistic Row Lock Dispute**: Both threads dispute the row lock via `userRepository.findByIdForUpdate(userId)`.
- **Result**:
  - Both transactions complete with HTTP `200 OK`, executed serially without interleaving deletions and insertions.
  - Final table state in PostgreSQL contains exactly **1 window** (`MONDAY` or `FRIDAY`), proving that availability windows were not corrupted or merged into an invalid union.

### 3.7 PostgreSQL Concurrency: Simultaneous Terminal Exchange Transition (Complete vs Cancel)
- **Scenario**: Exchange is in `SCHEDULED` status. Participant A calls `POST /exchanges/{id}/complete` while Participant B calls `POST /exchanges/{id}/cancel` simultaneously across 2 threads with a latch trigger.
- **Pessimistic Row Lock Dispute**: Both threads dispute `requireExchangeForUpdate(exchangeId)` (`PESSIMISTIC_WRITE` on `exchanges`).
- **Result**:
  - Exactly one transition succeeds with HTTP `200 OK` (transitioning status to `COMPLETED` or `CANCELLED`).
  - The second transaction acquires the lock, observes that the exchange status is no longer `SCHEDULED`, and fails with HTTP `409 CONFLICT` (`INVALID_EXCHANGE_STATUS`).
  - Final status in PostgreSQL is strictly consistent (`COMPLETED` or `CANCELLED`).

### 3.8 Skill Vocabulary Identity & Slugs (C, C++, C#)
- Suggested `C`, `C++`, and `C#` via `POST /skills/suggest`:
  - `C` $\to$ slug `c` (HTTP `201`).
  - `C++` $\to$ slug `c~2b~2b` (HTTP `201`).
  - `C#` $\to$ slug `c~23` (HTTP `201`).
  - All 3 skill IDs and slugs in PostgreSQL are completely distinct and non-colliding.
- Idempotent re-suggestion of `C++` returns the existing skill ID without duplicating rows or reassigning IDs.
- Meaningless punctuation-only suggestions (`###`, `+++`) rejected with HTTP `400 BAD_REQUEST` (`INVALID_SKILL_NAME`).
- *Note on C++ Legacy Data*: The migration and preservation of legacy C++ records (slug `c` without `identity_key`) followed by additive SQL migration and Docker production image verification was independently validated by Maestro in the separate `matchskill_smoke` database (`matchskill-closure-smoke` container, old JAR $\to$ SQL rollout $\to$ prod Docker image). No duplicate legacy IT was required in `matchskill_validation`.

### 3.9 Sampled Concurrency Scope & Retained Architectural Limits
The concurrency scenarios tested here demonstrate that pessimistic row-level write locks (`findByIdForUpdate` on `exchanges` and `users`) and database unique constraints prevent corruption during specific simultaneous write operations. They do not claim full transactional serializability or global atomicity across all application read paths:
1. **Multi-Query Read Snapshots**: Multiple distinct queries executed during read operations may observe different concurrent transaction snapshots.
2. **In-Memory Matching**: Skill matching computes rankings over candidate subsets in memory rather than as a single atomic database query.
3. **Fixed Reference Week for DST**: Recurring availability calculations use a fixed reference week (January 2024), which serves as an approximation for Daylight Saving Time transitions.

---

## 4. Visual & Responsive Hit-Testing Audit

Executed via [`frontend/tests/live/responsive-visual.spec.ts`](../frontend/tests/live/responsive-visual.spec.ts) on headless Microsoft Edge:

### 4.1 Onboarding 375px Viewport (.reg-foot Clearance)
- **Viewport**: $375 \times 667\text{px}$.
- **Action**: Filled teach input with "React", selected option; filled learn input with "Java", selected option.
- **Bounding Box Audit**: Checked vertical overlap between `.reg-foot` and the learn chips.
- **Result**: `[Visual Audit] 375px Onboarding footer overlap with learn chips: false`.
- Both inputs and chips receive pointer focus and clicks without obstruction.

### 4.2 Mobile Bottom Navigation 375px Viewport
- **Viewport**: $375 \times 667\text{px}$.
- **Action**: Navigated to `/availability`, scrolled to page bottom, clicked "Save availability".
- **Result**: Button scrolled into view and was clicked successfully. Notice `"Availability saved."` appeared. No hit-test occlusion by the bottom navigation bar.

### 4.3 320px Ultra-Compact Viewport (Fluid Layout)
- **Viewport**: $320 \times 568\text{px}$.
- **Action**: Measured document scroll width vs client width on `/login`.
- **Result**: `scrollWidth <= clientWidth`. Zero horizontal overflow. All form controls and headers fully visible.

### 4.4 Tab Formatting 375px Viewport
- **Viewport**: $375 \times 667\text{px}$.
- **Action**: Inspected tab trigger inner text on `/invitations`.
- **Result**:
  - Tab 0: `"Received\n0"`
  - Tab 1: `"Sent\n0"`
  - Pattern demonstrates that the tab count renders on a separate line below the label rather than colliding as unspaced text on a single line.

---

## 5. Reconciliation of Owner Runs & Defect Tracking

### 5.1 Clarification: Tester Historical Check vs Backend Final Verify

1. **Intermediate Tester Run (Historical Context)**:
   - During initial runner isolation checks, tester observed 2 failures in `DeploymentConfigurationTest.java` (out of 24 deployment configuration tests; 262 other backend unit/slice tests passed):
     - Assertion failure in `shouldRetainJwtMinimumLengthValidationInProduction` due to non-English JVM locale (`pt_BR`) emitting localized message text (`"tamanho deve ser entre 32 e 2147483647"`).
     - Database index expectation in `shouldUseRedisUrlCredentialsDatabaseAndTlsWithoutOpeningAConnection` expecting database `2` from URL path when Lettuce factory did not parse database index from URL path.
   - Per `AGENTS.md` boundaries, Tester did not edit owner tests or configuration. The findings were escalated to the backend owner.

2. **Backend Owner Final Full Clean Verify (Authoritative)**:
   - Backend owner corrected both points (locale-independent assertion and explicit `REDIS_DATABASE` handling).
   - Backend owner executed full clean verify: **311 / 311 PASS across 28 suites** (0 failures, 0 errors, 0 skips).
   - This authoritative 311-test suite represents the standard backend test verification, which intentionally excludes the external live integration suite (`PostgresRedisLiveIT`).

---

## 6. Artifact & Log References

- **Postgres Live Integration IT**: [`backend/src/test/java/com/matchskill/backend/e2e/PostgresRedisLiveIT.java`](../backend/src/test/java/com/matchskill/backend/e2e/PostgresRedisLiveIT.java)
- **Live Server Runner (H2)**: [`backend/src/test/java/com/matchskill/backend/e2e/LiveServerRunner.java`](../backend/src/test/java/com/matchskill/backend/e2e/LiveServerRunner.java)
- **Live Server Runner (Postgres + Redis)**: [`backend/src/test/java/com/matchskill/backend/e2e/LivePostgresRedisServerRunner.java`](../backend/src/test/java/com/matchskill/backend/e2e/LivePostgresRedisServerRunner.java)
- **Playwright Live Config**: [`frontend/playwright.live.config.ts`](../frontend/playwright.live.config.ts)
- **Playwright Live Browser Test**: [`frontend/tests/live/live-browser.spec.ts`](../frontend/tests/live/live-browser.spec.ts)
- **Playwright Responsive Visual Test**: [`frontend/tests/live/responsive-visual.spec.ts`](../frontend/tests/live/responsive-visual.spec.ts)
- **Playwright Test Results JSON**: `frontend/test-results/live-browser-results.json`

---

## 7. Final Confirmation & Release

1. **Independent Live Suite**: 9 / 9 integration tests in `PostgresRedisLiveIT` pass against real PostgreSQL 15.19 and Redis 7.4.11.
2. **Live Browser Suite**: 6 / 6 Playwright tests pass against real Spring Boot + PostgreSQL + Redis + Vite.
3. **Owner Suites**: Backend full clean verify passes (311 tests / 28 suites); Frontend owner suite passes (28 Vitest, 22 browser scenarios, 0 lint errors/warnings).
4. **Processes Released**: Test helper runners on ports `18089` and `4176` are verified stopped and inactive.
5. **Containers Released**: Docker validation services (`matchskill-validation-postgres-1` and `matchskill-validation-redis-1`) are released for Maestro teardown.
