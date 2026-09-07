# Backend modernization plan

Date: 2026-09-05. Owner: backend agent. Status: implemented; final build/tests passed.

## Objective and boundaries

Improve demonstrable backend correctness, transactional safety, HTTP validation,
and data access while preserving existing frontend-facing routes, JSON fields,
authentication tokens, and exchange lifecycle. Own `backend/` and this plan plus
`docs/backend-modernization-report.md`. Do not edit frontend files, shared
documentation, AGENTS.md, or another agent's plan. No deployment or production
database operations. Frontend adjustment requests require coordinator routing.

Read before implementation: AGENTS.md, docs/coordination-plan.md,
docs/documentation.md, docs/planning.md, docs/test-plan.md; all backend
controllers/repositories, DTOs, relevant services/entities/utilities/tests,
pom.xml/application.yml and frontend API types/client/http adapter.

Neither project root nor backend is a Git repository in this workspace. Preserve
the existing filesystem and do not initialize Git or reset files.

## Revalidation of docs/planning.md

| Earlier item | Current evidence and decision |
| --- | --- |
| 1. Pending skills | UserSkillService.createEntries now rejects non-approved skills. Read queries and MatchService.search still accept historical/reclassified pending assignments. Apply approved filtering to matching reads without hiding historical exchange skills. |
| 2. Error shape/validation | GlobalExceptionHandler now catches unexpected/integrity exceptions; timezone/name/password validation exists. Malformed JSON, invalid UUID/enum, missing parameters, and invalid pagination still become 500; security filter failures bypass advice. Nested availability windows lack cascaded validation. Fix HTTP handling and nested null validation. |
| 3. Rate limits | Atomic Redis Lua counters, fail-closed 503, per-user exchange keys, and Retry-After already exist. Old tests still mock non-atomic operations and trust X-Forwarded-For; baseline fails. Preserve current protection and test its actual contract; route exchange creation by mapped controller method. |
| 4. Reputation N+1 | MatchService uses ofBatch, but FeedbackRepository returns lazy Feedback entities, and ofBatch initializes each Exchange to attribute ratings. Neither query filters COMPLETED. Use database aggregation projections, including completed/participant predicates. |
| 5. In-memory match pagination | Still present and explicitly documented. Retain ranking semantics; fix integer overflow in page*size, deterministic ties, and skip empty candidate work. SQL ranking/cache remains a separate scale project. |
| 6. Availability overlaps | AvailabilityService.rejectOverlaps already rejects overlaps and permits adjacency. Retain. Fixed January 2024 reference-week timezone calculation remains a documented limitation; no silent seasonal behavior change. |
| 7. Indexes | Foreign-key query indexes are still absent. Add targeted JPA indexes on actual user/skill/participant lookup paths, respecting existing unique-index prefixes. |
| 8. Page size | Controllers still pass unchecked page/size to PageRequest. Centralize page>=0, size>=1 and clamp size to 100 while retaining 0/20 defaults. |
| 9. JWT lifecycle/storage | Long-lived bearer JWT and frontend localStorage remain. Defer cookies/refresh/revocation because they require coordinated frontend/deployment changes. |
| 10. Read transaction | AuthService.login still lacks readOnly transaction. Add it. |
| 11. Google linking | Existing documented email auto-link is retained. Review startup configuration and verified provider identity handling; do not redesign account linking in this task. |
| 12. Repeated user lookups | Small repetition remains; do not introduce a broad service abstraction without concrete benefit. |

Earlier feature ideas (notifications, queue, administration, withdrawal, calendar,
messaging, exports, multi-skill search) are not part of this modernization.

## Additional evidence and priorities

### P0: transaction boundaries and concurrent writes

- ExchangeController.list/get/mutation methods call ExchangeResponse.from after
  ExchangeService transactions return. ExchangeResponse reads lazy Skill name
  and slug; application.yml disables open-in-view. Reads and transitions can
  fail with LazyInitializationException. Map DTOs inside service transactions;
  fetch only response associations for list reads. Flush mutations before DTO
  mapping so createdAt/updatedAt represent the persisted response.
