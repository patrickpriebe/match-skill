# Backend pending fixes plan

Date: 2026-09-07. Owner: backend agent. Status: implementation, local image and
full clean verify completed; coordinator confirmed isolated SQL/image rehearsal.

## Objective, evidence and ownership

Close the two confirmed backend input defects and prepare a deployable Java 21
image/configuration without deploying. Read AGENTS.md,
docs/pending-work-closure-plan.md, prior backend reports/contracts, current
Slugs/SkillService/SkillRepository/Skill entity, scheduling DTO/validator/entity,
application configuration, Maven configuration and existing owner tests first.

- Slugs.slugify currently drops +/#; C, C++ and C# all become c. SkillService
  assumes a slug hit proves identity and reuses that row without comparing its
  name. Punctuation-only input can produce an empty slug. Skill.name and slug
  have unique constraints, but there is no independent normalized identity key.
- ScheduleExchangeRequest has no length bound, MeetingUrlValidator only checks
  scheme/host, and Exchange.meetingUrl has the default varchar(255) column.
  Valid meeting URLs above 255 pass validation and fail at persistence.
- There is no backend Dockerfile/deployment runbook. Port is hard-coded 8080;
  OAuth success defaults to /oauth/callback while frontend uses /auth/callback.
  Existing local datasource/JWT defaults and ddl-auto:update are unsuitable as
  implicit production configuration.

Own backend/ except tester-exclusive src/test/java/com/matchskill/backend/e2e/
and src/test/resources/e2e/. Own this plan and backend-pending-fixes-report.md.
Backend deployment SQL/runbook lives under backend/deployment/. No frontend,
AGENTS.md, coordinator docs or tester files may be edited. Do not use any
existing database/Redis/container data; isolated services are provisioned by
the coordinator. No Git initialization, production SQL execution or deployment.

## Ordered implementation and allocation

1. Save this plan and notify the NEW coordinator terminal
   term_e84d9f20-a1ee-400b-ae5c-c187c85a90d4 of proposed contracts/schema.
2. Parent implements skill identity normalization, collision handling, bounded
   concurrent retry and owner unit/JPA regression tests. Owned files: util/Slugs,
   entity/Skill, repository/SkillRepository, service/SkillService and their tests.
3. Exchange worker implements a shared 2048 length constant, DTO/validator/entity
   alignment and boundary/persistence/rollback tests outside reserved e2e paths.
4. Runtime worker prepares Dockerfile, .dockerignore, application.yml and an
   opt-in production profile, relevant configuration tests. Consult official
   current Render/Docker documentation. Parent owns deployment SQL/runbook.
5. Run focused regressions, then full clean verify. Do not run concurrent Maven
   builds. Coordinate image building/smoke with coordinator/tester; use the image
   only against explicitly assigned disposable services, not existing resources.
6. Publish a stable artifact/version with actual evidence, SQL rollout details,
   limitations and tester handoff in backend-pending-fixes-report.md.

## Skill identity and compatibility design

- Compute a deterministic normalized identity from the full name, with NFD
  accent folding, Locale.ROOT case folding and collapsed separators. Preserve
  + and # as structural symbols, never replacing substrings or English words.
  Require at least one Unicode letter/digit. Reject meaningless input with
  HTTP 400 INVALID_SKILL_NAME before any write.
- Separate new identity from the public slug: store the SHA-256 of the canonical
  identity in a nullable unique identity_key varchar(64). Existing names, IDs,
  statuses and slugs are NEVER rewritten. Verify the canonical name when reading
  an identity-key match rather than trusting the hash alone.
