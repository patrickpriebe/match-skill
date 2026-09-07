# Planning: Code Improvements & New Features

## Melhorias no código atual (Code Improvements)

Ordered by risk. Each item cites the concrete file/line evidence found in the current codebase.

### P0 — Correctness & Security

**1. `PENDING_REVIEW` skills can leak into matching**
`UserSkillService.createEntries` (`backend/.../service/UserSkillService.java:86-103`) accepts any existing skill id via `skillRepository.findById(skillId)` with no check on `skill.getStatus()`. `MatchService.classify` (`MatchService.java:163-185`) only reads `UserSkill` rows and never joins back to `Skill.status`. Since `POST /skills/suggest` returns a `PENDING_REVIEW` skill id to the client, a user can immediately `PUT /me/skills` with that id and have it count toward matching — directly violating the documented rule "does not participate in matching until approved." Fix: filter by `Skill.status == APPROVED` either when validating the incoming ids in `UserSkillService`, or add a status join/filter in the match queries.

**2. Unhandled exceptions break the documented error contract**
`GlobalExceptionHandler` (`exception/GlobalExceptionHandler.java`) only maps `ApiException` and `MethodArgumentNotValidException`. Anything else falls through to Spring Boot's default error body, breaking the doc's promise that "errors return the same shape throughout: a status plus `{code, message}}`." Concrete triggers already possible with current code:
   - `RegisterRequest.timeZone` (`dto/auth/RegisterRequest.java:11`) is only `@NotBlank`, not validated as a real IANA zone. A bad value reaches `AvailabilityOverlap.toUtcMinuteRanges` → `ZoneId.of(zoneId)` (`util/AvailabilityOverlap.java:53`) and throws `DateTimeException`.
   - `SuggestSkillRequest.name` (`dto/skill/SuggestSkillRequest.java:5`) has no `@Size` cap; an overlong name overflows the DB column default length on `Skill.name` (`entity/Skill.java:34-35`) and throws `DataIntegrityViolationException`.
   - `FeedbackService.create` (`service/FeedbackService.java:54-57`) does a check-then-insert on the unique `(exchange_id, author_id)` constraint; a genuine race (two concurrent submits) hits the DB unique violation instead of the clean `FEEDBACK_ALREADY_SUBMITTED` path.
   Fix: add a catch-all `@ExceptionHandler(Exception.class)` returning `{code: "INTERNAL_ERROR", message: ...}` (logged, not leaked), plus a specific handler for `DataIntegrityViolationException` → 409, and add the missing `@Size`/timezone validation at the DTO layer.

**3. No rate limiting on auth or exchange creation**
`SecurityConfig` (`config/SecurityConfig.java:48-56`) permits `/auth/login` and `/auth/register` with no throttle — open to credential brute-forcing. `ExchangeController.create` (`controller/ExchangeController.java:40-47`) has no per-user throttle either, so one account can spam exchange requests at every other user. Fix: add a rate limiter (bucket4j, or a Redis-backed counter — Redis is already in the stack) in front of both.

**4. Reputation lookup is N+1 in the match/search hot path**
`MatchService.getMatches` (line 100) and `MatchService.search` (line 138) call `reputationService.of(candidateId)` once per candidate inside a loop, and `ReputationService.of` (`service/ReputationService.java:22`) issues one `findReceivedByUserId` query per call. For a search returning 200 candidates that's 200 extra round-trips. Fix: add a batched `FeedbackRepository` query (`findReceivedByUserIdIn(Set<UUID>)`) and compute all reputations in one pass, same pattern already used for `usersById`/`availabilityByUser`.

### P1 — Quality & Scale

**5. Full in-memory pagination on `/matches` and `/search`**
`MatchService.paginate` (`MatchService.java:226-233`) sorts and slices an already-fully-materialized `List<Candidate>` — every candidate for a popular skill is loaded and ranked in memory on every request, regardless of requested page size. Acceptable at current scale (comment in the code says so explicitly) but should be flagged as an explicit scale ceiling, not silently outgrown. Fix path when it matters: pre-filter more aggressively at the query layer, or cache ranked results per user with a short TTL (Redis is already provisioned).

**6. Overlapping availability windows aren't rejected**
`AvailabilityService.replaceMyAvailability` (`service/AvailabilityService.java:44-51`) only checks `startTime < endTime` per window; it never checks that two windows for the same day don't overlap each other. Overlapping windows double-count minutes in `AvailabilityOverlap.overlapMinutes`, inflating a candidate's overlap score in the ranking. Fix: reject or merge overlapping windows for the same `dayOfWeek` before saving.

**7. No indexes on foreign key columns**
None of the entities (`UserSkill`, `Availability`, `Exchange`, `Feedback`) declare `@Index` on `user_id`, `skill_id`, `requester_id`, `receiver_id`, `exchange_id`, `author_id`. PostgreSQL does not auto-index FK columns, so `ExchangeRepository.findByParticipant`, `UserSkillRepository.findByUserIdAndDirection`, etc. will table-scan as data grows. Fix: add `@Index` in `@Table(indexes = ...)` for every FK used in a `WHERE`/`JOIN`.

