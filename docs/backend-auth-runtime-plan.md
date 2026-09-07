# Backend authentication and rate-limit runtime plan

Date: 2026-09-05. Owner: backend authentication/runtime worker.

## Objective and evidence

Implement the authentication/runtime portion of `backend-modernization-plan.md`.
`SecurityConfig` injects `GoogleOAuth2SuccessHandler` in its constructor; the
handler requires `AuthService`, which requires the password encoder declared by
that same configuration. This creates a bean construction cycle. The default
Google client ID is blank in `application.yml`; Spring Boot 3.3.4's
`OAuth2ClientProperties.validateRegistration` rejects it before authentication
configuration can finish. `oauth2Login` is currently unconditional and supplies
HTML login behavior for unauthenticated API requests.

Additional verified evidence: Spring Security's default authorization resolver
matches `/auth/{registrationId}` for the configured base URI `/auth`, including
`/auth/login`, `/auth/register`, and `/auth/me`. With Google enabled these local
routes can be intercepted as nonexistent OAuth client registrations. Restrict
OAuth authorization resolution to `GET /auth/google` and test local authentication
with actual servlet/context paths in every Google configuration mode.

`RateLimiterService` already executes an atomic Redis script and fails closed
with 503. Its tests still mock the earlier separate increment/expire operations
and expect fail-open behavior. Interceptor tests still trust an untrusted
`X-Forwarded-For` header. Exchange creation is selected by the raw servlet path,
while authentication routes already use the resolved controller method.

## Scope and ownership

Own `SecurityConfig.java`, `AuthService.java` (login transaction annotation only),
`RateLimitInterceptor.java`, their assigned rate-limit tests, and new security
configuration tests. Parent additionally authorized `application.yml` changes
to exclude eager OAuth auto-configuration while preserving its existing property
namespace and environment variables. Own this plan. Do not edit OAuth identity linking, frontend,
another worker's files, or the parent's modernization plan/report. Any adjustment
to application OAuth configuration requires coordination with the backend parent.

## Ordered implementation

1. Break configuration construction dependencies using bean-method injection.
2. Make Google optional without placeholder credentials; preserve configured
   Google authorization/callback URLs, scopes, and environment variables.
3. Return `ApiError` JSON with `UNAUTHORIZED` / `FORBIDDEN` for security failures.
4. Mark local login's service transaction read-only.
5. Resolve exchange creation using its mapped `HandlerMethod`; preserve existing
   rate budgets, fixed windows, user/IP keys, 429 headers, and 503 behavior.
6. Replace stale mocks with tests of atomic-script invocation, inclusive rate
   limits, storage failures, mapped routes, trusted identity, and ignored headers.
7. Verify real security context startup and HTTP behavior for local-only and
   configured Google modes. Coordinate Maven execution to avoid concurrent builds.

## Compatibility, dependencies, and risks

Keep bearer JWT tokens, `{token}` authentication responses, all existing API
routes, and the Google callback token redirect. No refresh/cookie/account-linking
redesign. Missing bearer credentials intentionally receive JSON 401 rather than
HTML login redirects. Blank Google configuration must leave local login usable;
partial configuration should not create a broken Google registration.

Parent owns web exception handling and adds H2 test support. Tests must avoid live
Google/Redis/database dependencies. Mocked Redis execution validates invocation
and error policy, not actual Lua atomicity. OAuth tests can validate authorization
redirect/callback failure routing without making a real provider token exchange.

## Acceptance and actual validation

Acceptance: no bean cycle with real authentication service/encoder/handler; blank
Google properties permit context startup; configured Google authorization still
redirects with the existing callback; unauthenticated and forbidden HTTP requests
return stable JSON; valid bearer requests work; rate-limit tests match production.
Parent runs the final backend build and complete suite.
First parent-run focused suite: security slices failed to start because slice
tests do not discover configuration-properties records automatically:
`Parameter 1 of constructor in com.matchskill.backend.security.RateLimitInterceptor required a bean of type 'com.matchskill.backend.config.RateLimitProperties' that could not be found.`
Fix by explicitly enabling the four required property records in test support.

Final worker verification executed from `C:\Projects\match-skill\backend`:

```text
./mvnw.cmd -B '-Dtest=*SecurityConfigurationTest,RateLimitInterceptorTest,RateLimiterServiceTest,AuthServiceTest' test
[INFO] Tests run: 48, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Log: `%TEMP%\matchskill-backend-auth-runtime.log`. Security slices cover 19 tests:
blank Google credentials (6), configured credentials (7), and incomplete credentials
(6). They instantiate the real authentication service, password encoder, JWT
service/filter, and OAuth success handler. Local login and authenticated profile
reads use actual `/api` context and servlet paths in all three modes. Google
authorization retains its callback URI and scopes; callback failures return JSON.
Filter exception handling returns JSON 401/403. Existing authentication unit tests
(11), interceptor tests (8), and Redis service unit tests (10) also pass.

No live Google token exchange, Redis instance, or database was used by this focused
run. Redis's existing Lua script was preserved; mocks validate inclusive budgets,
single-script invocation, invalid configuration handling, and fail-closed 503.
Full application/database integration and executable-jar verification remain with
the parent agent. No frontend files or OAuth identity linking were edited.
