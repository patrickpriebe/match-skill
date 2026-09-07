# Frontend pending fixes report

Date: 2026-09-07. Owner: frontend. Status: locally stable and ready for independent integration validation.

## Delivered changes

Corrected invitation/scheduled tab styling, separated all nine Fast Refresh helper exports, removed the currently reported npm vulnerabilities, aligned skill and meeting-link UX with the coordinator-confirmed backend contract, and prepared Vercel configuration without deployment. React 18, Vite 6, Spring JWT, REST DTOs and bilateral feedback behavior remain in place. Router is updated as a declarative library; no framework-mode migration was introduced.

Owned changes are confined to frontend/ and this report plus frontend-pending-fixes-plan.md. The external design source, backend/, other owners' documentation, frontend/tests/live/ and frontend/playwright.live.config.ts were preserved. Owner Playwright configuration now ignores tests/live/ so a normal fixture run does not accidentally invoke the tester's separate harness.

## Visual findings: reproduction and disposition

Used installed Microsoft Edge headless, viewport screenshots (not full-page stitching), real scrolling and document.elementFromPoint at control centers. Four viewports: 375x900, 375x667, 320x568 and 375x320. The last is a short viewport, not a claim of real mobile software-keyboard emulation. API data was controlled by the owner HTTP fixtures.

| Finding | Evidence | Disposition |
|---|---|---|
| Onboarding footer cuts across second panel | The input, Python suggestion option and save button all pass hit tests after scrolling at all four sizes; the option was actually clicked. At 375x667 the input occupies y311.1-356.4 and save button y589.0-633.0 at bottom. The initial sticky footer can appear over content in a stitched image, but the underlying input and final content are reachable. | No persistent obstruction reproduced; screenshot-based defect is a false positive within tested viewports. Preserve layout, no arbitrary padding added. |
| Mobile navigation covers final content | Runtime navigation is position:sticky, not fixed. Availability, feedback, scheduling and profile final controls pass hit tests. At maximum scroll, main bottom and navigation top coincide within subpixel rounding, leaving the content above the bar. | No persistent obstruction reproduced; preserve layout. |
| Tab label/count fused | Baseline measured gap=0 in all four sizes. CSS targeted .tabs a while component uses button[role=tab]. Updated selector and removed inline border reset that hid the active underline. | Real defect fixed: gap=7px, visible active underline, keyboard semantics retained. |

Navigation top at maximum scroll is approximately 843px (900px height), 610px (667), 511px (568) and 263px (320). Differences from main bottom are at most 0.3px, not hidden content. Before/after viewport images and base64-encoded geometry attachments are preserved in `frontend/qa/pending-fixes/visual-before/` and `visual-after/`, including onboarding-bottom.png, _availability-bottom.png and invitation-tabs.png. Manually inspected the 375x667 onboarding and availability bottoms and corrected invitation tabs.

## Fast Refresh boundaries

Moved shared values/functions into domain/availability-helpers.ts, exchange-helpers.ts, exchange-status.ts, match-helpers.ts, meeting-hosts.ts and ui/button-styles.ts; updated all consumers. Components remain in their original modules. The react-refresh/only-export-components rule is still enabled. Final lint: zero errors and zero warnings, down from nine warnings.

## Current dependency audit and scope

Queried registry and primary maintainer advisories on 2026-09-07. Baseline: seven affected package entries (five moderate, one high, one critical). These are dependency-tree findings, not seven demonstrated exploits in this application's runtime.

| Package and installed path before | Reachability assessment | Resolution / current version |
|---|---|---|
| react-router-dom 6.30.6 -> node_modules/react-router 6.30.6 | Production browser routing. Open-redirect API exists, but inspection found application-generated paths/IDs rather than arbitrary user redirect targets. SSR hydration advisory does not apply to this BrowserRouter application. | Both 7.18.3; no remaining advisory. |
| node_modules/vitest 2.1.9 | Development only. Current script uses vitest run with jsdom, without Vitest UI/API or Vitest Browser Mode. The vulnerable UI/server is not shipped in dist. | 3.2.7; no remaining advisory. |
| vitest/node_modules/@vitest/mocker 2.1.9 | Development transitive advisory inherited from nested Vite. | @vitest/mocker 3.2.7, deduplicated fixed Vite. |
| node_modules/vite-node 2.1.9 | Development transitive advisory inherited from nested Vite. | 3.2.4, deduplicated fixed Vite. |
| vitest/node_modules/vite and vite-node/node_modules/vite 5.4.21 | Development servers; Windows file-access/UNC issues matter when affected server middleware is running/exposed. Neither server is part of the deployed static bundle. | Both affected copies removed; all tools resolve Vite 6.4.3. Direct Vite was already 6.4.3. |
| vitest/node_modules/esbuild and vite-node/node_modules/esbuild 0.21.5 | Development transitive; advisory concerns esbuild serve mode. The project does not invoke esbuild serve. | Affected copies removed; remaining esbuild 0.25.12 via Vite 6.4.3. |

