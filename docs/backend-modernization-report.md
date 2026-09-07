# Backend modernization report

Date: 2026-09-05. Owner: backend agent. Status: implemented and verified locally.

## Result

`./mvnw.cmd -B clean verify` completed successfully in
`C:\Projects\match-skill\backend`: **203 tests, 0 failures, 0 errors, 0 skipped**.
The executable artifact is `backend/target/backend-0.1.0-SNAPSHOT.jar`
(63,300,176 bytes). Final build finished at 21:05:57 America/Sao_Paulo and took
16.606 seconds. No deployment or production database operation was performed.

F1/F2 are implemented and validated for frontend integration. The adopted
requests, responses, visibility rules and error codes are in
[backend-integration-contracts.md](backend-integration-contracts.md). The
coordinator was notified after the passing full build. No frontend files,
frontend documentation, AGENTS.md, or another agent's plan were edited.
The coordinator independently checked the 203 successful Surefire executions
and JAR, and released frontend integration and independent tester review. Code
has remained at that verified checkpoint; subsequent changes here are report
updates only. Further implementation findings require coordinator assignment.

## Implemented changes and evidence

| Area | Change and source |
| --- | --- |
| Exchange DTO lifecycle | ExchangeService.java:57/87/94 and mutation methods now map ExchangeResponse inside their transactions; saveAndFlush runs before mapping mutation timestamps. ExchangeController returns DTOs directly. This removes lazy Skill access after transaction closure with open-in-view disabled. |
| Exchange concurrency | ExchangeRepository.findByIdForUpdate locks only the exchange row, without nullable association joins. Mutations and feedback submission check state/duplicates after acquiring that lock. Conflicting lifecycle requests cannot overwrite each other silently. |
| Exchange listing | EntityGraph loads both skill references, and list ordering is createdAt DESC, id DESC. JPA test proves a two-statement page/count read without per-row skill queries. |
| Whole-set preferences | UserRepository.findByIdForUpdate serializes skill replacement/deletion and availability replacement per user. UserSkillService.java:52 batches requested skill validation, validates before deletion and flushes deletions before inserting identical unique keys. Repeating the same PUT succeeds. |
| F1 availability | ReplaceAvailabilityRequest adds optional timeZone and cascaded/null element validation. AvailabilityService.java:47 validates input and persists zone plus local windows in one transaction; omitted/null zone preserves the old value. Me/profile reads expose the saved zone. |
| F2 participant state | GET /exchanges/{id}/feedback maps mine/theirs/counterpartSubmitted in FeedbackService.java:78. Only participants of COMPLETED exchanges may read it. Counterpart contents remain absent until own submission. POST keeps its shape and concurrent duplicate submissions return FEEDBACK_ALREADY_SUBMITTED. |
| F2 publication | FeedbackRepository.findReceivedForViewer exposes only jointly published received feedback or the viewer's own submission. Public feedback and all reputation aggregation require both participant submissions on COMPLETED exchanges. This policy was explicitly accepted by the coordinator. |
| Reputation cost | ReputationService.java:29 uses one grouped projection query, not Feedback/Exchange entity traversal. MatchingRepositoryTest asserts one SQL statement and zero entity loads for multiple users, including zero-rating defaults and corrupt/historical rows. |
| Matching correctness | Approved-only assignment queries exclude pending/reclassified skills from discovery and classification. Search rejects a pending skill and computes availability tie ordering without dropping deliberate-search results. Ranking is strength, average, count, overlap, then UUID; long offsets prevent arithmetic overflow. Empty inputs avoid unnecessary candidate queries. |
| HTTP contract | Pagination.java:14 centralizes positive size/nonnegative page, caps effective size at 100, rejects unsupported JPA offsets. Malformed JSON, UUIDs, enum values, missing query values and nested validation return JSON 4xx rather than INTERNAL_ERROR. Framework 404/405 envelopes and headers are preserved. |
| Authentication startup | SecurityConfig uses bean-method injection to remove the configuration/handler/service/encoder cycle. Optional Google configuration binds the original property namespace only when both credentials exist; local auth starts without Google. The Google resolver handles only GET /auth/google, avoiding interception of /auth/login and /auth/me. |
| Authentication errors | Security filter failures and failed Google callbacks return JSON 401/403. Bearer tokens, token-only login/register responses and successful OAuth redirect contract remain unchanged. AuthService.login is read-only transactional. |
| Rate limiting | Existing atomic Lua/fail-closed behavior was retained. Exchange creation now uses its mapped handler identity. Stale tests were corrected to verify current script invocation, user/IP key selection, ignored untrusted forwarding headers, limits, Retry-After and unavailable-storage behavior. |
| Indexes | Added user_skills(skill_id,direction), availabilities(user_id), exchanges(requester_id,status,created_at), exchanges(receiver_id,status,created_at), feedback(author_id). Existing unique indexes already cover user_skills user prefix and feedback exchange prefix. |