- New safe slugs distinguish symbols via reserved escaped markers (e.g. C++
  becomes c~2b~2b, C# becomes c~23, C remains c). Normal text keeps the previous
  accent/case/separator behavior. Literal C plus plus/C sharp remain different
  identities. Bound generated slug length to the existing 255-character column.
- Resolve legacy candidates using the old slug algorithm, new deterministic
  slug/fallback, identity key and case-insensitive exact name. Compare canonical
  names before reusing a row. A single matching unkeyed legacy row acquires only
  identity_key; its public ID/slug remain unchanged. Multiple matching legacy IDs
  cause 409 SKILL_IDENTITY_CONFLICT for manual review, never an automatic merge.
- If an unrelated legacy row occupies the preferred new slug, allocate a bounded
  deterministic hash-suffixed fallback. If that is also occupied incompatibly,
  return explicit conflict. Example: old C++/slug=c stays C++/c; new C gets a
  separate ID and fallback slug instead of being aliased to C++.
- Unique identity_key/slug/name arbitrate races across instances. Persist through
  a repository transaction, catch uniqueness failure OUTSIDE that transaction,
  then retry a bounded lookup/create attempt with a fresh transaction. No retry
  inside an aborted PostgreSQL transaction, no process-local lock as authority.
- Additive nullable schema avoids a forced data rewrite. Document rollout and
  review of legacy ambiguous/imported rows; never execute an automatic bulk
  normalization/merge. Old application instances must stop serving suggestions
  before enabling the new writer, since old code still performs wrong aliases.
- Provide an offline, test-source SkillIdentityAudit command under
  backend/src/test/java/com/matchskill/backend/tools/ using the exact Slugs
  implementation. Read an exported JSON vocabulary, report ambiguous/meaningless/
  mismatched records and unique unkeyed candidates; perform no database writes.
  Document guarded manual backfill only after review with suggestion writers
  paused. This closes the reconciliation gap for arbitrarily imported old slugs
  that bounded request-time lookups cannot discover by spelling/old slug alone.

## Meeting URL contract

All existing request/response fields, HTTPS requirement and allowlist stay.
URLs of length 255, 256 and 2048 are valid when host/scheme pass; 2049 is rejected
before persistence. DTO rejects oversized input with existing 400 VALIDATION_ERROR;
direct domain validation uses existing 400 INVALID_MEETING_URL. Length uses
Java/JavaScript UTF-16 units; storage varchar(2048) is sufficient and may be
more permissive for supplementary code points. No truncation.

Shared constant drives @Size, the validator and @Column(length=2048). Test
committed round-trip and failed-reschedule rollback of URL/status/date/timestamp.
Preserve the existing acceptance/rescheduling/time policies.

## Deployment/configuration extension

- Add backend/Dockerfile with Java21 build/runtime stages and backend/.dockerignore;
  no new application runtime dependencies. Preserve Maven wrapper/pinned app stack.
- application.yml uses PORT with 8080 fallback; local OAuth redirect default becomes
  http://localhost:5173/auth/callback. Explicit OAUTH2_SUCCESS_REDIRECT_URI overrides
  retain precedence. Test both default and override.
- Opt-in production profile uses ddl-auto:validate, mandatory JWT secret and
  explicit TLS datasource/Redis settings. Retain other local defaults. Document
  exact Render environment names and Google/CORS redirect requirements.
- Document SQL to expand meeting_url and add nullable identity_key, plus staged
  CREATE INDEX CONCURRENTLY for existing query indexes and the identity unique
  index. Use bounded lock waits, preflight/postflight and preserve all data. Index
  statements must run outside transaction blocks; do not rely on IF NOT EXISTS
  to prove an existing index has the intended definition/is valid.
- Production schema changes happen before deploying code using new columns;
  document a non-destructive rollback (keep widened column/new nullable column).
  No SQL scripts execute in production as part of this assignment.

## Risks and acceptance criteria

No client should derive identity from a slug or rename existing IDs. Historical
arbitrary imported aliases require review; a collision does not authorize a
merge. SHA-256 collisions are additionally guarded by canonical-name comparison.
Nullable legacy keys permit gradual adoption; old-writer and review sequencing
must be explicit. A wider column and new index still need PostgreSQL rollout
validation. JDBC/Redis TLS/Render/Google configuration depends on actual hosting
settings; no credentials should be printed or fabricated.

Required regression cases: C/C++/C#; C plus plus/C sharp; unrelated substrings;
punctuation/symbol-only names; Unicode/case/accent and Locale.ROOT; existing IDs/
slugs and conflicting legacies; concurrent same-identity suggestions; URL
255/256/2048/2049, real persistence and rollback; PORT and OAuth overrides;
production required properties/DDL validation; full build/tests; local image build.

Distinguish owner H2/MockMvc/configuration tests from tester's new PostgreSQL,
Redis Lua and real network/browser evidence. Prior 203-test checkpoint plus the
tester-owned live HTTP test are historical evidence; record fresh actual counts.
Report exact failure lines if any check fails. Docker is now available; coordinate
resources before using it. Do not repeat a green build without changed code or
an unresolved verification concern.

## Actual validation

- Skill/URL focused command: `mvnw.cmd -B test -Dtest=SlugsTest,SkillServiceTest,SkillIdentityPersistenceTest,MeetingUrlValidatorTest,ScheduleExchangeRequestTest,ExchangePersistenceTest`.
  Result: 100 tests, zero failures/errors/skips. H2 persistence and service/unit
  evidence; not PostgreSQL or a real HTTP server.
- Runtime/security focused command: `mvnw.cmd -B test -Dtest=DeploymentConfigurationTest,GoogleSecurityConfigurationTest,IncompleteGoogleSecurityConfigurationTest,SecurityConfigurationTest`.
  Corrected result: 43 tests, zero failures/errors/skips. Initial three failures
  revealed Redis URL database-selection behavior and a locale-sensitive test
  assertion; details and exact lines are in the report.
- After the final test fixture isolation and offline auditor were added:
  `mvnw.cmd -B test -Dtest=SkillIdentityAuditTest,DeploymentConfigurationTest`.
  Result: 54 tests, zero failures/errors/skips (30 auditor + 24 configuration).
  The repeated 24 configuration tests are not additional distinct coverage.
- `docker build --tag matchskill-backend:closure-local --file Dockerfile .`
  succeeded. Image digest:
  `sha256:5708fcbc11b3267ce4677ac090011fe2eb2c01f0c7b7719c51d725e40b8d29d1`.
  The build intentionally skips tests; the isolated runtime reports Temurin
  21.0.12+8 and runs as UID/GID 10001. No application/database service was started
  by the backend owner.
- SQL files 01/02/03 and deployment README are stable. The coordinator reported
  exit 0 for all three on PostgreSQL 15.19 in `matchskill_smoke`, preserved legacy
  IDs/slugs/URL, six valid equivalent indexes and successful image prod/validate
  startup. No SQL was executed by this owner.
  Redis smoke must use explicit `REDIS_DATABASE=1`; `/1` in REDIS_URL is insufficient.
- After the coordinator released the isolated harness, `mvnw.cmd -B clean verify`
  completed at 2026-09-07 14:44:00 -03:00: 311 tests in 28 suites, zero
  failures/errors/skips, BUILD SUCCESS. All 24 DeploymentConfigurationTest cases,
  including corrected locale/Redis cases, passed. The 9 PostgreSQL IT cases
  independently written into the shared report directory were excluded from
  this run's count. See report for evidence snapshot, JAR hash and exact limits.
- Coordinator Redis DB1 probe: nonexistent login returned 401; scoped counter
  incremented to 1 with TTL 59 seconds. DB0 and tester data were untouched.
  Image digest remains unchanged. No repeated build is needed without new code.
- Coordinator closed the PostgreSQL same-identity race check on the unchanged
  prod image: eight PostAsync requests were dispatched before awaiting responses;
  all returned 201 with one distinct ID/slug. SQL confirmed one row and one
  identity key in matchskill_smoke. Sanitized evidence was read from
  `%TEMP%/matchskill-closure-20260907/concurrent-skills-results.json`.
  This separate smoke adds no cases to the 311-test Maven count. Final code,
  image digest and JAR checkpoint are preserved; only owner documentation changed.