**8. Unbounded page size on every list endpoint**
`MatchController` (lines 27, 36), `SkillController` (line 32), `ExchangeController` (line 54) all accept `size` as a raw `int` with `defaultValue = "20"` and no cap. A client (malicious or buggy) can request `size=1000000` and force a huge query/response. Fix: clamp with `@Max(100)` or clamp server-side (`Math.min(size, 100)`) before building `PageRequest`.

**9. JWT: single long-lived token, no revocation, stored in `localStorage`**
`JwtService` (`security/JwtService.java`) issues one 24h-default access token (`application.yml:41`) with no refresh token and no server-side revocation — `AuthContext.logout` (`frontend/src/context/AuthContext.tsx:64-69`) only clears client state, so a stolen token stays valid until it expires regardless of logout. The token itself lives in `localStorage` (`AuthContext.tsx:5, 30, 38, 48, 57`), readable by any injected script (no httpOnly cookie option). Fix (staged): short-lived access token + refresh token flow, or at minimum move the token to an httpOnly cookie set by the backend.

**10. Minor inconsistency**: `AuthService.login` (`service/AuthService.java:49`) is missing `@Transactional(readOnly = true)`, unlike every other read method in the same class.

### P2 — Polish

**11. `GoogleOAuth2SuccessHandler` auto-links an existing local account purely by email match** (`AuthService.findOrCreateGoogleUser`, lines 72-95). Low risk since Google verifies email ownership, but it's a silent identity merge with no user-facing confirmation — worth an explicit product decision, not just an implementation detail.

**12. `AuthService.register`/`login` and friends repeat the same `orElseThrow(() -> new ApiException(NOT_FOUND, "USER_NOT_FOUND", ...))` block in five different services** (`AuthService`, `MatchService`, `ExchangeService`, `AvailabilityService`, `UserSkillService`). Not urgent, but a shared `UserLookup` helper would remove ~15 duplicated lines.

---

## Novas features possíveis (New Features)

Each scoped against the current model — none require breaking the existing contract.

| Feature | Value | Complexity | Touches | Depends on |
|---|---|---|---|---|
| **Email notification on exchange events** (request received, accepted, declined, scheduled, reminder before `scheduledAt`) | Closes the loop without the user polling the app — the exchange flow spans days between strangers who won't otherwise think to check back. | M | Backend (async job) | Background job queue (already planned in stack, not yet built) |
| **Scheduled-exchange reminder** (e.g. 1h before `scheduledAt`) | Cuts no-shows on a platform that never observes the meeting itself. | S | Backend | Same job queue as above |
| **Admin panel to approve `PENDING_REVIEW` skills** | Right now nothing surfaces the review queue — approval has no interface. Without it the vocabulary can never grow past the seed list. | M | Backend + Frontend (new admin-only screen) | A simple admin role/flag on `User` |
| **Multi-skill search** (`GET /search?skill=` → `?skills=` accepting several ids, OR-matched) | Users rarely want just one skill; letting them search "guitar OR piano" in one pass beats three round trips. | S | Backend + Frontend | None |
| **Reputation-range filter on Home/Search** | Lets a user bias toward proven teachers on a platform where the only trust signal is aggregate rating. | S | Backend (query param + filter in `MatchService`) + Frontend | None |
| **Withdraw a sent, still-`REQUESTED` exchange** (`POST /exchanges/{id}/withdraw`) | The doc only lets the *receiver* decline; a requester who changes their mind currently has no path out of `REQUESTED`. Small, real gap in the state machine. | S | Backend + Frontend | None |
| **"Verified skill" badge** (e.g. ≥5 completed exchanges teaching that skill with average ≥4) | Turns aggregate reputation into a per-skill trust signal, which matters more than an overall score when picking a teacher for one specific thing. | M | Backend (new derived query) + Frontend | Enough completed-exchange volume to be meaningful |
| **Related-skill suggestions for the `PENDING_REVIEW` queue** (fuzzy-match a new term against existing slugs before creating a new row) | Cuts vocabulary fragmentation at the source — the doc calls this "the engine of the product." | M | Backend | None, but benefits from the admin panel above |
| **Google Calendar event on `schedule`** (create/update an event with the `meetingUrl`, send both participants a calendar invite) | Removes a manual step from the one moment the platform hands off to an external tool. | L | Backend (Calendar API integration) + Frontend (consent flow) | Google API scope beyond current OAuth2 login scope |
| **Exchange history export** (CSV/PDF of a user's completed exchanges) | Low effort, asked-for utility feature for a "ledger of the platform." | S | Backend + Frontend | None |
| **Lightweight in-app message thread per exchange** (pre-scheduling, so two strangers can agree on details without swapping personal contact info) | Reduces friction between `ACCEPTED` and `SCHEDULED`, and keeps the negotiation on-platform instead of forcing an early external-channel exchange. | L | Backend (new entity) + Frontend | None, but is the single biggest net-new surface on this list |

**Not recommended right now**: anything that changes the `PARTIAL`/`MUTUAL` model itself (e.g., allowing 3-way trades) — the doc is explicit that `MUTUAL` vs `PARTIAL` is "the complete trade" concept the whole ranking depends on; widening it is a data-model change, not a feature add, and should get its own design pass instead of riding along here.
