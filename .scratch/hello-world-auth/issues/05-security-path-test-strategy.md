# Security-path test strategy

Type: grilling
Status: resolved
Blocked by: 01, 03

## Question

Choose the test approach for the PRD's required integration coverage (login success/failure/generic-error, lockout trigger + cooldown reset, IP throttling independent of lockout, post-logout cookie replay rejection, reset-token single-use/expiry/session-invalidation, admin self-action guard, USER→403 on `/api/admin/**`):

- **Harness:** `@SpringBootTest` + MockMvc vs full-server `WebTestClient`/`TestRestTemplate` — which suits session-cookie assertions better?
- **Time control:** injectable `Clock` bean for `locked_until` and token `expires_at` vs sleeping vs direct DB manipulation.
- **IP simulation:** how tests present distinct source IPs for the throttle tests — depends on ticket 03's mechanism (filter `remoteAddr`, `X-Forwarded-For`, etc.).
- **Fixtures:** how users/tokens get seeded per test; H2 for tests vs Testcontainers (Postgres) — is H2-enough acceptable for a demo, or test on the prod-shaped DB?
- **CSRF in tests:** whether tests run with CSRF enabled (preferred — tests the real path) and how they fetch tokens — depends on ticket 01.
- **Scope:** the PRD minimum list above, or broader?

## Answer

Decided:

- **Harness:** `MockMvc` + `@SpringBootTest`. Source-IP variation via `.remoteAddress(...)` (per ticket 03, Spring 6.0.10+); cookie assertions standard.
- **Test DB:** H2 — same engine as dev, incl. Spring Session JDBC tables; no Testcontainers.
- **CSRF in tests:** enabled, real path — tests obtain a token via `GET /api/auth/csrf` and send `X-XSRF-TOKEN` exactly as the SPA does (per ticket 01).
- **Time control:** injectable `Clock` bean (per ticket 03) — tests manipulate `locked_until` / token `expires_at` by controlling the clock, not by sleeping.
- **Fixtures:** seed users/reset tokens via repositories in `@BeforeEach`-style setup.
- **Scope:** broad suite — the PRD's required security list plus near-full endpoint coverage including edge cases.
