---
status: accepted
---

# ADR-043: The authorization matrix is adopted narrowly, whitelist first

Every endpoint's authorization comes from one bound configuration record, the authorization matrix: role → list of
(method, path). It is applied in `authorizeHttpRequests` in a fixed order: **whitelist first, guards second,
`anyRequest().denyAll()` last**. The prescribed RBAC recipe registers its guards before its whitelist, so a maintainer
holding the recipe would swap the order back. That silently guards every whitelisted path that a guard pattern also
matches. The matrix is "narrow" because it maps roles to routes and nothing else: no privileges, no per-user grants.

## Context

- The governing standard's Decision Logic says the role-based authorization matrix controls authorization for each
  API endpoint and HTTP method per role, and §2 Happy Path step 1 says it is loaded before serving traffic. The PRD
  never mentions the mechanism; it scopes authorization to a USER/ADMIN check on admin endpoints.
- The RBAC recipe does not work as printed (R-STD-032):
  - it registers `url-guards` **before** the whitelist, under Spring Security's first-match-wins request matching;
  - its YAML (`- GET: /api/v1/users/**`) is a list of single-entry maps with dynamic keys, while its code calls
    `guard.getMethod()` and `guard.getPath()`, so it does not bind without a type and a converter it never defines;
  - it calls the wrong `requestMatchers` overload.
- IM8 ac-4 asks for a declared per-account permission baseline and periodic revocation of drift.
- The TOTP factor rules are composed onto the manager the matrix produces (ADR-026), not declared beside it.

## Decision

- **Shape.** A typed `@Validated @ConfigurationProperties` record with explicit `method:` and `path:` keys per entry.
  It binds once at context refresh; a restart is the only reload path (T-ADM-006).
- **Order.** Whitelist entries first, role guards second, `anyRequest().denyAll()` last. An authenticated request that
  matches no rule gets 403 `ACCESS_DENIED`, and an anonymous one gets 401 (T-ADM-007).
- **Rows worth naming, because each was once missing or ambiguous:**

  | Path | Rule |
  | --- | --- |
  | `/api/admin/roles/**`, all methods | explicit `denyAll()`, placed **before** the `/api/admin/**` guards |
  | `/api/admin/**` | `ROLE_ADMIN`, plus the TOTP factor rule (ADR-021, ADR-026) |
  | `/api/mfa/**` | `ROLE_ADMIN`, no factor, deliberately outside `/api/admin/**` so an unenrolled admin can enrol |
  | `GET /api/profile` | any authenticated user, password only |
  | `GET /api/hello` | `ROLE_USER`, no factor |

  - The role-definition row exists so that both a USER and an ADMIN get a literal 403 there, rather than the ADMIN
    matching the admin guard and getting a 404 or 405 from MVC. It also survives a future `/api/admin/**` wildcard
    handler (T-ADM-019, T-ADM-020).
  - `GET /api/hello` failed closed before it had a row, because of the terminal `denyAll()`. The row makes the
    decision explicit. It is **not** on the forced-change allowlist.
- **Defence in depth for the role, and only for the role.** `@PreAuthorize` on the service methods repeats the role
  check, and a test asserts that for every matrix entry the chain decision and the annotation agree (T-ADM-012). The
  factor gate has one layer, the request matchers, because the factor is not repeated in method security. So a
  mis-written admin matcher would remove the factor with nothing behind it. The compensating control is a test
  **enumerated from the bound matrix**, not hand-written, over every admin route and method (T-MFA-002).
- **Matching.** `PathPatternRequestMatcher` only, Spring Security 7's default. `AntPathRequestMatcher` is not
  reintroduced.
- **The matrix is IM8 ac-4's declared baseline.** The scheduled compare-and-revoke half is deferred with its
  justification (R-ADM-001).

## Considered options

- **The recipe as printed.** Rejected: guard-before-whitelist ordering, and it does not bind or compile.
- **Defer the matrix and use `@PreAuthorize` only**, as the PRD's scope would allow. Rejected. The standard's Decision
  Logic makes the matrix the control, and annotations alone give no single inspectable baseline for ac-4. Nothing
  would stop a new endpoint shipping unannotated, while the terminal `denyAll()` fails it closed.
- **Privileges per role** (the recipe's `USERS_READ`, `USERS_UPDATE` and similar). Rejected with ADR-042: with two
  roles there is nothing for them to express.
- **Hot reload of the matrix.** Rejected. A mapping change at runtime is an authorization change with no deploy
  record. The standard's §5 test only requires the change to take effect on the next load.

## Consequences

- Every new endpoint needs a matrix row, or it is denied. That is intended; a forgotten row fails closed and is found
  in the first test that calls it.
- The ordering is invisible in the running system. T-ADM-012 and T-ADM-007 are what make it visible.
- IM8 ac-4 is graded partial, not fail: the baseline is declared, and scheduled revocation is not built (R-ADM-001).

## Sources

- Standalone User Access Control Application Standard §2 Happy Path step 1 and Decision Logic; §5 Role and
  Authorization Tests (duplicate definitions, role-definition mutation refused, mapping change on next load).
- `Common_Role-Based_Access_Control_Configuration.md` (the RBAC recipe), as printed.
- IM8 ac-4.
- Spring Security 7.1.x reference, "Authorize HttpServletRequests" (first-match ordering, `PathPatternRequestMatcher`).