- ExchangeService.requireExchange uses findById without a version or lock.
  Concurrent accept/decline or complete/cancel can both succeed and overwrite
  state. Lock the exchange row for mutations, then enforce existing state rules.
  Avoid locking a nullable outer-join association on PostgreSQL.
- UserSkillService deletes via derived deleteByUserId and reinserts before an
  explicit flush; Hibernate may insert before deleting identical unique keys.
  Validate requested skills first, flush deletes before inserts, and serialize
  whole-set replacement by locking its User. Apply the same per-user locking
  to availability replacement and skill deletion to avoid mixed concurrent sets.
- SecurityConfig constructor -> GoogleOAuth2SuccessHandler -> AuthService ->
  PasswordEncoder bean in SecurityConfig forms a startup dependency cycle.
  Decouple bean dependencies and prove context startup with optional Google
  configuration, local login, and bearer-only API error responses.

### P1: query cost, validation and reproducibility

- Move reputation aggregation into grouped queries instead of loading all
  feedback/exchanges; preserve average/count and zero defaults.
- Exclude unapproved assignments from match classification; search currently
  ranks every candidate with overlap=0 and can carry a null strength. Restore
  documented availability tie ordering without filtering deliberate search.
- Add stable ID tie-breakers and overflow-safe offsets; retain MUTUAL before
  PARTIAL, reputation, availability ordering. Rating-count tie-break follows
  docs/test-plan.md and will be documented explicitly.
- Add targeted indexes from JPA metadata only, no manual production SQL.
- Cover web binding, nested collection validation, repeated replacement,
  detached response serialization, query counts, lifecycle races, and repository
  predicates with meaningful tests beyond Mockito-only checks.

## Owned files and ordered implementation

1. Save this plan and notify coordinator of baseline failures and pre-existing
   frontend contract mismatches before implementing.
2. Backend data worker: ExchangeService, ExchangeRepository, ExchangeController,
   and their tests; DTO mapping in transaction, safe locking, deterministic
   list ordering. Preserve existing accept payload requirement and rescheduling.
3. Backend matching worker: MatchService, ReputationService, FeedbackRepository,
   matching/reputation tests and any new projection; approved matching queries
   in UserSkillRepository coordinated with parent; no controller edits.
4. Parent: request/error handling, pagination helper and non-exchange controllers,
   nested DTO validation, SecurityConfig/AuthService, rate limiter regression
   tests, UserSkillService/AvailabilityService/UserRepository, entity indexes.
5. Add isolated database/web integration verification (test dependencies only
   where needed). Run focused tests then full Maven verify. Use a disposable
   database, never the application's existing database. Record infrastructure
   limitations rather than skipping them silently.
6. Inspect newly appearing frontend-backend-adjustments.md without implementing
   additional scope until coordinator delegation. Finish plan status and report
   exact commands/results/failures, notify coordinator with report paths.

## Compatibility and coordinator dependencies

- Keep `/api` context path, bearer Authorization header, auth `{token}` response,
  MeResponse fields, PageResponse `{items,page,size,total}`, all exchange DTO
  fields/statuses, MySkillsResponse entry IDs, and existing stable domain codes.
- Existing frontend API types differ from current backend: registration currently
  requires displayName/timeZone, auth returns only token, skill replacement uses
  offeredSkillIds/wantedSkillIds, match DTO is flat, and availability PUT does not
  update a timezone. These precede this task. Notify coordinator; keep backend
  wire contracts unchanged pending a separately routed integration request.
- Invalid requests will return appropriate 4xx `{code,message}` instead of 500;
  missing/invalid bearer credentials will receive JSON 401 rather than a login
  redirect. Oversized list requests return at most 100 and report effective size.
- No new schema columns or required migration: only additive indexes via existing
  JPA schema management. Index creation can lock tables on an existing deployment;
  production rollout remains coordinator-owned and needs a planned maintenance
  or concurrent-index approach. Pessimistic locks serialize writes per aggregate.
- Do not change optional acceptance semantics, scheduling policy, timezone update
  API, profile editing, feedback response pagination, OAuth redirect token format,
  or JWT lifetime without coordinator routing. Document gaps as follow-up work.

## Baseline and acceptance criteria

Executed in `C:\Projects\match-skill\backend` with installed Java 21 and wrapper:
`./mvnw.cmd -B test` (log: `%TEMP%\matchskill-backend-baseline.log`).

