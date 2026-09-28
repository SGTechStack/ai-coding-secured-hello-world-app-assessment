# 23 — Decide the TOTP enrolment, step-up, and factor-reset flows

Type: grilling
Status: resolved
Blocked by: 06, 08, 22

## Question

Now that the enforcement shape is chosen, what are the exact endpoints, states, and configuration that
make it work?

[Resolve the MFA scope conflict raised by IM8 ac-2](19-mfa-scope-conflict.md) decided *what* we build
and *why*. This ticket decides the surface. It is deliberately not merged into ticket 19: that ticket
resolved a scope conflict against IM8, this one is ordinary feature design downstream of it.

## Settled going in, not up for re-litigation

- TOTP only. No PIN, no OTP, no recovery codes.
- `MFA_Core` prescriptions retained as-is: 20-byte `SecureRandom` secret, `otpauth://` QR,
  `PENDING_TOTP` → `TOTP_USER_DETAILS` promotion inside one transaction, RFC 6238 HMAC-SHA1 6 digits,
  ±1 window, constant-time compare, `lastUsedCounter` replay rejection.
- `MFA_Critical_Transaction` is not followed. Enforcement is two
  `AuthorizationManagerFactory` rules: admin reads require `FACTOR_PASSWORD` + `FACTOR_TOTP` unbounded,
  admin mutations require `FACTOR_TOTP` with `validDuration(10 minutes)`.
- Enrolment is admin-only; `MFAPrompt` is not built.
- Challenge timing is eager — the SPA routes an admin to the challenge straight after password login.
- Secret encrypted with AES-GCM under an environment-supplied key, `AesGcmBytesEncryptor
  .withSecretKey(...)`, with a key-version column. `Encryptors.stronger()` and `AesBytesEncryptor` are
  prohibited (CVE-2026-47842).
- Lost authenticator is handled by admin-resets-admin plus a two-enrolled-admins invariant.

## What to decide

**Endpoints.** Exact paths, methods, request and response shapes, and guards for:

- QR provisioning. Keep the corpus's `GET /mfa/generateTotpQrCode` returning PNG bytes, or move to our
  own path and envelope? Note the PNG-bytes response is the one endpoint that does not fit ticket 06's
  `ProblemDetail`-shaped world on the error path. **Constraint from
  [ticket 20](20-deployment-origin-topology.md):** the document CSP allows `img-src 'self' blob:` and
  deliberately **not** `data:`, so the QR must stay bytes-turned-object-URL. Switching to a base64
  `data:` URI is not a free choice — it requires widening a CSP directive, and Vite's own guidance is
  that `data:` must never be allowed in `script-src`, which is why we set `assetsInlineLimit: 0` to keep
  `data:` out of the policy entirely. If you do want `data:`, say so here and ticket 14's policy changes
  with it.
- Setup confirmation. The corpus contradicts itself over whether the first code arrives in the `X-TOTP`
  header or a `{ code }` body — pick one, given we abandoned the header pattern everywhere else.
- **Factor verification.** New surface with no prescription: the endpoint the SPA posts a code to in
  order to have `FACTOR_TOTP` granted to its session. Decide path, body, success response, and whether
  it is reachable before enrolment completes.
- **Factor state.** The endpoint the eager challenge reads to learn which factors the session holds and
  which it needs. Decide whether this is its own endpoint or a field on the self-read endpoint from
  ticket 11 — the SPA already calls that on load, so a second round trip may be avoidable.
- **Admin factor reset.** ADMIN-only, audited, behind the factor itself. Decide whether it clears the
  confirmed row only, or also any stale `PENDING_TOTP`, and what the target user sees next.

**Spring Security configuration.** Which of `@EnableMultiFactorAuthentication`, an explicit
`AuthorizationManagerFactory` bean, or `setAdditionalAuthorization` expresses our two rules most
legibly. The global annotation is the wrong tool here — it would demand the factor everywhere, including
of regular users who never enrol — so the likely answer is explicit per-matcher rules, but decide it and
write down why. Also decide how the custom TOTP `AuthenticationProvider` is registered and what it
returns.

**Session interaction.** Ticket 08 owns the session contract; this ticket owns the MFA-specific parts:

- Does granting the TOTP factor into a live session require `changeSessionId()`? It is an
  authentication event, which argues yes; it is not a privilege escalation across principals, which
  argues no.
- Factor authorities carry timestamps and must survive Spring Session JDBC serialisation and
  deserialisation intact, or `validDuration` silently misbehaves. Decide how this is verified.
- What the absolute-session-lifetime decision from ticket 05 does to a held factor.

**The two-admin invariant.** Where it is enforced. Candidates: at factor reset, at admin disable, at
admin delete, at role demotion — or one central guard covering all four, which is the same shape as
ticket 11's self-action guard and should probably be the same mechanism.

**Failure and lockout reconciliation.** `MFA_Core` mandates persisted per-principal per-factor failure
tracking, 10 failures in a 1-hour window, session kick-out, and lock until administrative review.
Ticket 09 decides the account lockout and dual rate limiting. Decide whether the factor counter is a
third independent limiter or folds into one of the two, and what "locked until administrative review"
means when the locked account may be one of only two admins.

**Enrolment timing for the bootstrap admin.** The seeded admin cannot reach the admin surface before
enrolling. Confirm that is acceptable as the intended first-run experience and that nothing in ticket
11's bootstrap needs the admin surface to seed itself.

## Done when

Every endpoint is specified with its guard and response, the Spring Security configuration is written
down as code an implementer can copy, the session interaction questions are answered, and the factor
lockout has one owner rather than two.

---

## Amendment from ticket 09 (lockout and dual rate limiting)

**Three cross-authenticator rules decided, one line of code you must not miss, and one NIST gap handed to you.**

Decided in ticket 09:

1. A failed TOTP code **never** increments the password lockout counter, and a failed password never increments
   the factor counter. They are different authenticators under incompatible policies — 5 failures / 20-minute
   auto-lift for the password, `MFA_Core`'s 10-in-a-1-hour-window locked until administrative review for the
   factor. Merging them would impose the stricter policy on the looser axis by accident.
2. Neither counter is reset by the other's success.
3. The per-IP throttle on the TOTP challenge route is **20/min**, in ticket 09's budget table, matching
   `MFA_Core`'s design-choice figure. The **per-user factor failure counter and its lock semantics remain
   yours** — ticket 09 owns the axis, not the factor.

**The line of code.** Ticket 09 resets the password failure counter from `InteractiveAuthenticationSuccessEvent`
rather than `AuthenticationSuccessEvent`, because the latter fires from `ProviderManager` before
`sessionStrategy.onAuthentication` runs. **But the interactive event does not separate the two authenticators
by itself**: if your TOTP step is a second `AbstractAuthenticationProcessingFilter`, it publishes the *same
event type*, so a successful factor step would reset the password failure counter — which is exactly the hole
rule 2 claims to close. **The listener must discriminate on the `Authentication` type or the source filter, not
on the event type.** Without it the rule reads as though it works and does not.

**The gap that is now yours, named rather than left implicit.** NIST SP 800-63B-4 §3.2.2 requires that where
more than one authenticator is implicated in an excessive number of failed attempts, **both** be disabled. Our
two axes are deliberately independent (rule 1), so we do not implement that. Related and live rather than
theoretical now that admin MFA is in scope: §3.2.2's reset-on-success `SHOULD` carries an **AAL constraint** —
a reset must not raise an authenticator above the AAL of the session performing it, so a password-only session
must not clear a factor's failure count.

---

## Answer

**Three endpoints, three authentication pathways, two filters, two authorization rules composed
role-first, and a two-tier factor lockout.** Enrolment is `POST /api/mfa/totp/enrolment` returning JSON
carrying the QR *and* the Base32 secret; confirmation and step-up are two `AbstractAuthenticationProcessingFilter`
instances sharing one provider, one session strategy and one pair of handlers; the factor rules are built
by hand with `hasRole` evaluated **before** the factor check; and the factor lockout is 10 consecutive
failures / 20-minute auto-lift on top of a monotonic 100-failure cumulative cap cleared only by admin reset.

Research asset: [`research/spring-security-7.1.x-mfa-api-verification.md`](../research/spring-security-7.1.x-mfa-api-verification.md)
— every Spring Security claim below is cited to 7.1.x branch source there.

### 1. The five endpoints

