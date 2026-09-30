# Review notes

The `/do-work` validation gate normally runs `/code-reviewer` (build, semgrep, IM8, React and
Spring review skills) in subagents with shell access. This codebase was authored in a session
without shell access, so the review checklists of `spring-security-review`,
`spring-web-review` and `react-review` were applied by reading the code instead, and the
tool-driven checks (build, Semgrep, mutation testing) are listed below as **not run**.

## Spring Security review (checklist applied by hand)

| Check | Result |
| --- | --- |
| No `WebSecurityConfigurerAdapter`; single `@EnableWebSecurity`; lambda DSL; explicit `authorizeHttpRequests` | Pass |
| `@EnableMethodSecurity` present; `@PreAuthorize` on admin controller; no self-invocation | Pass |
| Session fixation (`changeSessionId`), HttpOnly/Secure/SameSite cookie, server-side timeout, `HttpSessionEventPublisher` | Pass (`Secure` is off only in `application-dev.yaml`; SameSite is `Lax`, see ADR 0001 for when `None` is needed) |
| CSRF not disabled; CORS centralised, no wildcard | Pass |
| CSP, Referrer-Policy, Permissions-Policy, HSTS defaults | Pass (CSP scoped to `/api/**` so Swagger UI works in dev) |
| BCrypt; no hardcoded users | Pass — the seeded admin is driven by configuration and only `application-dev.yaml` carries a literal password |
| Seeded data profile-gated | **Deviation, accepted**: the checklist wants seeders under `@Profile("dev")`, but PRD story 12 requires first-start seeding in every environment. Mitigation: refuses to start without explicit credentials, password policy applies, idempotent. |
| `requiresChannel` HTTPS enforcement | Not configured; TLS termination in front of the app is the documented assumption (PRD "Transport"). |
| Filters not double-registered | `CorrelationIdFilter` is a plain servlet filter bean (intended); security handlers are components but not filters. |
| `SpringAuthorizationEventPublisher` | Not wired; authorisation denials are visible as 403 audit-free responses. Could be added if central denial logging is wanted. |

## Spring Web review

| Check | Result |
| --- | --- |
| DTOs over entities, `@RestController`, verb-specific mappings, stateless controllers, explicit media types | Pass (ArchUnit enforces the entity rule) |
| Declarative validation with `@Valid` on `@RequestBody` | Pass |
| Global `@RestControllerAdvice` with structured errors and correct status codes | Pass (`ProblemDetail`) |
| Web-layer tests | Integration tests use `@SpringBootTest` + MockMvc through the full filter chain on purpose: the behaviour under test (cookies, CSRF, session rotation) lives in the filters, which `@WebMvcTest` would slice away. |
| API versioning | Not applied (`/api/...`); out of scope for the PRD. |

## React review

| Check | Result |
| --- | --- |
| No side effects in render; effects have cleanup; no async effect functions | Pass (`ConfirmDialog` effect only syncs the native dialog) |
| Immutable state updates; stable keys (`user.id`) | Pass |
| Derived state computed in render, not effects (`localProblems`, `confirmMismatch`) | Pass |
| Context discipline | One small auth context; server data goes through TanStack Query, not context |
| `items.length && ...` footgun | Not present |
| Data fetching | TanStack Query for reads; mutations invalidate the users query |

## Verification run (2026-09-30, after authoring)

- `mvnw test`: 78 tests, 0 failures (JDK 26, Spring Boot 4.0.6). Fixes needed on first run: `IndexResolver` only exposes `resolveIndexesFor`; `IpLoginThrottle` needed `@Autowired` on its injection constructor because of the test-only constructor; the ArchUnit package pattern `..hello..` matched the root package; and the seeded admin passwords contained the username, which the policy rightly rejects.
- Frontend: `npm run lint` clean, `npm run typecheck` clean, `vitest run` 24 tests passing.

## Still not run

- Semgrep, dependency vulnerability scan, mutation testing.
- `git commit` / `git push` — nothing has been committed.