No new runtime dependency was added. H2 is test-scoped only. No entity columns
were added or renamed; indexes are additive JPA metadata. Controller/repository
layer separation and existing success response fields remain intact, except
the explicitly authorized additive F1/F2 contracts.

The complete revalidation of the 12 old planning items is recorded in
[backend-modernization-plan.md](backend-modernization-plan.md). Previously fixed
timezone/password/name validation, pending-skill writes, overlap rejection and
atomic rate limiting were retained. Broad abstractions, caches, event pipelines
and feature ideas were not introduced without evidence.

## Validation actually executed

| Command in backend/ | Result |
| --- | --- |
| `./mvnw.cmd -B test` before implementation | 140 tests; 1 failure, 7 errors, 0 skipped. Baseline rate-limit tests were stale. |
| `./mvnw.cmd -B test -Dtest=!FeedbackPersistenceTest` first implementation check | 188 tests; 0 failures, 17 errors. New security test slices omitted required configuration-properties beans. Fixed test context imports, then rerun. |
| `./mvnw.cmd -B test -Dtest=FeedbackPersistenceTest` | 12 passed; 0 failures/errors/skipped. |
| `./mvnw.cmd -B -Dtest=*SecurityConfigurationTest,RateLimitInterceptorTest,RateLimiterServiceTest,AuthServiceTest test` | 48 passed; 0 failures/errors/skipped. Google/local/partial modes and real servlet paths covered. |
| `./mvnw.cmd -B clean verify` final | 203 passed; 0 failures/errors/skipped; executable JAR built. |

Important verification surfaces:

- BackendApiIntegrationTest: complete Spring application exercised through
  **MockMvc**, with simulated servlet transport and real BCrypt/JWT, security
  filters, controllers and H2 database. It does not start a network HTTP server
  or exercise a browser-to-server connection. Registers three users, sets
  complementary skills, saves/rejects F1 preferences, traverses the exchange
  lifecycle, then checks F2 private and published data through exchange state,
  recipient/author/third-party profiles, matches and search.
- FeedbackPersistenceTest: 12 executions covering requester/receiver directions,
  separate transaction reads before/after second submission commits, duplicate
  concurrent POST, legacy unilateral rows, missing exchange, third party and
  every non-completed state.
- ExchangePersistenceTest: four executions covering detached DTO values and
  timestamps, pagination/query count, valid transitions/rescheduling and
  concurrent complete/cancel serialization.
- PreferencesPersistenceTest: repeated unique skill replacement, rollback on
  invalid skill, zone/window atomicity, legacy omission, UTC and invalid input.
- MatchingRepositoryTest: four executions validating JPQL projections,
  bilateral publication, invalid authors/statuses, reclassified skills and
  skill fetch behavior with measured Hibernate statement/entity counts.
- RequestContractTest and security contexts: seven binding/response cases and
  19 security cases. Existing domain/unit suites also pass.

H2 verifies actual JPA transactions and SQL execution, but does not establish
PostgreSQL-specific lock behavior, execution plans or performance under load.
The HTTP assertions above use MockMvc, not a listening HTTP server. Browser-to-
Spring network integration is assigned separately to the tester, whose exclusive
harness paths are backend/src/test/java/com/matchskill/backend/e2e/ and
backend/src/test/resources/e2e/. Its results are not included in this report's
203-test checkpoint.
RateLimiterService is mocked in the full API test; dedicated limiter tests mock
Redis execution and do not prove the Lua script against a live Redis server.
Google configuration and redirect/failure routing are tested without a real
provider authorization/code exchange. No coverage percentage was measured.

## Failure evidence and resolution

Baseline decisive lines from `%TEMP%\matchskill-backend-baseline.log`:

```text
[ERROR] Tests run: 140, Failures: 1, Errors: 7, Skipped: 0
[ERROR]   RateLimitInterceptorTest.shouldBlockExchangeCreationWhenRateLimitExceeded:89
  org.mockito.exceptions.misusing.PotentialStubbingProblem:
Strict stubbing argument mismatch. Please check:
    "ratelimit:create-exchange:null",
Unnecessary stubbings detected.
```

The mismatch was an old test expecting a client-controlled X-Forwarded-For key.
The other three interceptor errors named
shouldAllowRequestWhenWithinLimit, shouldBlockLoginWhenRateLimitExceeded and
shouldBlockRegisterWhenRateLimitExceeded. The four RateLimiterServiceTest errors
were shouldAllowFirstRequestAndSetExpiration, shouldAllowSubsequentRequestWithinLimit,
shouldBlockRequestExceedingMaxLimit and shouldFailOpenWhenRedisUnavailable;
all reported `Request protection is temporarily unavailable, try again later`.
They mocked the removed increment/expire calls instead of the existing Lua
execution. Production protection was not weakened to satisfy these tests.