Exact baseline summary:

```text
[ERROR] Tests run: 140, Failures: 1, Errors: 7, Skipped: 0
[ERROR]   RateLimiterServiceTest.shouldAllowFirstRequestAndSetExpiration:39 ... Api Request protection is temporarily unavailable, try again later
[ERROR]   RateLimitInterceptorTest.shouldBlockExchangeCreationWhenRateLimitExceeded:89
```

Three interceptor tests additionally report `UnnecessaryStubbing`; four limiter
tests use the outdated non-Lua/fail-open contract. Capture complete decisive
failure lines in the final report. Docker's selected desktop-linux context is
currently unavailable; inspect another existing context before deciding the
database-test strategy.

Acceptance: full `./mvnw.cmd -B verify` builds the executable jar, zero failures;
database tests exercise real JPA transactions/query validation and detached DTOs;
web tests preserve JSON contracts and return correct error statuses; no frontend
or shared-document edits. Explicitly report whether PostgreSQL and live Redis
were exercised, and avoid claiming unit mocks validate SQL, locks, or Lua.

## Validation results

Final `./mvnw.cmd -B clean verify` passed: 203 tests, 0 failures, 0 errors,
0 skipped; executable JAR produced. H2 persistence, Spring MVC/JWT through
MockMvc (simulated servlet transport, no listening network server),
OAuth configuration contexts and mocked rate-limiter verification passed.
PostgreSQL/Redis/Google live coverage remains unavailable/not executed; details,
intermediate failures and residual risks are in backend-modernization-report.md.

Implementation refinements: HTTP offsets above Integer.MAX_VALUE return 400
before calling JPA; skill replacement validates through one batched lookup and
flushes removals before inserts; OAuth auto-configuration is excluded in favor
of conditional binding of the SAME spring.security.oauth2.client namespace.
Google's default /auth/{registrationId} resolver also intercepted local auth
routes, so it now resolves only GET /auth/google. See the worker's separate
backend-auth-runtime-plan.md for focused evidence and validation.

## Authorized integration extension: F1 and F2

Coordinator forwarded F1/F2 after reviewing this plan and explicitly accepted
the bilateral-publication policy before final validation. Read the Integration
handoff in docs/coordination-plan.md and docs/frontend-backend-adjustments.md.
F3/F4 remain deferred. Parent owns the additions below and the new
docs/backend-integration-contracts.md; workers keep separate implementation files.

1. F1: extend ReplaceAvailabilityRequest with optional timeZone; validate it and
   all windows before persistence, update the locked User and windows in the same
   transaction. Null/omission preserves the zone; blank/invalid names return 400.
   Preserve window local times and the array response. Verify rollback, legacy
   input, auth/me and profile reads. Owned: availability DTO/controller/service
   and unit/integration tests.
2. F2: add participant-only completed-exchange feedback state DTO and GET route;
   map mine always, theirs only after own submission, boolean submission status.
   Keep POST and duplicate 409. Serialize submissions using the exchange row lock
   so concurrent duplicates retain FEEDBACK_ALREADY_SUBMITTED. Parent owns
   FeedbackService, feedback DTOs, UserProfileController and the new GET method
   in ExchangeController (exchange worker has released that file).
3. Publication policy: a completed exchange contributes public feedback and
   reputation only after BOTH participants submitted. A single submitted row
   remains visible solely to its author. Apply consistently in profile feedback,
   exchange state, profile aggregates and matching/search rankings, including
   requests from third-party accounts. Notify coordinator before applying these
   publication/aggregate predicates. Matching worker owns FeedbackRepository
   predicates/projection and aggregation tests; parent owns endpoint tests.
4. Ranking impact is intentional and necessary: historical one-sided reviews
   cease contributing until the other participant rates; no blind-period timeout
   or unilateral publication is introduced. Completed exchange counts/lifecycle
   remain unchanged. No new columns, no production rewrite or deletion.
5. Acceptance: never expose hidden rating/comment through profiles or aggregate
   changes to recipient/third party; own submitted form state survives refresh;
   after second submission both reviews publish atomically with reputation.
   Include duplicate race and participant/status authorization checks.