`api.base-path` is `/api` (ticket 08's `/api/v1` is the stale outlier, per ticket 11).

| Endpoint | Method | Guard | Request | Success | Failure |
|---|---|---|---|---|---|
| `POST /api/mfa/totp/enrolment` | POST | `ROLE_ADMIN`, **no factor**; *not* on the forced-change allowlist | none | `200` `application/json` `{ otpauthUri, secretBase32, qrPng }`, `Cache-Control: no-store` | `409 FACTOR_ALREADY_ENROLLED` |
| `POST /api/mfa/totp/enrolment/confirmation` | POST | `ROLE_ADMIN`, no factor; precondition in the filter | `{ "code": "123456" }` | `204`, **factor granted** | `412 INVALID_FACTOR`; `422 FACTOR_ENROLMENT_REQUIRED`; `429` |
| `POST /api/mfa/totp/verification` | POST | `ROLE_ADMIN`, no factor; precondition in the filter | `{ "code": "123456" }` | `204`, **factor granted** | `412 INVALID_FACTOR`; `422 FACTOR_ENROLMENT_REQUIRED`; `429` |
| `GET /api/profile` | GET | password only (ticket 11) | — | `factors: { held, required, enrolled }` | — |
| `DELETE /api/admin/users/{uuid}/totp` | DELETE | `ROLE_ADMIN` + `FACTOR_TOTP` ≤10 min + `AdminActionGuard` (ticket 11) | — | `204` | — |

**No new factor-state endpoint.** Ticket 06 put it on `GET /api/profile` and the SPA already calls that on
load, so the eager challenge costs no extra round trip. Lock state never ships (ticket 06 Q23a).

**`/api/mfa/**` sits deliberately outside `/api/admin/**`** so an unenrolled admin can reach it, and
deliberately *off* ticket 11's five-path forced-change allowlist, so the fresh-deploy order is
seed → login → change password → enrol → factor granted → `/api/admin/**` opens.

#### Why the provisioning response is JSON and not PNG bytes

The corpus prescribes `200 OK` with PNG bytes and nothing else (STD §2.2 step 5, §3.1 table). Ticket 22
found that leaves **no manual-entry fallback** — `alt="QR Code"` is the only text alternative and the
`otpauth://` URI is never exposed — so an admin who cannot scan a QR cannot enrol at all. On an
admin-only factor that is a functional blocker, not a conformance detail.

So one JSON response carries all three: `otpauthUri`, `secretBase32`, and `qrPng` as base64. The SPA does
`atob` → `Uint8Array` → `new Blob([...], {type:'image/png'})` → `URL.createObjectURL`, which yields a
**`blob:` URL** and is therefore already permitted by ticket 20's `img-src 'self' blob:`. **The CSP does
not change and `data:` stays out of the policy entirely**, which was ticket 20's constraint on this ticket.

It also collapses ticket 06's two-media-type problem: the endpoint no longer returns `image/png` on success
and `application/problem+json` on error, so `ProblemDetailWriter` needs no carve-out and the SPA needs no
per-route `Accept` handling (ticket 06 §10's Jackson trap).

Rejected alternative worth recording because it is genuinely attractive: return `{ otpauthUri, secretBase32 }`
only and render the QR client-side as inline SVG. That deletes zxing, deletes object URLs entirely and with
them the React 19 StrictMode revoke bug ticket 22 found at FE-RCP L543–547, and needs no `blob:`. It lost on
one ground: it moves a prescribed server responsibility to the client on the MFA happy path, which is the most
visible place on this map to deviate. If ticket 14 finds the Blob reconstruction fights Base UI, this is the
fallback and it costs one ADR, not a redesign.

**The secret is shown exactly once and can never be re-fetched** (§3.1's plaintext-never-stored rule,
RCP L427). So: `Cache-Control: no-store`, never logged, and — see §9 — the `otpauthUri` is as sensitive as
the Base32 string because it embeds it.

#### Method, write ordering, and the re-provisioning guard

**POST, not the prescribed GET.** STD L38 mandates a `GET` whose step 3 performs a create-or-update write.
Under Spring Security a `GET` is in the CSRF safe-method allowlist, is prefetchable, and is the one MFA call
that would trigger no CORS preflight. A state-mutating GET is a defect, not a style question. Recorded as a
deviation from STD L38. *Consolidated into the register (ticket 33): R-MFA-010. Amend the table by ID, not this list.*

**QR generation happens inside the transaction; any failure rolls the pending row back.** The two reachable
states are therefore "no pending secret" or "pending secret the admin has seen" — never "pending secret
nobody can enrol against". `PENDING_TOTP.TOTP_KEY` is `NOT NULL` and holds ciphertext, so the row cannot
precede encryption; that is the only ordering constraint the corpus imposes (RCP L382, RCP L427).

**Guard:** reject with `409 FACTOR_ALREADY_ENROLLED` when a confirmed `TOTP_USER_DETAILS` row exists for the
caller. STD §3.4's setup self-service guard forbids overwriting an existing key without admin removal, but
Recipe 10's `findByUsername(...).orElse(builder...)` then `save` **silently overwrites a confirmed row**
(RCP L669–674) and no guard exists anywhere in the corpus. A disabled button is not a control.

**An existing `PENDING_TOTP` row is overwritten freely.** An unconfirmed secret has no standing, and forcing
an admin reset to retry a failed scan would be absurd. **Carve-out, stated positively:** provisioning writes
`PENDING_TOTP` only and **never touches `failed_attempts`, `last_failed_at`, `locked_until` or the cumulative
counter on `TOTP_USER_DETAILS`.** Without that sentence, re-provisioning launders a lockout. NIST SP 800-63B-4
§3.1.3.2 states this as a `SHALL NOT` — "Generating a new authentication secret SHALL NOT reset the failed
authentication count" — but only for out-of-band verifiers, so we take it by **analogy, not citation**.

Ticket 22's resolution of the frontend contradiction stands: §5 wins over §3.2, the Generate button is
disabled when a key exists, and the `"Regenerate QR Code"` label and its "the old TOTP can no longer be used"
warning are unreachable and **must not be carried**.

#### Where the confirmation code arrives

The corpus prescribes **three mutually incompatible transports**: `X-TOTP` header (STD L48 and RCP L705, both
normative), JSON `{ code }` (FE-STD L341), and a controller that takes neither and reads `OTPRequestContext`
(RCP L698–702). We abandoned the header pattern everywhere else, so: **JSON `{ "code": "123456" }`**, and
dropping `X-TOTP` is a deviation from two normative statements. *Consolidated into the register (ticket 33): R-MFA-011. Amend the table by ID, not this list.*

FE-STD's `200` carrying a **boolean** is dropped outright — `false` as a failure channel is a second error
path beside ticket 06's envelope. Success is `204`; every failure is a `ProblemDetail`.

#### Successful confirmation grants the factor

The corpus implies otherwise: STD §2.5 initialises `lastUsedCounter` to the matched counter specifically so
the confirmation code "cannot be immediately replayed as an authentication attempt". But confirmation *is* a
possession proof of the freshly-enrolled secret, verified by the same constant-time comparison against the
same ±1 windows, and the replay concern is already closed by burning the counter — which our grant does too.
Demanding a second code seconds later would add a step with **zero assurance gain**, and it is the first thing
the seeded admin would meet on a fresh deploy.

So confirmation grants `FACTOR_TOTP`, and the SPA then re-fetches `/api/csrf` (ticket 08's contract) and
`/api/profile`. The consequence, which is why this is a decision and not a convenience: **confirmation is an
authentication event and therefore must be the same mechanism as verification**, not a plain controller.

### 2. Spring Security configuration

#### The two authorization rules, composed role-first

`@EnableMultiFactorAuthentication` is **unusable here**, from source: `MultiFactorAuthenticationSelector`
imports `AuthorizationManagerFactoryConfiguration` when `authorities().length > 0`, which registers a single
**global** `DefaultAuthorizationManagerFactory` whose additional authorization is ANDed into every
`hasRole`/`hasAuthority`/`authenticated` rule in both web and method security. One bean cannot hold two
different `validDuration`s, and it would demand the factor of regular users — out of scope per ticket 19.

`AuthorizationManagerFactories.multiFactor()` is also rejected, and the reason is the sharpest finding on this
ticket. `DefaultAuthorizationManagerFactory.withAdditionalAuthorization` is:

```java
return AuthorizationManagers.allOf(new AuthorizationDecision(false), this.additionalAuthorization, manager);
```

and `AuthorizationManagers.allOf` returns the **first** non-granted result and stops. So the factor check runs
**before** `hasRole` and the role check never executes. Two live consequences:

- An ordinary logged-in `USER` hitting `/api/admin/**` fails the factor check first, producing a
  `FactorAuthorizationDecision` → our missing-factor entry point → **an instruction to enrol MFA for a surface
  they must never reach**. Wrong answer and a small state disclosure, of exactly the class ASVS **6.3.8 (L3)**
  exists to catch.
- `AllRequiredFactorsAuthorizationManager.getFactorGrantedAuthorities` returns an **empty list** for an
  unauthenticated request, so every factor reads as missing and an **anonymous** request also gets **412
  instead of 401**.

So the rules are built by hand, role first, with the **two-argument** `allOf` because the one-argument form
defaults to `new AuthorizationDecision(true)` on all-abstain and is fail-open:

```java
private static AuthorizationManager<RequestAuthorizationContext> adminRule(
        @Nullable Duration totpValidFor, Clock clock) {
    var factors = AllRequiredFactorsAuthorizationManager.<RequestAuthorizationContext>builder()
        .requireFactor(f -> f.passwordAuthority())                                  // FACTOR_PASSWORD
        .requireFactor(f -> f.authority("FACTOR_TOTP").validDuration(totpValidFor))
        .build();
    factors.setClock(clock);                       // the SAME Clock bean the TOTP counter uses — see §5
    return AuthorizationManagers.allOf(
        new AuthorizationDecision(false),          // all-abstain ⇒ DENY. allOf(managers...) would GRANT.
        AuthorityAuthorizationManager.hasRole("ADMIN"),   // FIRST: non-admin ⇒ 403, anonymous ⇒ 401
        factors);
}

// GET /api/admin/**  → adminRule(ABSOLUTE_SESSION_LIFETIME, clock)   // see §4 — a type guard, not a bound
// other /api/admin/** → adminRule(Duration.ofMinutes(10), clock)
```

Applied to the manager ticket 11's typed authorization matrix produces, not beside it, so `/api/admin/**` keeps
one source of truth for role→path. Method-aware matchers, reads before writes, first-match-wins.
`spring.mvc.servlet.path` remains prohibited (CVE-2026-22753). `PathPatternRequestMatcher` only.

**`RoleHierarchy` is deliberately absent.** A hand-built `AuthorityAuthorizationManager` defaults to
`NullRoleHierarchy` and does not pick up a bean, whereas the DSL's `hasRole()` would have got one via the
factory we just abandoned. Ticket 11 ruled the role hierarchy **out of scope** — it maps one-to-one onto itself
with two roles — so calling `setRoleHierarchy` here would be dead code. Recorded once, plus a one-line
assertion that no `RoleHierarchy` bean exists, so a future hierarchy fails loudly instead of being silently
ignored by the admin rules. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-ADM-017. Amend the table by ID, not this list.*

**Negative assertion:** no rule for any surface is ever routed through a factor-bearing
`DefaultAuthorizationManagerFactory`. Its javadoc says the additional authorization "does not affect
`anonymous`", but 7.1.x source overrides `anonymous()` through `createManager(...)` → `withAdditionalAuthorization(...)`.
We never touch that path; the assertion exists so nobody reintroduces it on the javadoc's word. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-MFA-018. Amend the table by ID, not this list.*

**Three startup assertions**, all fail-fast during context refresh:

1. The TOTP `AuthenticationProvider` bean exists (STD §4.1's provider-registration validation, surviving in
   spirit after `MFATypeContainer` is dropped).
2. Both rule sets are non-empty.
3. `readValidDuration >= absoluteSessionLifetime`. Ticket 05 owns that lifetime and can change it; if it ever
   exceeds the read rule's duration, admins get bounced to the challenge mid-session with no code change to
   blame. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-017, T-CFG-018, T-CFG-019. Amend the table by ID, not this list.*

#### The two filters

Confirmation and verification are **two `AbstractAuthenticationProcessingFilter` beans** sharing one provider
family, one session strategy and one pair of handlers. The filter is chosen over a controller because 7.x
performs the authority merge itself, inside `doFilter`, gated on `shouldPerformMfa` — we do not write it. But
four things must be explicit or it fails silently:

```java
@Bean
TotpVerificationFilter totpVerificationFilter(AuthenticationManager am,
        SecurityContextRepository repo, SessionAuthenticationStrategy factorGrantStrategy,
        ProblemDetailWriter writer) {
    var filter = new TotpVerificationFilter(
        PathPatternRequestMatcher.withDefaults().matcher(POST, "/api/mfa/totp/verification"), am);

    // (1) Arms the authority merge. Default false. Normally set by EnableMfaFiltersConfiguration's
    //     BeanPostProcessor, which we do NOT get because @EnableMultiFactorAuthentication is
    //     deliberately unused (it registers a GLOBAL factory — see ADR 23-2).
    //     Without this: verification succeeds, FACTOR_PASSWORD is dropped, every admin request 412s forever.
    filter.setMfaEnabled(true);

    // (2) The filter has its OWN repository field, defaulting to RequestAttributeSecurityContextRepository.
    //     Its setter javadoc: "The default action is not to save the SecurityContext."
    //     Without this: the grant lives for one request. 204, then no factor on the next call.
    filter.setSecurityContextRepository(repo);

    // (3) Ticket 08's MINIMAL strategy: ChangeSessionId + explicit saveContext + CsrfAuthenticationStrategy,
    //     deliberately EXCLUDING AuthInstantStampingStrategy. Not the login composite.
    //     A configurer would inject the shared (login) strategy and silently reset the 8-hour window
    //     on every 10-minute re-verification. The filter's own default is NullAuthenticatedSessionStrategy,
    //     which would not rotate at all. Both wrong; this is the only correct value.
    filter.setSessionAuthenticationStrategy(factorGrantStrategy);

    // (4) Defaults redirect (SavedRequestAware / SimpleUrl). The SPA would get a 302 to a page that
    //     does not exist. ProblemDetailWriter on failure; sendError is prohibited (ticket 06).
    filter.setAuthenticationSuccessHandler(new NoContentAuthenticationSuccessHandler());
    filter.setAuthenticationFailureHandler(new ProblemDetailAuthenticationFailureHandler(writer));
    return filter;
}
```

Registered `addFilterBefore(..., AuthorizationFilter.class)` — after `CsrfFilter` (so the verification POST is
CSRF-protected, without which the whole reason for rejecting GET at provisioning returns), after
`SecurityContextHolderFilter`, and **after ticket 11's forced-change filter**, which shares the anchor and must
run first so a flagged admin changes their password before enrolling.

`TotpAuthenticationToken` declares a public no-arg `toBuilder()` — `declaresToBuilder` checks the **concrete**
class by reflection — and its `getName()` is taken from the current `Authentication`, never from the payload.

#### The precondition, and why it cannot live in the authorization rules

`shouldPerformMfa` is **not** a reachability gate. From source, it is consulted *after* `attemptAuthentication`
has already returned, and returning `false` merely skips the merge before falling through to
`sessionStrategy.onAuthentication` and `successfulAuthentication`. And because a matching request is consumed —
`continueChainBeforeSuccessfulAuthentication` is `false` — `AuthorizationFilter` never runs, so
`.requestMatchers("/api/mfa/totp/verification").authenticated()` is **dead code**. Writing that rule and
believing it is the trap.

So the precondition lives in the provider, before any code comparison:

- **No authenticated, non-anonymous principal** → `InsufficientAuthenticationException` → failure handler →
  **401 `AUTHENTICATION_FAILED`**. The test must be `AuthenticationTrustResolver.isAnonymous(...)`, **not**
  `current != null && current.isAuthenticated()`: our filter runs after `AnonymousAuthenticationFilter`, and an
  `AnonymousAuthenticationToken` *is* authenticated, so the null-and-authenticated form passes for anonymous
  and provides nothing.
- **Authenticated but not `ROLE_ADMIN`** → `AccessDeniedException`, which is not caught by the filter (it
  catches `AuthenticationException`) and propagates to `ExceptionTranslationFilter` → `accessDeniedHandler` →
  **403 `ACCESS_DENIED`** through ticket 06's existing producer 3. No new envelope producer.
- **The username comes from the `SecurityContext`, never from the request body.** This is the single
  load-bearing detail behind §5's repricing: accept a username or id from the payload and an anonymous
  attacker can burn any admin's cumulative counter for free.
- **No counter increment on precondition failure.** Someone who is not even a candidate must not consume the
  victim's budget, or the cheap denial-of-service lever appears in the one place nobody would look for it.

Also from source, two consequences of `unsuccessfulAuthentication`: it calls `clearContext()` **before** the
failure handler, so the handler cannot read who failed — the username must be captured during
`attemptAuthentication` for counter attribution and the audit event; and because the repository is written only
on success, clearing the holder does **not** end the session, so a failed TOTP attempt correctly leaves the
password authentication intact in the store. Never return `null` from `attemptAuthentication` — with no
`AuthenticationConverter` set, `continueChainWhenNoAuthenticationResult` is `false` and the request returns with
no response written. Throw.

#### Missing, expired and not-enrolled denials

Spring Security *does* know which factor is missing, so ticket 08 was right that this is a conversion job. But a
**custom** factor gets no automatic entry point: `FormLoginConfigurer` and `OneTimeTokenLoginConfigurer` register
theirs for `FACTOR_PASSWORD` and `FACTOR_OTT` only. Without registration, missing-`FACTOR_TOTP` denials produce a
bare **403** from `AccessDeniedHandlerImpl`. So:

`defaultDeniedHandlerForMissingAuthority(totpEntryPoint, "FACTOR_TOTP")`, where `totpEntryPoint` writes through
`ProblemDetailWriter` and branches on request attribute `WebAttributes.REQUIRED_FACTOR_ERRORS`:

- `RequiredFactorError.isMissing()` **and** `isExpired()` → **412 `MISSING_FACTOR`**, with extension members
  `factor: "TOTP"` and `reason: MISSING | EXPIRED`. One code because the client's action is identical — show the
  challenge — and ticket 06's enum is closed; the distinction survives for logs and UX copy without spending an
  identifier. `INVALID_FACTOR` is reserved for a wrong code submitted to the verification endpoint, the only
  place a code is ever judged.
- **Not enrolled** → **422 `FACTOR_ENROLMENT_REQUIRED`**. The authorization layer cannot know enrolment state, so
  the entry point performs **one read** of the principal's TOTP row before writing. This is what makes the
  corpus's setup-redirect behaviour work without prose-matching on `"User Details not found."` — a string ticket
  22 proved is **unproducible** from any recipe exception — and it is the server-side fallback for the stale-cache
  case FE-STD §5 names (an admin resets the factor after page load). Without it, ticket 06's
  `FACTOR_ENROLMENT_REQUIRED` is dead code.

Ticket 06 §11's enumeration **carve-out** is what permits this specificity: every MFA response goes to an
already-authenticated session about its own account.

**A defect wider than this ticket, raised to ticket 06:** the auto-registered entry points for `FACTOR_PASSWORD`
and `FACTOR_OTT` use a *browser-request* matcher that excludes `X-Requested-With: XMLHttpRequest` and JSON
`Accept`. On our SPA those denials never match and fall through to **403, not ticket 06's 401**. And because
`ExceptionTranslationFilter.handleAccessDeniedException` routes anonymous denials to the chain's **main**
`authenticationEntryPoint` — the missing-authority map lives in the `accessDeniedHandler`, the `else` branch —
the anonymous 401 story and that amendment are **one fix**: the main entry point must be our writer with a
matcher that matches `fetch`.

### 3. Session interaction

**Session id rotates on every factor grant**, via ticket 08's minimal strategy. It is an authentication event and
the rotation is nearly free; what matters far more is what the strategy **excludes**.

**`AuthInstantStampingStrategy` is wired in exactly one place — the login composite — and nowhere else.** This is
ticket 08's invariant and the trap is the framework default, not a hypothesis: `AbstractAuthenticationProcessingFilter`
calls `sessionStrategy.onAuthentication(...)` on the second factor, and `AbstractAuthenticationFilterConfigurer`
injects the *shared* strategy. Wire either filter through a configurer and the 8-hour absolute window resets on
every 10-minute re-verification, so a busy admin never absolutely expires. Bypassing the configurer and setting
the strategy explicitly is the entire reason for the `addFilterBefore` registration.

**Re-verification is a replacement, not a mutation.** `FactorGrantedAuthority.getIssuedAt()` *is* the clock
`validDuration` reads, so granting a fresh factor means constructing a new `Authentication` and writing it back.
Reinforced from source into a correctness requirement: `AllRequiredFactorsAuthorizationManager.requiredFactorError`
takes `findFirst()` on the matching authority and does **not** try later ones, so a stale `FACTOR_TOTP` sitting
beside a fresh one decides the outcome. Appending is forbidden; the authority set is replaced.

**The absolute session lifetime kills the factor with the session** — 8 hours from `AUTH_INSTANT`, and 15 minutes
of idle gets there sooner. There is no separate factor lifetime to expire.

**Serialisation is asserted, not assumed** — and the test needs a negative case. `FactorGrantedAuthority` is in
`org.springframework.security.core.authority`, implements `GrantedAuthority` (hence `Serializable`), declares a
`serialVersionUID`, and carries a `String` plus an `Instant`, so it survives JDK serialisation into
`SPRING_SESSION_ATTRIBUTES`. The test asserts the **deserialised type** and `getIssuedAt()`, not just
`getAuthority()`, **and** asserts that a plain `SimpleGrantedAuthority("FACTOR_TOTP")` is denied. Without that
second assertion nothing in the suite exercises the reason §4's duration is there. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-MFA-005. Amend the table by ID, not this list.*

### 4. `validDuration` on the read rule is a type guard, not a time bound

Ticket 19 left admin reads unbounded and ticket 22 handed over whether to bound them. The answer is a duration
equal to the absolute session lifetime, and the reason is **not** the bound.

From source, `requiredFactorError` grants whenever `validDuration == null` and the authority *string* matches —
and `getFactorGrantedAuthorities` returns **all** authorities, not just `FactorGrantedAuthority` ones, despite
its javadoc claiming otherwise. So any plain `GrantedAuthority` bearing the string `FACTOR_TOTP` satisfies an
unbounded rule. A non-null `validDuration` is the only thing that routes a degraded authority into
`createExpired(...)`.

That asymmetry is otherwise dangerous in the worst direction: the 10-minute write rule fails **closed** under a
degraded round trip (annoying, visible, safe) while an unbounded read rule fails **open**. Setting the duration
to the absolute session lifetime makes both fail closed while remaining unable to fire in normal operation,
because the factor is always granted *after* `AUTH_INSTANT` so factor-expiry always falls after session-expiry.

**The ADR text must say this explicitly, because a reviewer seeing `validDuration(8h)` will grade it as an
8-hour factor bound.** It is present to force fail-closed behaviour on a degraded authority; it can never fire in
normal operation; the real bounds are the session's 15-minute idle and 8-hour absolute limits. *Consolidated into the register (ticket 33): R-MFA-012. Amend the table by ID, not this list.*

The residual deviation is unchanged and is recorded: STD L310 is an enforced constraint requiring the enforcement
layer to verify **on each request**, a per-request possession-proof model. Ours is per-session. Per-request was
rejected because it would demand a fresh six-digit code for every page of an admin list; the 10-minute rule is
the compensating bound on the half that changes state. *Consolidated into the register (ticket 33): R-MFA-012. Amend the table by ID, not this list.*

### 5. Factor lockout: two tiers, and the arithmetic that forces the second

`MFA_Core` STD L237 mandates 10 cumulative failures in a 1-hour window → session kick-out → **locked until
administrative review**. Ticket 22 found the window is not sliding (only the most recent failure is tested, so
paced attempts accumulate), that **no TOTP unlock path exists anywhere** in the corpus (Recipe 11 clears
`PIN_USER_DETAILS` only), and that this is a denial-of-service lever against a known admin username. *Consolidated into the register (ticket 33): R-STD-042. Amend the table by ID, not this list.*

**Tier 1 — 10 consecutive failures inside a 20-minute observation window → 20-minute lock, automatic lift.**
Mirrors ticket 09's password axis exactly: same three columns, same derived predicate
(`locked_until == null || locked_until <= now`), same staleness reset on `last_failed_at`, no scheduler. One
window value fixes both of ticket 22's defects — the missing observation window, and the counter that would
otherwise stay at 10 after auto-lift and re-lock on the first wrong code. "Until administrative review" is
deviated from on three independent grounds, all already on this map: ticket 11's bootstrap would brick on a fresh
deploy; the phrase is unimplementable for a two-admin population where the reviewer may be the locked party; and
WSTG-ATHN-03 requires a tier-1 self-recovery path beneath any tier-3 admin control. Tier 1 **resets on success**
(NIST §3.2.2's SHOULD), subject to its AAL ceiling. *Consolidated into the register (ticket 33): R-STD-042. Amend the table by ID, not this list.*

**Tier 2 — 100 cumulative failures → the factor is disabled, cleared only by `DELETE /api/admin/users/{uuid}/totp`.**
This tier exists because copying ticket 09's auto-lift alone breaks ticket 09's own reasoning. Its calibration
argument was explicitly *"100 guesses is a six-digit-OTP number; ours is ~12 guesses/hour against a 15-char
zxcvbn-3 credential"* — it distinguished the two credential spaces, and a 10⁶ space cannot inherit a control
calibrated for a passphrase. With tier 1 alone:

> 10 attempts per 20-minute cycle = **720/day**. Against a ±1 window each guess matches with probability
> ≈ 3/10⁶. That is 2.16×10⁻³/day, and 1 − exp(−262 800 × 3×10⁻⁶) = **≈ 55% over a year**.
>
> With the cumulative cap: 100 × 3×10⁻⁶ = **≈ 0.03% over the life of the secret.**

Two sentences that must travel with those figures or a reviewer will mis-grade them:

- **The 55% is conditional on prior password compromise.** The verification endpoint is behind an authenticated
  session, so every guess costs the attacker that admin's password — which is precisely the scenario a second
  factor exists for, so the precondition justifies tier 2 rather than weakening it. The mechanism that makes it
  true is §2's **provider-side precondition plus the context-derived username**, *not* `shouldPerformMfa`, which
  gates only the merge. A reviewer tracing `shouldPerformMfa` would find it does not say this.
- **The per-IP throttle is not the control bounding this.** At tier 1's pace a single source sits at 0.5/min
  against its 20/min budget, two orders of magnitude inside it, so the throttle never engages on the guess rate
  and tier 1 is the sole rate ceiling — which is why a cumulative cap is necessary rather than redundant. The
  throttle's remaining job on this route is **unauthenticated noise**: the anonymous POSTs the precondition now
  rejects still cost a request, a context read and a rejection.

**Tier 2 does not reset on success.** This is deliberate and it is the half that makes the arithmetic hold: if a
legitimate morning login zeroed the counter, tier 2 would never fire against an active admin and the 55% returns.
NIST's cap is verbatim "**consecutive** failed authentication attempts", so a strictly compliant reading resets on
success and delivers the 55%; **our cumulative reading is stricter than the SHALL**, which §3.2.2's "agencies MAY
impose lower limits" explicitly permits. That sentence belongs in the ADR, because it pre-empts the reviewer who
notices "consecutive" and thinks they have found a gap. *Consolidated into the register (ticket 33): R-LCK-008, R-STD-044. Amend the table by ID, not this list.*

**Tier 2 clears only by rebinding**, which is what NIST asks — "disabled authenticators SHALL be required to
rebind to the subscriber account" — and our reset deletes the secret and forces re-enrolment, so the requirement
is satisfied rather than deviated from. Tier 1's auto-lift is **not** rebinding, and that is recorded as a
deliberate structural choice, not a deviation: the SHALL attaches to the disablement NIST specifies **at its cap**,
and we implement that at its cap. Tier 1 is an additional control *below* NIST's threshold, which §3.2.2 permits;
reading it as a deviation would imply a verifier may not throttle more aggressively than the cap. *Consolidated into the register (ticket 33): R-LCK-008. Amend the table by ID, not this list.*

**Conjunction rule (NIST §3.2.2's multi-authenticator SHALL).** Counters stay independent for counting — a failed
code never touches the password counter and a failed password never touches the factor counter, per ticket 09's
rules 1 and 2 — **and if both are simultaneously over threshold, both lock.** That satisfies "if more than one
authenticator is involved with an excessive number of authentication attempts … both authenticators SHALL be
disabled" with one predicate, and creates no lever an attacker can pull on a single axis. *Consolidated into the register (ticket 33): R-LCK-008. Amend the table by ID, not this list.*

**Availability cost, honestly priced.** Every guess against a tier-2 counter costs that admin's password, so
burning one admin's factor costs one password compromise and burning both costs two — strictly more than the
attack tier 2 defends against, and an attacker holding both passwords wants *in*, not a dark surface. What
genuinely remains: an admin can burn their own counter (self-harm, the other admin resets them), and if both do,
the surface is unreachable. Both actors are trusted. So the break-glass path goes to ticket 25 as a **second
trigger on the runbook control ticket 19 already owes for lost authenticator**, not as a new control compensating
for a cheap lever. ASVS **6.1.1 (L1)** is the documentation hook, and it asks for anti-automation as well as rate
limiting and adaptive response. *Consolidated into the register (ticket 33): R-LCK-003, R-RUN-001. Amend the table by ID, not this list.*

**Escalating backoff declined.** NIST offers it as a **MAY**, framed as *anti-lockout* rather than as strengthening
the throttle, and ticket 09 already declined progressive backoff on thread-pool exhaustion — a sleeping request
holds a thread. Tier 2 now does the work it would have done. CAPTCHA remains out of scope.

**Counter location and locking.** The three columns live on `TOTP_USER_DETAILS` per STD §4.2, with `lockedAt`
renamed `locked_until` to match the derived predicate, plus a `cumulative_failures` column the corpus has no
equivalent for. Recipe 12 calls getters and setters for `failedAttempts`, `lastFailedAttemptAt` and `lockedAt`
that **Recipe 5's entity does not declare** (RCP L787, L822–831 against RCP L332–352), so it cannot compile as
printed and we borrow the `PIN_USER_DETAILS` column names on our own authority. *Consolidated into the register (ticket 33): R-STD-040. Amend the table by ID, not this list.*

**Replay rejection is atomic, not merely constant-time.** ASVS **6.5.1 (L2)** requires TOTPs be "only successfully
usable once" and RFC 6238 §5.2 is a `MUST NOT`. `lastUsedCounter` delivers that only if two concurrent requests
carrying the same code cannot both win. The pessimistic row lock is already required for the counters, so it is
held across **load → decrypt → compare → write** as one unit — cheaper than a second conditional statement and
one write path instead of two. *Consolidated into the register (ticket 33): R-MFA-013. Amend the table by ID, not this list.*

**And on this path we deliberately do not fail open**, unlike ticket 09's password axis. There, a lock timeout
loses a count; here it loses replay rejection itself, which *is* the control. A lock timeout on the TOTP path is a
failed authentication. Adding the TOTP row to the ordering: **user rows → TOTP rows → session rows**, with H2's
`LOCK_TIMEOUT` pinned at 1 s on the JDBC URL per ticket 09. *Consolidated into the register (ticket 33): R-MFA-013. Amend the table by ID, not this list.*

**Enrolment confirmation is rate-limited but not counted**, and the reasoning has to be recorded or the absence
reads as an oversight. Ticket 22 is right that Recipe 10 counts nothing and is brute-forceable indefinitely — but
the threat does not exist: anyone who can reach the confirmation endpoint already holds a password-authenticated
session for that admin and can simply provision *their own* secret and confirm it legitimately. Brute-forcing the
pending code buys nothing they do not have. The 15-minute pending TTL bounds the window regardless.

**Three new rate-limit rows** for ticket 09's budget table, all per source IP in the early filter, which cannot key
on a principal because it runs before `SecurityContextHolderFilter`:

| Route | Axis | Burst | Sustained |
|---|---|---|---|
| `POST /api/mfa/totp/verification` | source IP | 20 | 1 / 3 s |
| `POST /api/mfa/totp/enrolment/confirmation` | source IP | 20 | 1 / 3 s |
| `POST /api/mfa/totp/enrolment` | source IP | 10 | 1 / 6 s |

The first names the path for ticket 09's previously pathless "TOTP challenge" row. `429` carries `Retry-After` in
integer seconds (STD L199 and ticket 06) plus ticket 09's full `Cache-Control`/`Pragma`/`Expires` triple. Admin
endpoints including the reset stay deliberately unthrottled.

**The listener discrimination debt is now ours in code.** Ticket 09 resets the password counter from
`InteractiveAuthenticationSuccessEvent`, and both our filters publish the **same event type** from
`successfulAuthentication`. So the listener **discriminates on the `Authentication` type or the source filter, not
on the event type** — one line, without which a successful factor step resets the password failure counter and
ticket 09's rule 2 reads as though it works and does not. The event carries the **merged** authority set;
`ProviderManager`'s `AuthenticationSuccessEvent` carries the unmerged provider result.

**NIST §3.2.2's both-authenticators rule is satisfied** by the conjunction rule above rather than declined. The AAL
reset constraint — "the maximum AAL of the authenticator being reset SHALL not exceed the AAL of the session from
which it is being reset" — is satisfied on both routes: self-reset on successful verification happens in a session
that just proved the factor, and admin unlock happens in a session holding the factor. *Consolidated into the register (ticket 33): R-LCK-008. Amend the table by ID, not this list.*

### 6. Admin factor reset, and one unlock endpoint for both axes

`DELETE /api/admin/users/{uuid}/totp` is ticket 11's: ADMIN-only, behind the factor with 10-minute
re-verification, routed through `AdminActionGuard` for both the actor≠subject rule and the two-enrolled-admins
invariant, target's sessions killed, audited.

**It deletes the confirmed row *and* any `PENDING_TOTP` row, and the security argument is the pending one.** If
reset cleared only the confirmed row, a pending secret left behind by an attacker who reached provisioning with a
hijacked password-only session survives, and the target's next enrolment attempt can confirm **the attacker's
secret**. That is why pending is cleared — not tidiness.

Killing the target's sessions is mandatory, not hygiene: a session already holding `FACTOR_TOTP` would otherwise
outlive the secret's destruction.

Deleting the row **deletes tier 1's lock and tier 2's disable with it**, which resolves ticket 22's "no unlock path
anywhere" for the enrolled case. But destroying an enrolment to clear a lock is disproportionate for someone who
merely mistyped, so **ticket 11's `POST /api/admin/users/{uuid}/unlock` is extended to clear both axes** — the
three password columns plus the TOTP tier-1 lock — rather than adding a second unlock endpoint that the eleventh
endpoint next year forgets. It does **not** clear tier 2, which is reserved for rebinding. ASVS **6.4.3 (L2)**
constrains *password reset*, not admin action, and ticket 10 already pinned that reset redemption clears the
password lockout and **never any TOTP counter or enrolment state**. *Consolidated into the register (ticket 33): R-STD-037. Amend the table by ID, not this list.*

The target's next experience: sessions gone, next login is password-only, `GET /api/profile` reports
`factors.enrolled: false`, `/api/admin/**` returns **422 `FACTOR_ENROLMENT_REQUIRED`**, and the SPA routes them to
`/settings/mfa`.

**ASVS 6.4.4 (L2) is *not* cited as satisfied here.** Its text is "if a multi-factor authentication factor is lost,
evidence of **identity proofing** is performed at the same level as during enrollment", and we perform no identity
proofing at enrolment, so there is nothing to match. Recorded as N/A-with-rationale; claiming it as satisfied would
be overclaiming, and "vacuously satisfied" is no better. *Consolidated into the register (ticket 33): R-RUN-001. Amend the table by ID, not this list.*

**The two-enrolled-admins invariant is not re-decided here.** Ticket 11 owns it: one central `AdminActionGuard`
covering disable, demote, delete and factor reset, under a pessimistic lock over the admin row set counted in Java
because **H2 rejects `FOR UPDATE` on aggregate queries**, with the predicate `activated_at IS NOT NULL` **and**
TOTP-enrolled so an invited-but-unredeemed admin cannot satisfy it.

### 7. Bootstrap admin, and the first-enroller race

Ticket 11's sequence holds, and nothing in the bootstrap needs the admin surface to seed itself: seed → login →
403 `PASSWORD_CHANGE_REQUIRED` on everything outside the five allowlisted paths → change password → flag cleared →
enrol → factor granted → `/api/admin/**` opens.

**Pre-enrolment the admin surface is closed, not password-protected.** Nobody is enrolled, so the factor check
denies and the entry point returns 422. A control saying "refuse to serve `/api/admin/**` until an admin is
enrolled" would buy nothing; it is already the behaviour.

**The real residual is a first-enroller race:** whoever reaches provisioning with the seed password first binds
their own authenticator and thereafter holds the surface with full, indistinguishable legitimacy. *Consolidated into the register (ticket 33): R-ADM-016. Amend the table by ID, not this list.*

**And NIST SP 800-63B-4 §4.1.2.1 endorses exactly this shape**, which converts the race from a finding a reviewer
discovers into a decision the standard sanctions. Binding an additional authenticator "SHALL" require
authentication at *"either the maximum AAL currently available in the subscriber account or the maximum AAL at
which the new authenticator will be used, **whichever is lower**"*. For a first enrolment the account's available
maximum is password-only, so **password-only enrolment is explicitly compliant**. No control is compelled.

Three things bound the window and are recorded rather than mitigated: the seed credential comes from the
environment with no default and no generate-and-log fallback, clearing the 15-character zxcvbn-3 floor; the forced
password change happens before enrolment is reachable; and the window is one login long in practice. Ticket 25
gains a required content: enrol the seeded admin before the deployment is reachable by anyone else. *Consolidated into the register (ticket 33): R-ADM-016. Amend the table by ID, not this list.*

**ASVS 6.3.2 (L1) does not bite.** It names *default accounts* — `root`, `admin`, `sa` — and ours has an
operator-supplied username from config with no default credential. Seed-disabled-then-activate was considered and
is **not implementable**: enabling a user goes through `PUT /api/admin/users/{uuid}/enabled`, which requires an
enrolled admin, and that is the only admin. **ASVS 6.4.1 (L1)** governs *system-generated* initial passwords and
activation codes, so it does not apply to an operator-supplied secret, and its operative "must not be permitted to
become the long term password" is already discharged by ticket 11's forced change.

**Fresh password re-entry on provisioning and confirmation: declined.** Not compelled by §4.1.2.1, and ticket 20's
`script-src 'self'` with no inline script and `assetsInlineLimit: 0` means the session-riding XSS it defends
against must first get past a policy we own. The logging argument for declining is **dropped**: body logging is
already suppressed on these routes for the six-digit code, the Base32 secret and the `otpauthUri`, so a password *Consolidated into the register (ticket 33): R-HDR-003. Amend the table by ID, not this list.*
field adds nothing to a list we must maintain anyway. **This makes a ticket 20 CSP decision load-bearing for an MFA
decision**, and ticket 20 is told it has acquired a dependent.

**The hijacked-session tension with §6 resolves rather than standing.** An attacker who completes an enrolment from
a hijacked session gains **nothing immediately exploitable**, because starting any future session still requires
the password. The residual is two things that outlive the session: **denial of the victim's own enrolment**, since
§1's guard now returns 409 and the real admin cannot enrol or reach `/api/admin/**` until another admin resets
them; and **a pre-positioned factor** that becomes useful the day the password leaks. Both are bounded by admin
reset, and both are visible — **the 409 to the legitimate admin is itself the detection signal**, an admin told
"you already have an authenticator" when they know they do not has been handed an unambiguous compromise
indication. Which is why the enrolment audit event is worth **alerting** on, not merely recording. *Consolidated into the register (ticket 33): R-MFA-019. Amend the table by ID, not this list.*

### 8. Authentication pathway inventory (ASVS 6.1.3 and 6.3.4, both L2)

Both are conditional on the application having multiple authentication pathways, and both demand that controls and
strength be enforced **consistently across them**, not merely documented per pathway. We have three, so they bind.

| Pathway | Grants | Verification | Session | Counters |
|---|---|---|---|---|
| `POST /api/login` (ticket 08 Route C) | `FACTOR_PASSWORD` + role authorities | BCrypt-12 behind `DelegatingPasswordEncoder` | login composite, incl. `AuthInstantStampingStrategy` | password axis |
| `POST /api/mfa/totp/enrolment/confirmation` | `FACTOR_TOTP`, merged | RFC 6238 ±1, constant-time, counter burned, against `PENDING_TOTP` | minimal strategy, **no** stamping | none (see §5) |
| `POST /api/mfa/totp/verification` | `FACTOR_TOTP`, merged | identical, against `TOTP_USER_DETAILS` | minimal strategy, **no** stamping | factor axis, both tiers |

Consistency is satisfied structurally rather than by assertion: **no pathway reaches `/api/admin/**` with less than
both factors**, which is the case 6.3.4 protects against — the legacy endpoint that skips MFA. Token redemption
(activation, invite, reset) is deliberately **not** a pathway: ticket 10 settled that redemption sets a credential
and mints no session. *Consolidated into the register (ticket 33): R-MFA-014. Amend the table by ID, not this list.*

**ASVS 6.3.3 (L2) is relaxed and the entry belongs here**, against the requirement number rather than against a
ticket. MFA gates the admin surface only; regular users are password-only. Relaxation requires **both** a fully
documented rationale and a comprehensive set of mitigating controls: the rationale is IM8 **ac-2**, which scopes
mandatory MFA to privileged access, plus ticket 19's finding that an opt-in factor no authorization rule ever
demands secures nothing; the mitigating controls are the 15-character floor for every account, the zxcvbn-3 gate,
the breach blocklist, dual rate limiting and the session contract. The map's standing note that a whole-application
**L2 claim would be false** on 6.3.3 is unchanged. *Consolidated into the register (ticket 33): R-MFA-015. Amend the table by ID, not this list.*

### 9. Secret encryption: the envelope, and one correction to ticket 19

`AesGcmBytesEncryptor.withSecretKey(...)`, key injected from the environment, absent from every profile —
overriding STD L253's instruction to store it in the database on the grounds that this co-locates key and
ciphertext, with cover from **Q13**, which presents key provisioning as an open integrator choice and thereby
contradicts the `<enforced-constraint>` tag beside it. The two-argument `AesBytesEncryptor` constructor and a null
IV generator in CBC mode remain prohibited (CVE-2026-47842).

**AAD binding is rejected as specified and replaced with an equivalent that costs nothing.**
`AesGcmBytesEncryptor` implements `BytesEncryptor`, whose entire surface is `encrypt(byte[])`/`decrypt(byte[])`;
there is no AAD parameter and the `Cipher` instances are private finals. Reaching AAD means hand-rolling `Cipher`
with `updateAAD`, discarding the CVE-safe implementation ticket 19 chose and returning to the code shape the CVE
came from. ASVS imposes no AAD requirement — **11.3.3 (L2)** merely *prefers* authenticated encryption, which GCM
already is.

So the context goes **inside the plaintext** as a fixed-width prefix: encrypt `userUuid ‖ keyVersion ‖ secret` and
verify the prefix after decrypt. A row swapped between users decrypts cleanly and then fails the prefix check; a
stale key version is caught the same way. Fixed-width so verification is a slice-and-compare rather than delimiter
parsing, and **a prefix mismatch is a security event with an audit entry, not a decrypt error** — the whole value
of the check is that it only trips when rows have been moved. One comment saying why AAD is absent, so a reviewer
reading for AAD knows it was considered.

**Envelope layout — ticket 19's figure is wrong and ticket 12 must own this diagram:**

```
┌────────────┬──────────────────────────────────────────────┬───────────┐
│  IV        │  AES-256-GCM ciphertext of the plaintext     │  tag      │
│  16 bytes  │  37 bytes                                    │  16 bytes │
└────────────┴──────────────────────────────────────────────┴───────────┘
                          plaintext, 37 bytes:
              ┌──────────────┬──────────────┬──────────────┐
              │ userUuid     │ keyVersion   │ secret       │
              │ 16 raw bytes │ 1 byte       │ 20 raw bytes │
              └──────────────┴──────────────┴──────────────┘
                     total stored: 16 + 37 + 16 = 69 bytes
```

Ticket 19's answer says 12 + 20 + 16 = 48. `AesGcmBytesEncryptor` uses a **16-byte IV**, not 12, and says so in its
own javadoc. A 48-byte column truncates. Three widths must be pinned or this recurs: the UUID goes in as **16 raw
bytes**, not its 36-character string form; the secret as **20 raw bytes**, not its 32-character Base32 form; and the
version width is fixed at one byte. Getting either of the first two wrong yields 81 or 105. The column is
`byte[]`/VARBINARY per STD §4.2 and Recipe 5, so base64 character counts do not apply — nobody should size a
`VARCHAR(92)`. Ticket 22's requirement that **both tables size identically** holds, because Recipe 10 copies the
blob verbatim with no re-encryption.

**A key-version column** is added because STD §4.2 has none, which makes STD L253's yearly-rotation MUST
unimplementable as written; with it, rotation runs incrementally. Q14 leaves the procedure open and ticket 25 owns
writing it. *Consolidated into the register (ticket 33): R-CFG-005, R-STD-039. Amend the table by ID, not this list.*

**NIST SP 800-38D is satisfied, not deviated from** — this replaces an entry that was heading for the deferral
register. The 96-bit figure is in **§5.2.1.1** and is non-normative: "it is recommended that implementations
restrict support to the length of 96 bits, to promote interoperability, efficiency, and simplicity of design". A
fully random 128-bit IV is **inside** §8.2.2's RBG-based construction and is the shape §8.2.2 itself recommends —
random field ≥96 bits, free field empty so the random field is the entire IV — and §8.2's own worked example names
"the construction in Sec. 8.2.2 for 128-bit and 160-bit IVs". The one **shall** in play is §8.3's cap of **2³²
authenticated-encryption invocations per key**, which applies to the RBG-based construction at any IV length. Our
numerator: one encryption per enrolment plus one per re-provisioning attempt, against two admins and a yearly-rotated
key — single digits to low tens per key-year against 4.29×10⁹, roughly eight to nine orders of magnitude inside the
limit. §7.1 Step 2 takes the GHASH-based `J0` derivation, which is a performance note and nothing else. *Consolidated into the register (ticket 33): R-MFA-016. Amend the table by ID, not this list.*

### 10. TOTP mechanics, configuration, and dependencies

`MFA_Core`'s prescriptions are retained: 20-byte `SecureRandom` secret, `otpauth://` URI with query order
`secret, issuer, period, digits` and no `algorithm` parameter, RFC 6238 HMAC-SHA1, 6 digits, ±1 window,
constant-time `MessageDigest.isEqual`, `lastUsedCounter` replay rejection initialised to −1 and set to the matched
counter on confirmation.

Two corpus bugs fixed: `lastUsedCounter`'s `-1L` field initialiser is **defeated by Lombok `@Builder`** with no
`@Builder.Default`, so `builder().username(u).build()` yields `0L` (RCP L347 against RCP L672); and Recipe 12 tests
`requestTotp == null` rather than `isEmpty()` (RCP L784), so an empty string counts as a failure, whereas Recipe 10
uses `StringUtils.isEmpty` (RCP L637). *Consolidated into the register (ticket 33): R-STD-043. Amend the table by ID, not this list.*

**Skew is ±1 and the ASVS deviation is recorded rather than traded away.** ASVS **6.5.5 (L2)** gives TOTP "a
maximum lifetime of 30 seconds"; ±1 yields up to ~90, and RFC 6238 §6's own worked example puts two backward steps
at "around 89 seconds". ASVS 5.0 contains **no** clock-drift or skew requirement at all, so there is nothing to
cite in support either way. Trimming to current-plus-previous was considered and rejected because it interacts
badly with the replay rule: ticket 22 established that after the first success `counter-1` and `counter` are burned,
leaving **+1 as the live window**, so dropping the forward step leaves a strict single window and breaks any client
whose clock runs fast. STD §2.5 makes ±1 an `<enforced-constraint>`, and the map's conflict rule is that the
standard wins on how a control behaves — so ±1 stands and **6.5.5 (L2) goes on the deferral register** with the
89-second figure stated honestly. The user-facing consequence ticket 22 identified is recorded for ticket 25: TOTP *Consolidated into the register (ticket 33): R-MFA-017. Amend the table by ID, not this list.*
depends on the **server's** clock, and a device running ~25 s behind succeeds once and then fails until the server
catches up. *Consolidated into the register (ticket 33): R-OPS-004. Amend the table by ID, not this list.*

**One `Clock` bean, two consumers.** The same injected `Clock` drives the TOTP counter computation and
`AllRequiredFactorsAuthorizationManager.setClock(...)`, so the code window and `validDuration` can never disagree.
ASVS **6.5.8** wants a trusted server-side time source but is **L3** and not binding at our target; this is free
regardless. *Consolidated into the register (ticket 33): R-MFA-018. Amend the table by ID, not this list.*

**Configuration properties**, as a `@Validated @ConfigurationProperties` bean explicitly registered — Recipe 8
declares only `issuer`, `period` and `digits`, never validates `period` (0 divides by zero in the counter), and the
corpus registers the class nowhere. Prefix is **`app.mfa.totp`**, not the recipe's `spring.eds.mfa.totp`, because
application properties do not belong in Boot's namespace; recorded as a deviation. *Consolidated into the register (ticket 33): R-CFG-002. Amend the table by ID, not this list.*

| Property | Default | Notes |
|---|---|---|
| `app.mfa.totp.issuer` | none | required, blank ⇒ startup failure |
| `.period` | `30` | must be > 0 |
| `.digits` | `6` | must equal 6 (STD §3.4) *Consolidated into the register (ticket 33): R-STD-054. Amend the table by ID, not this list.* |
| `.secret-bytes` | `20` | RFC 6238 |
| `.skew-steps` | `1` | must be exactly 1; see the 6.5.5 entry |
| `.qr-size` | `200` | literal in the recipe |
| `.pending-ttl` | `15m` | §11 |
| `.lockout.tier1-threshold` / `.tier1-window` | `10` / `20m` | one value serves window and duration |
| `.lockout.tier2-threshold` | `100` | NIST §3.2.2's cap |
| `.encryption.key` | none | environment only, absent from every profile (IM8 **as-8**) |
| `.encryption.key-version` | none | required |

**Dependencies the recipes never name and Boot's BOM does not manage:** `com.google.zxing:core`,
`com.google.zxing:javase` (for `MatrixToImageWriter`), and `commons-codec:commons-codec`. All three explicitly
version-pinned at implementation time.

**The twelve aspect-dependent seams** ticket 22 inventoried are replaced wholesale, since `MFA_Critical_Transaction`
is not followed: the undefined `MultiFactorAuthenticationProvider` superclass (interface-incompatible with Spring's
`AuthenticationProvider` despite the name), `public void authenticate()` with no `Authentication` and no
`supports()`, `mfaRequestContext.getUsername()`, the `OTPRequestContext` downcast, `MFATypeContainer.getRequestKey`
(called five times, never declared), `MFATypeContainerConfig`, and `MFAAuthenticationException extends
RuntimeException` — which is **not** an `AuthenticationException`, so thrown from a filter it would neither reach
`@RestControllerAdvice` nor be treated by `ProviderManager` as an auth failure. Our exceptions extend *Consolidated into the register (ticket 33): R-STD-038. Amend the table by ID, not this list.*
`AuthenticationException`. Recipe 12's `@Transactional(REQUIRES_NEW)` is **re-justified on its own terms** — factor
counters must survive a rejected authentication — rather than on the aspect rationale RCP L843 gives, which no
longer exists.

### 11. `PENDING_TOTP` gains an expiry

STD §4.2's Pending TOTP table has three columns and no clock of any kind, so a provisioned-but-unconfirmed secret
lives forever. `expires_at`, **15 minutes**, and confirmation of an expired pending row is refused with
`422 FACTOR_ENROLMENT_REQUIRED` — no new code. Fifteen rather than ten because enrolment may involve installing an
authenticator app or typing a Base32 secret by hand, and a 10-minute clock strands real people.

It pays four times: it shrinks §6's stale-pending attack window from forever to minutes; it bounds the brute-force
window on the uncounted confirmation endpoint independently of that endpoint's pointlessness; it gives §1's "shown
exactly once" secret a natural end even for abandoned enrolments; and it caps how long plaintext-recoverable key
material sits in the database at all.

**Disposal, stated so its absence is not read as an oversight:** an expired pending row is overwritten by the next
provisioning and otherwise lingers as encrypted dead weight. That is acceptable with no scheduler, consistent with
the account-hygiene scheduled jobs the map has already deferred. The column belongs to **ticket 12**, which is still
open.

ASVS **6.4.1** is *not* cited for this: it governs system-generated initial passwords and activation codes, and a
pending TOTP secret is neither. The control stands on its own merits.

### 12. Status codes: two recorded deviations from HTTP semantics

**412 is not what 412 means.** RFC 9110 §15.5.13 scopes `412 Precondition Failed` to conditions "given in the
request header fields … when tested on the server", cross-referencing §13 Conditional Requests. A missing
authentication factor is not a conditional-request precondition. We keep 412 because ticket 06 inherited it from
the MFA corpus and the enum is closed, and an inconsistent envelope is worse than an odd code — but the ADR records
the deviation and names **RFC 9470** as the road not taken. 9470 is Standards Track (September 2023) and defines
precisely the shape we are re-deriving: `401` with `WWW-Authenticate` carrying `error="insufficient_user_authentication"`,
`acr_values` for required strength and `max_age` for recency. It is **not** authority for us: §3 scopes that error
parameter to the Bearer and other OAuth authentication schemes, and §9 states it "MUST NOT be used to position OAuth
as an authentication protocol". Right shape, wrong applicability for cookie-session authentication. Recording it
pre-empts the reviewer who knows 9470. RFC 9457 imposes no constraint on which statuses may carry `problem+json`,
so nothing in the envelope forced this.

**`FACTOR_ENROLMENT_REQUIRED` at 422 is likewise inherited and likewise off-label** — it is an authorization
outcome, so 403 with a problem type is the better HTTP reading. One line in the register, not a choice to defend. *Consolidated into the register (ticket 33): R-MFA-002. Amend the table by ID, not this list.*

**409 `FACTOR_ALREADY_ENROLLED` takes the enum to 14.** Already-enrolled is a conflict with current resource state,
which is 409's job; 422 is for well-formed but semantically erroneous content, and a 400 `VALIDATION_FAILED` would
stack a second semantic misuse on the two above. Ticket 08's precedent is to reuse rather than earn a code, but the
alternative here — 409 carrying `VALIDATION_FAILED` — would break ticket 06's fixed status/code pairing, which is a
worse precedent than one more member.

### 13. Audit events

Ticket 13 owns the catalogue; these are the events this ticket needs, with `event.action` mapping deferred to it.
`event.action` is a **closed enum** (ticket 03) and `user.target.id` is our documented schema extension for the
second party in an admin action.

| Event | Level | Notes |
|---|---|---|
| TOTP provisioned | INFO | no secret, no ciphertext, no key material (STD §3.3 prohibited content) |
| **TOTP enrolment confirmed** | INFO | **alert-worthy** — see §7, this is the binding event and the compensating record for the NIST §4.1.2.1 notification we cannot send *Consolidated into the register (ticket 33): R-MFA-019. Amend the table by ID, not this list.* |
| Factor verification failure | WARN | attempt count, never the submitted code |
| Tier-1 lock | WARN | fires once per transition, detected inside the row lock |
| **Tier-2 disable** | ERROR | requires admin rebinding; the break-glass trigger |
| Admin factor reset | INFO | ticket 11's `totp-remove`, with `user.target.id` |
| **Context-prefix mismatch** | ERROR | §9 — only trips when database rows have been moved |

**NIST §4.1.2.1's notification SHALL is failed, not deferred.** "When an authenticator is added, the CSP SHALL
notify the subscriber via a mechanism independent of the transaction binding the new authenticator." With a stubbed
transport we cannot, and ticket 10 established that the stub logs what it would send, so a notification would leak
rather than inform. Recorded as a named SHALL failure with the enrolment audit event and the 409 detection signal as
compensating controls. ASVS **6.3.7** is weak support and should not be leaned on: it is **L3**, and its text covers
credential resets and username or email changes, not factor binding — arguably extensible, but the binding
obligation sits at the higher standard anyway. **6.3.5** is about suspicious authentication attempts generally, not
partially-successful authentication, which is one of its four examples. *Consolidated into the register (ticket 33): R-MFA-019. Amend the table by ID, not this list.*

### 14. Corrections to this ticket's own premises

- **`shouldPerformMfa` is not a reachability gate.** It is consulted after `attemptAuthentication` returns and
  gates only the merge; execution falls through to the session strategy and `successfulAuthentication` regardless.
  The precondition that makes every guess cost a password lives in the **provider**, and the ADR must cite it there
  — a reviewer tracing `shouldPerformMfa` would find it does not say what the register claims.
- **An `authorizeHttpRequests` rule on either filter's path is dead code**, because the filter consumes the request
  and `continueChainBeforeSuccessfulAuthentication` is `false`.
- **The factor check runs before the role check** in every factory-produced manager, which was the ticket's assumed
  composition and is wrong in a way that leaks state to non-admins and breaks 401 for anonymous.
- **The one-argument `AuthorizationManagers.allOf` is fail-open** on all-abstain.
- **`shouldPerformMfa`'s own authenticated check passes for anonymous**, because an `AnonymousAuthenticationToken`
  reports `isAuthenticated() == true`.
- **The filter's `SecurityContextRepository` defaults to request-attribute scope** and must be set explicitly, or
  the grant survives one request.
- **`getFactorGrantedAuthorities` returns all authorities, not just factor ones**, despite its javadoc — which is
  why §4's duration is a type guard.
- **Ticket 19's 48-byte envelope is wrong**; it is 69.
- **Ticket 09's auto-lift cannot be copied to a 10⁶ credential space**; that was ticket 09's own argument, applied
  consistently rather than inherited.
- **The pre-enrolment admin surface is closed, not password-protected**, so a "no admin surface until someone is
  enrolled" control is a no-op.
- **`AesGcmBytesEncryptor` cannot carry AAD**; the prefix scheme is the equivalent.

### 15. ADRs owed, glossary terms, and amendments

**ADRs (11):** JSON provisioning envelope with the manual-entry secret, deviating from PNG bytes; per-matcher *Consolidated into the ADR routing (ticket 34): ADR-025. Amend by ID, not this list.*
hand-composed authorization rules rejecting `@EnableMultiFactorAuthentication` **and** the factory idiom, with
role-first ordering as the reason; `validDuration` on the read rule as a fail-closed type guard rather than a time *Consolidated into the ADR routing (ticket 34): ADR-026. Amend by ID, not this list.*
bound; POST provisioning against STD L38; JSON `{ code }` against `X-TOTP`; confirmation grants the factor; the *Consolidated into the ADR routing (ticket 34): ADR-021 / REJ-069 / REJ-070 / REJ-071. Amend by ID, not this list.*
two-tier factor lockout with the 55% / 0.03% arithmetic, the conditional-on-password-compromise framing, and the
cumulative-versus-consecutive note; one unlock endpoint clearing both axes; the context-prefix substitute for AAD *Consolidated into the ADR routing (ticket 34): ADR-027 / REJ-072. Amend by ID, not this list.*
with the key-source override of STD L253; 412 for missing factor against RFC 9110, naming RFC 9470; ±1 skew against *Consolidated into the ADR routing (ticket 34): ADR-028 / REJ-073. Amend by ID, not this list.*
ASVS 6.5.5 (L2). *Consolidated into the ADR routing (ticket 34): REJ-074. Amend by ID, not this list.*

**Glossary:** *pending enrolment*, *tier-1 lock*, *tier-2 disable*, *enrolment binding*, *context prefix*. *Factor
freshness* is already owed by ticket 08.

**Register entries:** ASVS 6.5.5 (L2) deviated; ASVS 6.4.4 (L2) N/A-with-rationale; ASVS 6.3.3 (L2) relaxed with *Consolidated into the register (ticket 33): R-MFA-017, R-RUN-001. Amend the table by ID, not this list.*
rationale and mitigating controls; ASVS 6.1.3 and 6.3.4 (L2) satisfied by §8's inventory; ASVS 6.1.1 (L1) satisfied *Consolidated into the register (ticket 33): R-MFA-014, R-MFA-015. Amend the table by ID, not this list.*
by §5's paragraph; ASVS 6.5.1 (L2) satisfied by atomic replay rejection; ASVS 6.5.8 (L3) satisfied though *Consolidated into the register (ticket 33): R-LCK-003, R-MFA-013. Amend the table by ID, not this list.*
non-binding; NIST §3.2.2 satisfied at the cap with tier 1 recorded as an additional control; NIST §4.1.2.1's *Consolidated into the register (ticket 33): R-LCK-008, R-MFA-018. Amend the table by ID, not this list.*
notification SHALL **failed**; NIST §4.1.2.1's AAL rule **satisfied and load-bearing** for the first-enroller race; *Consolidated into the register (ticket 33): R-ADM-016, R-MFA-019. Amend the table by ID, not this list.*
**NIST SP 800-38D satisfied**, removing an entry rather than adding one. *Consolidated into the register (ticket 33): R-MFA-016. Amend the table by ID, not this list.*

**Amendments raised:** tickets **06** (14th code; the browser-matcher defect breaking 401 for `FACTOR_PASSWORD`),
**08** (`NullRequestCache`; the factor-grant filter's four explicit settings), **09** (three rate-limit rows; the
asymmetric cumulative cap; TOTP rows in the lock ordering; no-fail-open on this path; the listener discrimination
debt discharged in code), **11** (unlock clears both axes), **12** (`expires_at`; the 69-byte envelope; counter
columns), **19** (48→69 bytes; AAD replaced by the context prefix; the read rule now bounded), **20** (it has
acquired a dependent), **25** (break-glass as a second trigger; seed-before-exposure; the server-clock note; the *Consolidated into the register (ticket 33): R-ADM-016, R-OPS-004, R-RUN-001. Amend the table by ID, not this list.*
key-rotation procedure). *Consolidated into the register (ticket 33): R-CFG-005. Amend the table by ID, not this list.*

---

## Amendment from ticket 12 (data model reconciliation)

The three schema requirements this ticket raised are adopted as written. Four refinements and one omission.

**1. `key_version` is `SMALLINT` with a named range check.** `CHECK (key_version BETWEEN 0 AND 255)` on both tables,
because the version is duplicated as a **one-byte** field inside the encrypted plaintext prefix — so a value above 255
would truncate there silently and the audited context-prefix mismatch would fire for a reason nobody could reconstruct.
`SMALLINT` rather than `TINYINT`, which PostgreSQL does not have. Storing the version twice is deliberate and now has a
constraint keeping the two in range of each other.

**2. The 69 bytes become a database invariant, not a comment.** `VARBINARY(69)` bounds only the upper end, so both
tables carry `ck_totp_user_details_totp_key_len` and `ck_pending_totp_totp_key_len` asserting
`OCTET_LENGTH(totp_key) = 69`. This exists because **`ddl-auto: validate` does not check column length at all** —
Hibernate's validator strips type arguments before comparing and never calls its own `hasMatchingLength` helper — so a
48-byte column, the exact figure this ticket corrected, would pass validation silently. The negative tests drive the
checks with **48-byte and 81-byte** values, so the two historically wrong widths are the literal test inputs.
`OCTET_LENGTH` is documented in H2, PostgreSQL and MySQL, so the check costs no portability seam. Note the contrast
inside the same schema: this check rests on a documented H2 function while the session migration's `LONGVARBINARY`
rests on an undocumented legacy alias. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-MFA-003. Amend the table by ID, not this list.*

**3. `last_used_counter` carries a database default of `-1` as well as the field initialiser.** Two mechanisms for one
invariant, which is correct here precisely because one of them has already been observed to fail: ticket 22 found
Lombok `@Builder` silently defeating the `-1L` initialiser without `@Builder.Default`, yielding `0L` and breaking
replay rejection on the first verification after enrolment.

**4. `VARBINARY`, never `BINARY`.** `BINARY(69)` zero-pads, and Hibernate's `Dialect#equivalentTypes` tolerates only
the varbinary family via `SqlTypes.isVarbinaryType`, excluding `BINARY` — so it should also fail validation on this
stack. Held as an assertion in the negative-test set rather than as a fact, because whether H2 2.x *reports* `binary`
for such a column is a metadata question and H2 1.x reported `varbinary` for everything (HHH-9835). *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CFG-003. Amend the table by ID, not this list.*

**5. `PENDING_TOTP.created_at` is omitted.** `expires_at` at 15 minutes is adopted and is what the confirmation path
enforces; nothing reads a creation timestamp, and ticket 12's own rule is not to add a column nobody reads. Both MFA
tables share one migration file — `V5__totp.sql` — because they must size identically (Recipe 10 copies the blob
verbatim with no re-encryption) and splitting them is how the two widths drift.

**Table shape:** both tables key on **`user_id` as a shared primary key**, simultaneously PK and FK to `users(id)`,
which enforces one row per user structurally and is what makes "enrolled = a row exists" free. Not keyed on username,
despite the corpus's `findByUsername`: the user is already loaded on every path that touches these rows. `ON DELETE
CASCADE` on both, so the factor-reset and account-delete paths converge — and consequently **deletion changes the
enrolled-admin count through a table its statement never names**, which is why ticket 11's guard now locks
`totp_user_details` on all four mutating paths in the order `users` → `totp_user_details` → session rows.
`PIN_USER_DETAILS` is not created.

---

## Amendment from ticket 09 (§R, the ticket 21 reopening) — your tier 2 owes a session kill, and your counting rule is vindicated as a divergence

### 1. Your cumulative reading stands, and the password axis deliberately does not copy it

The reopening told ticket 09 to "copy" your tier 2. It does not, and your design is the reason why. You chose
cumulative-never-reset because a legitimate admin's morning login would zero the counter and your 55%-per-year
arithmetic would return — correct for a freshly-bound, admin-only, cheaply-rebindable secret in a 10⁶ space.
**Ticket 09 takes consecutive-with-reset-on-success**, because a password is held by every account for the life of
that account and cumulative counting there fires as an availability failure with no attacker present.

So the same integer, **100**, now appears on both axes under **different counting rules**. That is recorded in both
tickets and in the glossary, because a reviewer comparing them will otherwise read one control. NIST also supports
the split more directly than either ticket first cited: §3.2.2's reset SHOULDs are scoped to "the authenticators
that were used", so per-authenticator scoping is the standard's own requirement, and its AAL clause independently
forbids resetting *your* counter from a password-only AAL1 session — which is what your "subject to the AAL ceiling"
was reaching for. *Consolidated into the register (ticket 33): R-LCK-008. Amend the table by ID, not this list.*

### 2. Your automatic tier-2 trip owes a session termination, and it has no producer today

Ticket 11's endpoint table guarantees "target's sessions killed" for the admin `DELETE /api/admin/users/{uuid}/totp`
path. **The automatic tier-2 trip is a different producer and inherits nothing.** A factor you have just declared
presumed-compromised must not leave the session it authorised alive — ASVS **7.4.2 (L1)** on ticket 09's axis, and
the same requirement here.

**It cannot be atomic with the disable, and a draft that said it could was wrong.** Spring Session 4.1.x configures
`PROPAGATION_REQUIRES_NEW`, so every session write suspends the caller's transaction and commits independently; and
a row lock cannot be released early, because locks release at commit or rollback only. See
[§19 of the verification asset](../research/boot-4.1-actuator-observability-and-nist-throttling-verification.md).
**So follow ticket 08's rule, which now covers all five triggers plus yours: session rows only after commit, never
inside the lock.**

This is the one place your §5 no-fail-open rule needs reading carefully rather than applied whole. Your rule is
right about the *lock*: a lock timeout on the TOTP path is a failed authentication, because the lock is what makes
replay rejection atomic. It cannot extend to the session kill, because that kill was never inside your transaction
to begin with. Doing it inline to force atomicity would demand a second pooled connection while your lock is held,
putting the connection pool into the lock graph — thread occupancy, which is the mode ticket 09 §11 declined
sleep-based backoff over.

### 3. A distinct audit reason, so the stream can tell a trip from an operator

Your tier-2 disable row needs its reason split: **automatic trip** versus **operator action**
(ticket 11's `totp-remove`, and now also ticket 09 §R.3's operator rebinding runner with `--scope=totp|both`). One
value for both collapses a machine decision and a human one, and ticket 25's break-glass runbook keys off exactly
that distinction.

### 4. A tier-2 disable now forces password rebinding, and the arithmetic belongs in your ADR

Ticket 09 §R.9 takes the **narrow** reading of §3.2.2's multi-authenticator sentence — stage-2 failures involved a
password that *succeeded*, so that authenticator did not fail — with your conjunction rule covering the genuine
simultaneous case. But it takes the best argument for the wide reading seriously: **every tier-2 increment cost a
valid password, so 100 factor failures are 100 confirmations that the password is known.**

Middle path: a tier-2 disable is a credential-compromise signal that **forces password rebinding at next login**,
hosted on ticket 11's forced-change *flag* (evaluated after a successful authentication, so it carries no oracle),
never on ticket 11's 30-day expiry, and with `credentialIssuedAt` not stamped.

**The arithmetic you own goes in that ADR:** because your tier 2 is cumulative and never resets, the wide reading
combined with it would guarantee a legitimate admin eventually destroys **both** authenticators with no attacker
present. That is the strongest argument for the narrow reading, and it comes from your counting rule rather than
from the text. *Consolidated into the ADR routing (ticket 34): ADR-027 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-LCK-008. Amend the table by ID, not this list.*

### 5. Your rate-limit rows gain a sibling axis

Ticket 09 §5 adds a third axis — distinct accounts a source has driven into tier-1 lockout, cardinality `k ≈ 5` per
hour — to bound the mass primitive its cap created. It is **password-axis only**. Your verification endpoint sits
behind an authenticated session, so the primitive has no analogue here: every guess costs a password, which is the
precondition your whole lockout argument rests on.

---

## Amendment from ticket 25 (operational handover document)

**§8's inventory gains a fourth row, 6.4.4's N/A does not survive, and one NIST citation is reframed.**

**1. §8 gains a fourth row, marked *not a pathway*.** Two channels post-date that table: ticket 25's break-glass
path (clears a tier-2 disable and a TOTP enrolment without an authenticated admin session) and ticket 09 §R.3's
rebinding runner (mints a redemption token from the shell). Neither is a new authentication pathway, and **§8's
own carve-out already reaches them** — "redemption sets a credential and mints no session" — but that sentence was
written about *user-initiated* redemption and now has to carry an operator-minted token and an out-of-band
enrolment clear. State it, because a reviewer reading §8's structural consistency claim asks about break-glass
first.

Worth knowing why this is a row and not a finding: **"authentication pathway" is undefined throughout ASVS 5.0,
including Appendix A's glossary**, and 6.1.3's text mentions neither sessions nor credential recovery. So whether
either channel is a "pathway" is interpretation, not quotation, and an earlier draft of ticket 25 that claimed
this inventory was *stale* was overreaching. *Consolidated into the register (ticket 33): R-MFA-014. Amend the table by ID, not this list.*

**2. ASVS 6.4.4 (L2) is regraded from N/A-with-rationale to satisfied-by-parity.** Verbatim: "Verify that if a
multi-factor authentication factor is lost, evidence of identity proofing is performed at the same level as during
enrollment." The **trigger is the loss event**, not the existence of a proofing process, and the obligation is a
**parity** obligation benchmarked on enrolment rather than an absolute floor — ASVS never requires identity
proofing anywhere. So "N/A because we perform no identity proofing" reasons from the wrong half of the sentence,
and adopting a documented break-glass path that clears a lost TOTP enrolment removes the basis for the label.

The parity argument, stated so it is auditable: this ticket's enrolment sits inside an authenticated
password-verified session with the username taken from the `SecurityContext`, and break-glass requires
**deploy-level shell access**, which subsumes reading and writing the row. Strictly stronger, so the verdict lands.
Note the condition though — parity would **fail** if break-glass were ever made weaker than the enrolment path. *Consolidated into the register (ticket 33): R-RUN-001. Amend the table by ID, not this list.*

**3. ASVS 6.5.6 (L3) added as the supporting positive citation**, and it was cited nowhere on this map: "Verify
that any authentication factor (including physical devices) can be revoked in case of theft or other loss." It
supports rather than carries, being L3; 6.4.4 above is the binding companion a reviewer reaches first. **6.4.3
(L2) is not threatened** — it constrains the forgotten-password process, and this ticket plus 09, 10, 19 and 22
have already pinned tier 2 and enrolment state as cleared only by rebinding. *Consolidated into the register (ticket 33): R-RUN-001. Amend the table by ID, not this list.*

**4. The §4.1.2.1 citation is reframed, conclusion intact.** "NIST §4.1.2.1 endorses password-only **first
enrolment**" is the wrong frame: §4.1.2.1 is titled *Binding an Additional Authenticator* and is post-enrolment,
with first enrolment deferred to SP 800-63A. The conclusion survives unchanged, because our seeded admin binding
TOTP to an account whose current capability is AAL1 **is that section's own worked example** — "binding an
authenticator that is suitable for use at AAL2 requires authentication at AAL2 unless the subscriber account
currently has only AAL1 authentication capabilities." Restate it that way so a reviewer checking 800-63A finds *Consolidated into the register (ticket 33): R-ADM-016. Amend the table by ID, not this list.*
nothing missing. *Consolidated into the register (ticket 33): R-ADM-016. Amend the table by ID, not this list.*

**5. Citation hygiene, inherited from ticket 25's verification.** Anything on this ticket citing the binding-code
channel prohibition or the 10-minute cap belongs to **§4.1.2.2 "Binding Across Endpoints"**, not §4.1.2.1; and
§4.1.2.2's own **112-bit binding-code length** must not be confused with the 112-bit storage boundary in §3.1.2.2.
Publish per-section URLs — the single-page NIST render strips section numbers.

---

## Amendment from ticket 15 (threat model) — one lock-order inversion, and one layer you are the reason we do not have

Two findings, both from putting your decisions beside tickets 09 and 11 rather than from re-reading yours. The
first is a defect with one right answer; the second is a consequence of a decision that was correct.

### 1. The tier-2 trip inverts the pinned lock order — TM-03

Three tickets independently pinned the same order: **user rows → TOTP rows → session rows** (`09:1324-1327`,
your own `23:565-567`, `11:586-589`). Your verification path takes the TOTP row lock across
load → decrypt → compare → write, and that is right — the atomicity *is* the replay control, and it is why you
deliberately do not fail open there.

But on the hundredth cumulative failure the tier-2 trip also sets **`users.force_password_change`**, per your
own amendment §4 and ticket 11 §R.4. That path therefore acquires `totp_user_details` **and then** `users`:
the pinned order, inverted, in the one place three tickets agreed it must not be.

The counterparty is `AdminActionGuard`, which takes `users` → `totp_user_details` on **all four** mutating
paths (`11:731-740`). That is a textbook deadlock pair. H2's pinned 1-second `LOCK_TIMEOUT` bounds it, so the
outcome is not a hang — and the two sides fail **asymmetrically**, which is the part worth having written down:

- the guard's side surfaces as ticket 13's **row 34** with reason `LOCK_TIMEOUT`, which is correct and visible;
- your side **does not fail open**, by your own decision, so the same contention rejects a legitimate admin's
  **correct** TOTP code as a failed authentication — and, because tier 2 is cumulative and never resets, that
  rejection also increments the counter that is about to disable them.

**Amendment: the trip acquires the `users` row first, before the TOTP row.** Forced rather than open — it is
the order the rest of the map already follows, and the alternative (dispatching the flag after commit, on the
session-kill precedent) is worse here, because it would put a credential-compromise consequence outside the
transaction that detected it. Ticket 16 owes the assertion; it is cheap to state and near-impossible to
discover by accident, since it needs contention on two tables at one instant. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-MFA-007. Amend the table by ID, not this list.*

### 2. The factor gate is single-layered, and abandoning the annotation is why — TM-04

Ticket 11 applies the authorization matrix in `authorizeHttpRequests` **and** `@PreAuthorize` on the service
methods, and calls the annotation defence in depth (`11:177-181`). You then abandoned
`@EnableMultiFactorAuthentication` precisely **because** it would AND the factor into every rule in *both* web
and method security (`23:240-244`) — which was the correct call for the reason you gave: one global
`DefaultAuthorizationManagerFactory` cannot hold two `validDuration`s, and it would demand the factor of
regular users.

The consequence nobody wrote down is that the factor requirement now exists in **exactly one place**. The role
gate has two independent layers; the factor gate has one, and it is the layer expressed as request matchers —
including the **method-aware GET-versus-mutation split** that carries the difference between an 8-hour type
guard and a 10-minute re-verification. So a mis-written matcher silently downgrades or removes the factor with
nothing behind it, while the identical mistake on the role is caught by the annotation.

This is not a reason to reverse your decision. A method-level `@RequiresFactor` was considered and is **not**
recommended: it rebuilds the global-bean problem you escaped, and a second hand-maintained list of which
methods need which duration is a second thing to get wrong.

**Amendment: state the asymmetry**, and give ticket 16 an assertion **enumerated from ticket 11's matrix**
rather than hand-written — for every admin route and method, a session holding `ROLE_ADMIN` and
`FACTOR_PASSWORD` but no `FACTOR_TOTP` is refused; for every mutation, a factor older than 10 minutes is
refused. Enumerating from the matrix is the whole point: a hand-written list omits the route added next year,
which is the failure this finding is about. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-MFA-002. Amend the table by ID, not this list.*

One thing checked and found **safe**, recorded so nobody re-derives it: `HEAD /api/admin/users` does not match
a `GET`-scoped `PathPatternRequestMatcher`, so it falls through to the mutation rule and gets the *stricter*
10-minute requirement. Fails closed. `OPTIONS` never reaches the chain, because CORS is processed ahead of
Spring Security. *Consolidated into the ADR routing (ticket 34): ADR-026 (attached amendment). Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-MFA-003. Amend the table by ID, not this list.*

### 3. Your §8 fourth row has a threat model now

Ticket 25 §10 gives you a fourth `## 8` row marked *not a pathway* for the break-glass path and the rebinding
runner. That grading is right — neither grants a session — and ticket 15 modelled both anyway, because both
mutate state your factor model otherwise protects: see its **TM-12** (new [ticket 28](28-out-of-band-privileged-channels.md)),
**TM-13** and **TM-14**. The one that touches you directly is TM-14: clearing an enrolment through either
channel changes the enrolled-admin count from outside any transaction `AdminActionGuard` could join, which
makes it the **third** such channel after ticket 12's cascade and ticket 11 §R.3's NIST cap. *Consolidated into the register (ticket 33): R-MFA-014. Amend the table by ID, not this list.*

---

## Amendment from ticket 28 — the break-glass route for tier-2 and lost authenticator

The break-glass path you sent to ticket 25 now runs through the rebinding runner with scope `totp` or `both`,
offline. Its existence check admits **any enrolled factor in any lock state**, not only tier-2. An earlier draft
required the tier-2 state and so refused the lost-authenticator case, which ticket 25 names as the primary one. The
runner destroys the factor row, and re-enrolment follows through your normal flow. It bypasses your two-admin
guard by design and reports pre- and post-operation enrolled-admin counts
([ticket 28](28-out-of-band-privileged-channels.md) §4, §10). *Consolidated into the register (ticket 33): R-RUN-001, R-STD-037. Amend the table by ID, not this list.*

---

## Amendment from ticket 30 — factor reset leaves the two-admin count

23:30, 23:601–603 and 23:630–633 route `DELETE /api/admin/users/{uuid}/totp` through the two-enrolled-admins
invariant. [Ticket 30](30-sole-admin-bootstrap-premise.md) §3 exempts it from the **count**; `AdminActionGuard` still
applies `actor ≠ subject`, and the session kill and `PENDING_TOTP` deletion are unchanged. The argument rests on this
ticket's 23:621–623: after a reset the subject re-enrols unaided, so the count is restored without another admin.
Side effect: a tier-2 disable (23:494) at exactly two admins was also refused by the guard, and is now clearable in-app.