First implementation check, resolved by importing test property configuration:

```text
[ERROR] Tests run: 188, Failures: 0, Errors: 17, Skipped: 0
Parameter 1 of constructor in com.matchskill.backend.security.RateLimitInterceptor required a bean of type 'com.matchskill.backend.config.RateLimitProperties' that could not be found.
```

Infrastructure checks failed in both existing Docker contexts:

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine; check if the path is correct and if the daemon is running: open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
failed to connect to the docker API at npipe:////./pipe/docker_engine; check if the path is correct and if the daemon is running: open //./pipe/docker_engine: The system cannot find the file specified.
```

Final decisive lines from `%TEMP%\matchskill-backend-verify.log`:

```text
[INFO] Tests run: 203, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building jar: C:\Projects\match-skill\backend\target\backend-0.1.0-SNAPSHOT.jar
[INFO] BUILD SUCCESS
```

Additional logs: `%TEMP%\matchskill-backend-first-verification.log`,
`%TEMP%\matchskill-backend-feedback-verification.log`,
`%TEMP%\matchskill-backend-auth-runtime.log`. Current XML/text test results are
under backend/target/surefire-reports. A final clean build removed stale reports
from tests no longer present in source. No test was skipped to obtain success.

## Compatibility, rollout and remaining risks

1. **Accepted F2 ranking change:** existing unilateral reviews remain stored but
   stop contributing to public average/count/rank until the other participant
   submits. No timeout or data deletion. A private review's existence is
   intentionally exposed by counterpartSubmitted; its contents and aggregate
   effect remain hidden. Previously downloaded/external content cannot be revoked.
2. **Indexes/locking:** current ddl-auto:update creates indexes on startup; that
   may block an existing large PostgreSQL table. Plan an explicit production
   rollout/concurrent-index strategy before deployment. Validate SQL plans and
   lock contention on PostgreSQL; the task did not operate on Supabase or Render.
3. **Matching scale:** candidates still load/sort in memory, and availability uses
   a fixed January 2024 reference week. Very popular skills and seasonal DST
   correctness need a separately scoped ranking/timezone design and load tests.
4. **Read snapshots:** F1 writes are atomic. Multi-query profile/matching reads use
   the existing default transaction isolation; a concurrent preferences commit
   between queries can yield different read snapshots. Separate frontend HTTP
   reads are likewise not one snapshot. A repeatable-read or joined-projection
   policy can address this if consistent concurrent read snapshots are required.
5. **Authentication/deployment:** existing default development JWT secret, 24-hour
   bearer lifetime, no revocation and OAuth token query redirect remain. The real
   deployment must supply its JWT secret and working PostgreSQL/Redis. Google
   identity-linking policy was not redesigned; actual provider flows remain untested.
6. **Unchanged lifecycle choices:** both strengths still require skillFromRequester
   on accept; existing SCHEDULED rescheduling and past-time acceptance remain.
   Historical exchange skill references remain readable even if vocabulary status
   changes. New exchange checks still use existing assignments; approval changes
   during an existing trade need a product policy before altering these semantics.
7. **Deferred surfaces:** F3/F4 enrichment/duplicate discovery, feedback array
   pagination, profile editing, notifications, review administration, refresh
   tokens and meeting-provider expansion remain outside this implementation.
   Generic documentation claims about caching/jobs do not imply an implemented
   cache or queue; no such execution path exists in the inspected backend.
8. **Vocabulary normalization:** Slugs.slugify maps C, C++ and C# to the same
   slug, and punctuation-only names can normalize to an empty string.
   SkillService.suggest currently reuses a matching slug without distinguishing
   those names. A controlled-vocabulary normalization policy and regression
   fixtures are follow-up work; no change was made after the verified checkpoint.
9. **Meeting URL length:** ScheduleExchangeRequest has no length constraint,
   while the existing meeting_url column is varchar(255). An allowed-host URL
   exceeding that length produces a data-integrity 409. Aligning validation or
   widening the column requires a documented follow-up; current code is preserved.

Only task-specific backend documents were written. Shared docs/documentation.md,
planning.md and test-plan.md were read and revalidated, not overwritten; several
historical examples differ from the current API, as detailed in the plan.

## Framework references consulted

The explicit delete flush follows Hibernate's documented action ordering;
targeted fetch graphs and lock annotations follow Spring Data JPA capabilities.
See [Hibernate 6.5 flushing](https://docs.jboss.org/hibernate/orm/6.5/userguide/html_single/Hibernate_User_Guide.html#flushing-order)
and [Spring Data JPA query methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html).
Runtime behavior was verified against the project's pinned Spring Boot 3.3.4
dependencies, not inferred solely from current documentation.