Router 6 was explicitly checked: npm view react-router-dom@6 version ends at 6.30.6, which was already installed. Both maintainer advisories list fixes only from 7.18.0: [untrusted-path redirect, GHSA-wrjc-x8rr-h8h6](https://github.com/remix-run/react-router/security/advisories/GHSA-wrjc-x8rr-h8h6) and [SSR constructor injection, GHSA-337j-9hxr-rhxg](https://github.com/remix-run/react-router/security/advisories/GHSA-337j-9hxr-rhxg). Therefore a Router 6 patch could not remove these findings. Selected 7.18.3, the patch release in the fixed minor, with react-router-dom compatibility exports retained.

The [maintainer upgrade guide](https://raw.githubusercontent.com/remix-run/react-router/v7/docs/upgrading/v6.md) requires Node 20/React 18. Local Node 24.17.0 and React/react-dom 18.3.1 meet these requirements. Existing BrowserRouter, Routes, Route, Outlet, Link, NavLink, Navigate and navigation hooks compile and pass browser journeys. There are no multi-segment splat routes, RouterProvider data loaders/actions, manual hydration or React.lazy inside components requiring the guide's corresponding migrations. OAuth callback, session refresh, onboarding, request/acceptance/scheduling, feedback privacy, search/profile and pagination were exercised after the update.

Vitest's [maintainer advisory](https://github.com/vitest-dev/vitest/security/advisories/GHSA-5xrq-8626-4rwp) still names 3.2.5, while the [current reviewed advisory, updated August 13](https://github.com/advisories/GHSA-5xrq-8626-4rwp) and npm audit require at least 3.2.6. Selected 3.2.7, staying in the smallest fixed major and above both thresholds. It supports existing Vite 6 and jsdom; the obsolete nested Vite 5 tree disappeared. No Vitest 5 update, override of incompatible transitive dependencies or audit fix --force was used.

Primary Vite/esbuild advisories: [optimized source-map traversal: fixed 6.4.2](https://github.com/vitejs/vite/security/advisories/GHSA-4w7w-66w2-5vf9), [Windows alternate paths: fixed 6.4.3](https://github.com/vitejs/vite/security/advisories/GHSA-fx2h-pf6j-xcff), [launch-editor UNC handling: Vite fixed 6.4.3](https://github.com/vitejs/launch-editor/security/advisories/GHSA-v6wh-96g9-6wx3), and [esbuild development-server CORS: fixed 0.25.0](https://github.com/evanw/esbuild/security/advisories/GHSA-67mh-4wv8-2f99).

Commands applied: npm install --save-dev vitest@~3.2.7 and npm install react-router-dom@~7.18.3. package-lock.json updated normally. After lockfile updates, both npm audit --json and npm audit --omit=dev --json report zero vulnerabilities of every severity. Saved evidence: audit-before.json, audit-after.json and audit-production-after.json under frontend/qa/pending-fixes/. No known fixable advisory remains in these current registry results. This is not a claim that the complete dependency tree has no undisclosed vulnerability.

## Confirmed API alignment

- Meeting URL: validate the exact trimmed submitted value at 2048 characters; 2049 disables confirmation and explains the limit. Pasting never silently truncates the link. Valid HTTPS syntax is checked locally; provider authorization remains with Spring. Unit cases cover 255/256/2048/2049, trim and invalid schemes. Browser case preserves date/link on HTTP 400 VALIDATION_ERROR and retries with the entire 2048-character value. Schema expansion remains backend/coordinator rollout ownership.
- Skills: INVALID_SKILL_NAME 400 and SKILL_IDENTITY_CONFLICT 409 produce clear messages stating the suggestion was not saved. Keep typed name and existing chips; allow a successful retry. Single-letter C is now suggestible, alongside C++ and C#. IDs/slugs remain server-owned; no client slug generation or identity rewriting was added.
- Inspection of UserSkillService confirmed SKILL_NOT_APPROVED when assigning pending vocabulary entries. The UI now explains this and blocks profile save while pending chips remain. Users can remove them and save approved skills; already submitted suggestions remain in review. The browser fixture test does not claim unapproved skills were successfully assigned.
- Frontend callback is /auth/callback; coordinator is aligning the backend default separately. No backend file was modified.

## Vercel preparation and public-origin contract

Added frontend/vercel.json. Select repository Root Directory frontend in Vercel, Vite framework and a compatible Node runtime (validated locally on Node 24.17.0; use Node 24.x for parity). Install uses npm ci, build uses node scripts/validate-hosted-env.mjs && npm run build, output is dist. The [official Vercel Vite guide](https://vercel.com/docs/frameworks/frontend/vite) documents the need for SPA rewrites for deep links. This configuration rewrites application paths, including /auth/callback and exchange/profile URLs, to index.html while excluding /api, /api/* and /assets/* from the HTML fallback.

Set public VITE_API_BASE_URL in each Vercel environment to the actual backend HTTPS origin plus /api. No fabricated hostname is present in deployment configuration. The build guard rejects missing/relative/http URLs and credentials/query/fragment; it does not print the supplied value. `.env.example` retains /api solely for local Vite development. VITE_API_PROXY_TARGET is a local development setting and does not create a production proxy. VITE_* values are embedded in the browser bundle and must contain no secrets.

Spring must allow the exact frontend origin through CORS (including Authorization/Content-Type preflight and supported methods), and redirect completed OAuth to that frontend origin's /auth/callback. The browser's Google entry link uses the configured backend /api/auth/google. Provider credentials and database/JWT secrets belong only to the backend. Coordinator owns hosted-origin selection, OAuth registration and environment configuration.

Local configuration tests verify rewrite matching and API exclusion. The executable guard returns 1 for absent/relative configuration and 0 for a synthetic HTTPS /api test URL; evidence is hosted-build-guard.json. No Vercel runtime or deployed CORS/Google flow was exercised. Those are explicit hosted validation items, not completed deployment.

## Actual validation and resolved failures

Commands ran from C:/Projects/match-skill/frontend.

| Check | Actual result |
|---|---|
| npm run lint | Final exit 0, zero errors/warnings |
| npm run build | Final exit 0, TypeScript + Vite 6.4.3, 1652 modules; emitted JS 268833 bytes |
| npm test | 4 files, 28 passing Vitest cases; existing 23 plus 5 URL boundary cases |
| node --test scripts/validate-hosted-env.test.mjs | 2 passing deployment configuration tests |
| npm run test:browser after router/helper/tab changes | 18 passed in 27.9s: original 14 journeys + 4 viewport reproduction cases |
| npx playwright test tests/pending-fixes.spec.ts after contract changes | 8 passed in 12.1s: URL boundary/retry, two skill error/retry cases, symbol/pending policy, four viewport cases |
| npx playwright test tests/design-integration.spec.ts -g 'registration refreshes\|scheduling uses\|failed skill lookup' after final component changes | 3 affected original journeys passed in 3.7s |
| npm audit and npm audit --omit=dev | Both zero findings after final dependency lock changes |

This covers 22 distinct owner browser scenarios across the full/focused runs, not one claimed 22-test run. Existing visual/axe checks at 320/375/768/1440 passed in the 18-test run. No green check was repeated without a relevant change. JSON for the 18-test run is visual-after/browser-results.json; the last 3-test focused run is affected-flow-final.json. The8-test result is retained in the execution transcript; its transient Playwright JSON was replaced by the subsequent focused run.

Resolved failure lines:

```text
Baseline viewport tests: Expected: >= 6; Received: 0 (tab spacing, all four sizes).
Intermediate lint: 'URL' is not defined (two Node scripts); 'summarise' is defined but never used.
Intermediate build: TS6133: 'summarise' is declared but its value is never read.
New skill test: Expected: 1; Received: 2 (chip count).
```

Imported Node URL explicitly and removed the unused helper import. The chip-count failure was a test scoping bug: a relative has locator matched both autocomplete fields; scope now identifies the first field. Actual error messages/query preservation passed even in that initial failing run. All final affected checks pass.

Install also reported the environment's esbuild postinstall approval policy; no policy was disabled. Existing optional platform binary built successfully. Playwright printed the harmless NO_COLOR/FORCE_COLOR environment warning; this is separate from the now-zero ESLint/Fast Refresh warnings.

## Handoff limits

Frontend is ready for the tester's independent PostgreSQL/Redis/Spring/browser validation. These new owner browser results use HTTP fixtures; they do not claim real new schema persistence, Redis Lua, provider OAuth or hosted Vercel/Render/Supabase coverage. Previously documented in-memory ranking limits, fixed-week DST overlap approximation and best-effort duplicate-request preflight were not changed by this follow-up. No deployments or production data operations occurred.
