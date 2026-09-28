---
status: accepted
---

# ADR-026: Factor rules are hand-composed per matcher, role first, not `@EnableMultiFactorAuthentication`

The two admin factor rules are built by hand for each request matcher: the role check first, then the factor check,
joined with the two-argument `AuthorizationManagers.allOf` whose all-abstain default is **deny**. We do not use
`@EnableMultiFactorAuthentication`, `AuthorizationManagerFactories.multiFactor()` or `requireFactors(...)`, which
are Spring Security 7's documented idiom. A maintainer tidying the configuration would reach for them. Each one is
wrong here for a reason visible only in the framework source.

## Context

From the Spring Security 7.1.x source:

- **`@EnableMultiFactorAuthentication` is global.** With a non-empty `authorities()`, it registers one
  `DefaultAuthorizationManagerFactory` bean whose additional authorization is ANDed into every `hasRole`,
  `hasAuthority` and `authenticated` rule, in web **and** method security. One bean cannot hold two different
  `validDuration`s, and it would demand the factor of regular users, who have none (ADR-023).
- **The factory puts the factor check first.** `DefaultAuthorizationManagerFactory` composes
  `AuthorizationManagers.allOf(new AuthorizationDecision(false), additionalAuthorization, manager)`, and `allOf`
  returns the first non-granted result and stops. So the role check never runs when the factor is missing:
  - a logged-in `USER` calling `/api/admin/**` gets a missing-factor answer, which tells them to enrol MFA for a
    surface they must never reach. That is a small state disclosure of the kind ASVS 5.0 6.3.8 (L3) exists to
    catch;
  - `AllRequiredFactorsAuthorizationManager` treats an unauthenticated principal as holding no authorities, so an
    **anonymous** request also gets the missing-factor answer (412) instead of 401.
- **`requireFactors(String...)` carries no `validDuration`.** It maps each string to an authority-only
  `RequiredFactor`. Only `requireFactor(Consumer<RequiredFactor.Builder>)` reaches the duration.
- **The one-argument `allOf(managers...)` is fail-open.** It delegates with a default of
  `new AuthorizationDecision(true)` when every manager abstains.
- **The factory's javadoc says the additional authorization "does not affect `anonymous`".** The 7.1.x source
  routes `anonymous()` through the same composition, so it does.

## Decision

- **One builder produces both admin rules:** `AuthorityAuthorizationManager.hasRole("ADMIN")` first, then an
  `AllRequiredFactorsAuthorizationManager` requiring `FACTOR_PASSWORD` and `FACTOR_TOTP` with the rule's duration,
  joined by `AuthorizationManagers.allOf(new AuthorizationDecision(false), role, factors)`. So a non-admin gets
  403, anonymous gets 401, and all-abstain denies.
- **Two matchers:** `GET /api/admin/**` with the session-lifetime duration, and every other method on
  `/api/admin/**` with 10 minutes (ADR-021). Matchers are method-aware and `PathPatternRequestMatcher` only, reads
  before writes, first match wins. The rules are applied to the manager that the authorization matrix produces, so
  the matrix stays the single source for role-to-path mapping (ADR-043).
- **The factor manager's `Clock` is set** to the same bean the TOTP counter uses.
- **No `RoleHierarchy`.** A hand-built `AuthorityAuthorizationManager` does not pick up a hierarchy bean, and with
  two roles there is nothing to arrange. An assertion that no `RoleHierarchy` bean exists makes a future hierarchy
  fail loudly instead of being silently ignored by these rules.
- **A missing `FACTOR_TOTP` gets its own entry point**, registered with `defaultDeniedHandlerForMissingAuthority`,
  because only the password and one-time-token factors get one automatically. Without it the denial is a bare 403.
- **Negative assertion:** no rule, web or method, `anonymous()` included, is produced by a factory that carries
  additional authorization, and no such factory bean exists.

## Considered options

- **`@EnableMultiFactorAuthentication`.** Rejected: global, single-duration, and it gates regular users.
- **`AuthorizationManagerFactories.multiFactor()` per rule.** Rejected: factor before role, which breaks 403 for
  non-admins and 401 for anonymous.
- **A method-level `@RequiresFactor` annotation as a second layer.** Considered and rejected. It rebuilds the
  global-bean problem, and a second hand-kept list of which methods need which duration is a second thing to get
  wrong.
- **Hand-composed, role-first rules (chosen).**

## Consequences

- **The factor gate has one layer; the role gate has two.** The role is enforced by the request matchers and again
  by `@PreAuthorize` on the service methods. The factor requirement exists only in the matchers, including the
  GET-versus-mutation split that separates the session-lifetime guard from the 10-minute rule. A mis-written matcher
  silently weakens or removes the factor, with nothing behind it. Recorded as a residual (R-MFA-003), and covered by
  a test enumerated from the authorization matrix rather than hand-written, so a route added later is covered
  automatically.
- `HEAD /api/admin/users` does not match a GET-scoped matcher, so it falls through to the 10-minute rule. It fails
  closed. `OPTIONS` never reaches the chain, because CORS is handled first.
- Tests: T-MFA-002 (the factor matrix, enumerated from the bound matrix), T-MFA-018 (no factor-bearing factory),
  T-ADM-012 (the matrix chain and `@PreAuthorize` agree), T-CFG-018 (both rule sets non-empty at startup).

## Sources

- Spring Security 7.1.x source: `EnableMultiFactorAuthentication`, `MultiFactorAuthenticationSelector`,
  `AuthorizationManagerFactoryConfiguration`, `DefaultAuthorizationManagerFactory` (`withAdditionalAuthorization`,
  `anonymous()`), `AuthorizationManagerFactories` (`requireFactors`, `requireFactor`), `AuthorizationManagers`
  (`allOf`), `AllRequiredFactorsAuthorizationManager`, `ExceptionHandlingConfigurer`
  (`defaultDeniedHandlerForMissingAuthority`).
- Spring Security reference, Multi-Factor Authentication (servlet).
- OWASP ASVS 5.0, 6.3.8 (L3).
- IM8 ac-2.
