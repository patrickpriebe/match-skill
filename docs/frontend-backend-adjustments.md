# Frontend integration: backend adjustments

Date: 2026-09-05. Owner: frontend agent. Prepared for coordinator handoff.

## F1 - Persist account time zone (required for complete availability screen)

Flow: user edits weekly windows and their IANA time zone on /availability.
Existing: GET/PUT /api/me/availability returns AvailabilityWindowResponse[]; ReplaceAvailabilityRequest only accepts windows. User.timeZone is returned by auth/me and users/:id but no update endpoint exists.
Gap: a time-zone selector would claim a save that is silently ignored.
Proposed compatible contract: extend PUT body with optional `timeZone: string` alongside `windows`, atomically validate/persist User.timeZone with windows. Keep array response to preserve current readers; frontend refreshes GET auth/me after save. Omitted timeZone preserves current zone. Accept standard IANA names and UTC; return stable 400 error for invalid zone. Alternatively provide an explicit profile update endpoint and notify frontend of the exact contract.
Acceptance: save America/New_York with windows; GET auth/me and users/:id return it; windows keep their local clock times; invalid zone does not partially replace windows; legacy windows-only saves still work.
Frontend fallback while pending: display stored zone read-only; save windows normally. Never report a zone change as successful without API support.

## F2 - Feedback submission state and blind visibility (required for approved blind-feedback behavior)

Flow: history indicates whether the current participant rated each completed exchange; feedback form remains closed after prior submission; counterpart rating remains blind until the viewer submits.
Existing: POST /api/exchanges/:id/feedback returns FeedbackResponse; GET /api/users/:id/feedback returns all received feedback without viewer filtering. ExchangeResponse has no own feedback status. Uniqueness errors use FEEDBACK_ALREADY_SUBMITTED.
Gap: prototype reads fictional MY_FEEDBACK and promises blindness the backend does not enforce. Merely hiding frontend rows cannot implement that promise.
Proposed compatible contract: add optional `myFeedback: { id, rating, comment, createdAt } | null` and `counterpartFeedbackSubmitted: boolean` to participant exchange responses; or add GET /api/exchanges/:id/feedback with `{ mine: FeedbackResponse|null, theirs: FeedbackResponse|null, counterpartSubmitted: boolean }`. Enforce participant access, COMPLETED lifecycle and viewer-aware blindness in all feedback reads (including public-profile feedback for that viewer). The counterpart content must not be returned to a participant who has not submitted their own rating.
Acceptance: before own submission API payload contains no counterpart rating/comment; after submission it becomes readable; refresh/relogin keeps own form closed; history uses real own rating; third parties cannot access private exchange feedback state; duplicate POST remains 409.
Frontend fallback while pending: resolve own submitted feedback through existing recipient profile endpoint and author/exchange IDs; never claim blind visibility. Remove mock records. Keep the blind design requirement explicitly pending until backend enforcement is available.

## F3 - Enriched summaries (performance improvement, fallback available)

Existing /matches and /search return flat MatchResponse with userId/name/bio/strength/reputation only. ExchangeResponse and FeedbackResponse carry bare participant IDs. Design needs skill lists, participant names and zones.
Proposed additive fields: MatchResponse.skillsOffered/skillsWanted/timeZone; ExchangeResponse.requester/receiver summaries `{id, displayName, timeZone}`; FeedbackResponse.author `{id, displayName}`. Existing fields and pagination unchanged.
Acceptance: lists render names/skills without per-row profile requests; real profiles remain the source of truth. Frontend can currently enrich by GET users/:id with per-load deduplication, so this does not block integration.

## F4 - Existing open exchange and duplicate invitation recovery

Existing: paginated GET /api/exchanges, no counterpart filter/status-set filter. Prototype has inert duplicate prevention.
Proposed: optional `counterpartId` query on GET exchanges or `openExchangeId` in profile/match summaries; create endpoint remains authoritative about duplicates. Document whether duplicates mean pair, directed pair, or skill pair.
Acceptance: active REQUESTED/ACCEPTED/SCHEDULED exchange can be opened instead of creating another; a race returns a stable conflict. Frontend can inspect actual paginated exchanges before sending, so this is not a blocking API addition.

## Confirmed frontend-only corrections (no backend action required)

- AuthResponse is token-only: call auth/me after storing bearer token.
- Registration carries displayName/timeZone and password constraints.
- Skill replacement uses offeredSkillIds/wantedSkillIds; MySkills wraps SkillResponse under UserSkillResponse.skill.
- Search already returns strength. Q1 in the old design is resolved by code evidence.
- Match directions can be derived from approved offered/wanted lists.
- AcceptExchangeRequest currently requires skillFromRequester for both strengths; frontend must select/send it.
- Pagination must be handled by frontend; lists are not arrays on the wire.
- Exchange createdAt/updatedAt do not constitute an audit trail. Render only recorded dates, not invented transition timestamps/actors.
- Meeting hosts can remain examples with server validation; a configuration endpoint is optional.

## Coordination status

Initial request ready for coordinator. Implementation will continue on independent screens. Exact adopted contracts and completion status will be appended when the backend responds.

## Adopted F1/F2 contracts (2026-09-05)

Coordinator confirmed backend local validation: 203 tests passed. Read backend-integration-contracts.md after its status changed to implemented and validated locally. Frontend now sends timeZone with windows, refreshes auth/me, and reads GET exchanges/:id/feedback. Temporary F1/F2 fallbacks are removed. Both ratings publish together only when both participants submit; there is no timeout. UI distinguishes private saved feedback from published feedback and continues using official reputationAverage/reputationCount. Frontend integration tests and browser validation remain in progress; backend validation is not represented as frontend end-to-end evidence.
