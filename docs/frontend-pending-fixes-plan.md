# Frontend pending fixes plan

Date: 2026-09-07. Owner: frontend. Status: locally complete and stable; independent integration validation pending.

## Objective and evidence

Close the authorized frontend follow-up without changing React/Vite/Spring architecture or deploying. Read AGENTS.md, pending-work-closure-plan.md, tester-integration-report.md and the previous frontend integration report. The tester's three visual findings were derived from full-page screenshots: onboarding sticky footer, mobile fixed navigation and fused invitation tab label/count. Reproduce interaction in real viewports before deciding which are defects.

Current lint reports nine Fast Refresh warnings in AvailabilitySelector (3), ExchangeCard (2), ExchangeStatusBadge, MatchCard, MeetingLinkField and Button. Current npm audit reports seven affected package nodes: react-router/react-router-dom (production), vitest, @vitest/mocker, vite-node, nested vite and esbuild (development). Inspect current maintainer advisories and compatible fixed releases before changing dependencies; audit's suggested latest major is not automatically the minimal fix. Production Vite is 6.x and unit Vitest is 2.x; nested tooling accounts for the affected Vite versions. Existing app uses BrowserRouter and relative /api with a development-only proxy; no Vercel configuration has been identified.

## Scope and owned files

Own frontend production components/styles, dependency manifests/lock, owner tests/configuration, deployment preparation and QA artifacts. Own only docs/frontend-pending-fixes-plan.md and docs/frontend-pending-fixes-report.md for documentation. Preserve frontend/tests/live/, frontend/playwright.live.config.ts, backend/, AGENTS.md, other owners' documents and the external design source. Notify the coordinator through terminal term_e84d9f20-a1ee-400b-ae5c-c187c85a90d4.

## Ordered steps

1. Save this plan. Record npm audit baseline and affected dependency paths; verify primary advisory/release documentation.
2. Add scoped browser reproduction with viewport screenshots, actual scroll limits and elementFromPoint hit tests for onboarding/footer, bottom navigation and tab label/count. Record baseline evidence, correct real defects and document screenshot artifacts.
3. Separate shared helpers/configuration from component modules and update imports, retaining Fast Refresh lint checks.
4. Select the smallest maintained compatible fixed tooling/router releases; use explicit installs, never audit fix --force, and preserve React/Vite application mode. If a router major is needed, use the maintainer-supported declarative compatibility path without framework migration.
5. On backend confirmation, align meeting URL help and length checks to 2048. Vocabulary continues to use opaque skill IDs and server slugs; no frontend slug generation is needed. Communicate contract dependencies while independent work continues.
6. Prepare Vercel SPA deep-link handling and a documented API-origin contract with public build variables, no secrets or deployment. Verify API requests cannot silently receive the SPA HTML fallback.
7. Run affected owner browser scenarios plus build, lint and unit tests after changes; collect current full/production audit JSON and package versions. Preserve reserved live harness ownership.
8. Save actual results, failing lines, visual conclusions and external limits in frontend-pending-fixes-report.md. Notify coordinator when stable for tester's PostgreSQL/Redis/browser validation.

## API compatibility and dependencies

Keep all auth, F1/F2 privacy and exchange DTOs unchanged. Backend owns vocabulary identity and meetingUrl storage/validation; wait for confirmation before treating the new length as supported. Deployment must point the public VITE_API_BASE_URL at the Render API /api prefix, with Spring CORS allowing the exact Vercel origin and OAuth callback configured there. No frontend variable may contain a credential. Local Vite proxy remains local-only. Coordinator owns hosted settings and deployment-readiness documentation.

## Risks and acceptance

Risks: screenshot stitching falsely implying occlusion; real short-height obstruction; helper import regressions; nested vulnerable dev dependencies surviving a direct update; router compatibility; API HTML fallback; concurrent harness runs. Browser tests must be scoped to owner files, excluding tester live tests.

Acceptance: each visual finding has measured viewport/scroll/hit-test evidence; genuine content is reachable and clickable above overlays at 320/375 px and short heights; label/count visibly separated. Lint exits zero with zero warnings and no disabled Fast Refresh rule. Build/unit/owner browser suites pass. Audit has zero fixable findings or a precise package/path/reachability/fix constraint. URL boundaries match the confirmed backend. SPA/API configuration is prepared without deployment, with remaining hosted verification explicitly stated. Do not rerun green checks without a relevant change.

## Results

Baseline lint: 0 errors, 9 warnings. Baseline audit: 5 moderate, 1 high, 1 critical across 7 package entries. Final lint: 0 errors and 0 warnings; build passed; 28 Vitest tests and 2 Node configuration tests passed. Both final full and production-only audits have zero findings. Browser validation passed in an 18-test run after router/visual changes, an 8-test follow-up after contract changes, and a 3-test affected-flow rerun after the final component edits. These cover 22 distinct browser scenarios across runs. See frontend-pending-fixes-report.md for exact results, resolved failure lines, primary advisory links and evidence paths.

- Visual baseline: four viewport runs (375x900, 375x667, 320x568, 375x320) fail only the tab spacing assertion, measured gap 0 vs required >=6 px. Onboarding input/option/footer and final page controls pass actual hit tests after scrolling; main content ends above sticky navigation at maximum scroll. Preserve those layouts; fix the anchor-only CSS selector to style the actual role=tab buttons.
- Router: registry's latest 6.x is 6.30.6 (already installed). Maintainer GHSA-wrjc-x8rr-h8h6 and GHSA-337j-9hxr-rhxg explicitly affect 6.x and are fixed only from 7.18.0. Select 7.18.3 (patches within the fixed minor), keep react-router-dom compatibility exports and BrowserRouter/Routes/Link APIs. React/DOM 18.3.1 and Node 24.17.0 meet minima React18/Node20. No data router, loaders, hydration, lazy-in-component or multi-segment splats are used. This is a router dependency update, with the existing Vite SPA architecture preserved.
- Vitest: select 3.2.7 in the smallest fixed major, respecting current registry advisory minimum 3.2.6 (maintainer advisory text still lists 3.2.5). All affected nested Vite5/esbuild0.21 instances are removed; tooling deduplicates to existing fixed Vite6.4.3/esbuild0.25.12. Exact full and production-only final audits remain to be recorded.
- Coordinator confirmed 2048/2049 URL boundary (400 VALIDATION_ERROR), skill errors INVALID_SKILL_NAME (400) and SKILL_IDENTITY_CONFLICT (409). Add explicit URL feedback without truncating pasted input and skill errors that preserve query/draft. Permit meaningful one-character suggestions such as C. Inspection of UserSkillService confirms pending skills cannot be assigned (SKILL_NOT_APPROVED): explain this and disable profile save while pending chips remain, with removal available and submitted vocabulary suggestions preserved. Tests must not fake successful assignment of unapproved suggestions.
