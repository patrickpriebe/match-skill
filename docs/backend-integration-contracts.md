# Backend integration contracts

Date: 2026-09-05. Owner: backend agent. Status: implemented and validated locally.
Scope: coordinator-authorized F1/F2 only; F3/F4 remain deferred.

## F1: availability and time zone

`PUT /api/me/availability` requires bearer authentication and accepts:

```json
{
  "timeZone": "America/New_York",
  "windows": [{"dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "10:00"}]
}
```

timeZone is optional; omission or null preserves the current zone. If supplied,
it must be a supported IANA zone or UTC (blank is invalid). Windows retain their
local clock values. User zone and windows save atomically; invalid input leaves
both unchanged. Response remains AvailabilityWindowResponse[], including IDs,
dayOfWeek/startTime/endTime. GET /api/auth/me and GET /api/users/{id} return the
persisted timeZone. Frontend should refresh auth/me after a successful save.
Invalid input returns 400 with the existing {code,message} envelope.

## F2: participant feedback state

`GET /api/exchanges/{id}/feedback` requires a bearer token, participant membership,
and status COMPLETED. Response:

```json
{"mine": null, "theirs": null, "counterpartSubmitted": false}
```

mine/theirs are FeedbackResponse or null. Fields are id, exchangeId, authorId,
rating, comment, createdAt. mine is always visible to its author. theirs remains
null until the viewer submits their own review. counterpartSubmitted indicates
existence only and never reveals rating/comment. Third parties receive 403
NOT_A_PARTICIPANT; missing exchanges 404 EXCHANGE_NOT_FOUND; other lifecycle states
409 EXCHANGE_NOT_COMPLETED. POST remains unchanged and duplicates return 409
FEEDBACK_ALREADY_SUBMITTED, including concurrent submissions.

## Publication policy and ranking impact

Policy forwarded before changing query semantics and explicitly accepted by coordinator:

- A single submitted review remains private to its author. It never appears to
  its recipient or unrelated viewers through profile feedback.
- Both reviews publish together only when both participants have submitted on
  the COMPLETED exchange. There is no timeout-based unilateral release.
- GET /api/users/{id}/feedback keeps its array shape. It returns published
  received feedback plus the authenticated viewer's own authored review of that
  user, if any. It never returns another person's unpublished content.
- Profile reputationAverage/reputationCount and match/search reputation/ranking
  use ONLY reviews from completed exchanges with both participant submissions.
  A private review therefore changes neither the exposed average nor the count.
- Existing one-sided reviews stop contributing to public reputation/rank until
  the counterpart submits. No data is deleted. Completed exchange history and
  the MUTUAL/PARTIAL hierarchy are unchanged.

This rule covers API reads by the recipient and third-party accounts; merely
hiding one endpoint would allow inference by subtracting aggregate scores. It
does not conceal that feedback was submitted (the boolean intentionally exposes
that), the author's own rating, or ratings both sides have already released.
No claim is made about external sharing or content previously retrieved before
this policy was introduced.

## Validation

Executed `./mvnw.cmd -B clean verify` in backend: **203 tests, 0 failures,
0 errors, 0 skipped; BUILD SUCCESS**. The executable JAR was produced.

- BackendApiIntegrationTest exercises the full Spring application through
  **MockMvc**: simulated servlet transport, actual JWT bearer authentication,
  controllers and H2 JPA under the `/api` context path. It starts no listening
  HTTP server and performs no browser-to-server network test. It
  covers registration, skill setup, F1 save/rollback/me/profile reads, exchange
  lifecycle, F2 author/recipient/third-party reads, duplicate POST, and private vs
  public reputation through profiles, matches and search. Rate storage is mocked.
- PreferencesPersistenceTest verifies repeated skill replacement, invalid-input
  rollback, zone/window atomicity, legacy omission and UTC changes with real JPA.
- FeedbackPersistenceTest: 12 executions cover both participant directions,
  post-commit publication, before-commit isolation, legacy unilateral rows,
  third-party 403, 404, invalid lifecycle states and concurrent duplicate 409.
- RequestContractTest verifies nested validation, F1 array response and optional
  input binding, F2 null fields and boolean; MatchingRepositoryTest verifies
  public predicates and one aggregation query with zero entity loads.

These results validate the local implementation for frontend integration, not a
deployment. H2 does not prove PostgreSQL-specific locking/query plans. Redis Lua
and Google provider exchanges were not exercised against real services. Docker
is unavailable in both existing contexts. Full evidence and residual risks are
in backend-modernization-report.md; raw build log is
`%TEMP%\matchskill-backend-verify.log`.
