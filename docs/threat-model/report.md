# Threat model report — Secured Hello World Auth App

Why this artefact exists and when it is updated: [ADR-064](../adr/0064-threat-model-is-a-plan-artefact.md).
Model: [`secured-hello-world.json`](secured-hello-world.json) (OWASP Threat Dragon v2.2.0 schema, 3 diagrams,
14 threats). Cross-references: [ADR index and rejection log](../adr/README.md) (`ADR-nnn`, `REJ-nnn`),
[deferral register](../register/register.md) (`R-…`), [test plan](../test-plan/test-plan.md) (`T-…`).

External facts rest on these primary sources:

- Spring Security reference, [Servlet Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
  — filter ordering: exploit protection (headers, CORS, CSRF), then authentication, then authorization.
- Spring Framework 7.0.9 javadoc,
  [`DeserializingConverter`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/serializer/support/DeserializingConverter.html)
  and [`DefaultDeserializer`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/core/serializer/DefaultDeserializer.html)
  — the deserialisation path and its `getObjectInputFilter()` hook.
- Spring Session reference, [JDBC configuration](https://docs.spring.io/spring-session/reference/configuration/jdbc.html)
  — documents no serialization or conversion-service configuration.
- [W3C Trace Context](https://www.w3.org/TR/trace-context/), W3C Recommendation — §4.3 (a received `traceparent`
  is used), §7 (defensive parsing of the headers) and §7.2 (contrived `trace-id` collisions as denial of monitoring).

Methodology: **STRIDE**, per the `owasp-threat-modeling` skill, applied per DFD element type. LINDDUN was
considered and **not** run as a separate pass: the personal data in scope is one email address per account plus
an IP address, and both already carry decided privacy positions — the tombstone HMAC as pseudonymisation with its
limit stated (ADR-044), `source.ip_hash` with a rotation floor (ADR-054). A LINDDUN pass would restate them.
The one LINDDUN-shaped finding that does not restate anything is TM-09, filed under Repudiation.

## 0. How to read this, and what it is not

The design under test is **exhaustively worked**. The design decisions name, by my count, well over a hundred
threats, oracles, leaks, fail-open modes and residuals, and most of the obvious STRIDE findings for a
cookie-session auth app are not merely mitigated but argued, priced and tested. Reporting those back would be
noise.

So this model's value is deliberately narrow. It is in three places:

1. **Composition** — two individually-correct decisions that combine badly. Each decision saw its neighbours; none
   saw the whole. Six of the fourteen findings are of this kind, and the two graded High both are.
2. **The DFD itself** — drawing the data flows surfaced controls that run at a *band* rather than at an endpoint,
   and therefore apply to paths no endpoint-keyed registry covers. That is TM-01.
3. **What arrived late** — the model was scoped against the session, lockout, credential-flow, admin, data-model
   and deployment-topology decisions, and two privileged channels were designed after all of those closed. TM-12,
   TM-13 and TM-14.

**Inherited threats are recorded as `Mitigated` with the owning decision cited**, not re-derived, so the `Open`
set in the model is exactly what the decisions missed. TM-11 is the single inherited risk carried explicitly,
because its actor — the log reader — has to appear on the DFD or the diagram misrepresents the system.

## 1. The DFD

Built from the design decisions, not from `prd/assessment-prd.md`. The handover document declines IM8 pm-6's
network-topology-and-data-flow diagram on the grounds that this data-flow work and the ADRs already carry it
(ADR-064) — so these three diagrams are the artefact that reference points at.

### Level 0 — origins, stores, trust boundaries

Six trust boundaries, and two of them are the finding-rich ones:

| Boundary | What crosses it | Note |
|---|---|---|
| Untrusted browser / network | everything | **No TLS terminator is nominated anywhere.** Production TLS is a documented requirement with an unfilled role, not an environment (R-OPS-003). |
| SPA origin `localhost:5173` | the bundle, the ten-directive CSP | Cross-origin but **same-site** with the API (ADR-059, R-OPS-002), which is the only reason `SameSite=Strict` survives (ADR-058). |
| API origin `localhost:8080` | credentialed fetch + direct non-browser requests | Decomposed in Level 1. The browser controls (SameSite, CORS) do **not** apply to the direct flow, which is where TM-01 and TM-02 live. |
| Local disk — one H2 file | JDBC as an **H2 administrator** | Least privilege attempted and declined on h2database#2846 (R-DATA-015). The same file holds the application tables *and* `SPRING_SESSION`, whose `SESSION_ID` is the cookie value. |
| Observability — stdout + rolling file | audit NDJSON, the dev reset link, **and the rebinding token** | ASVS 16.4.2 (L2) and 16.4.3 (L2) both F (R-AUD-012, R-AUD-013). One descriptor, multiple writers: the whole of TM-12. |
| Secret material — `configtree:` + env | three 32-byte keys + bootstrap credential | OS environment variables override imported config data, which is why the startup fingerprint exists (R-CFG-022). |

Six external entities. Four are the expected ones; two are actors the design established and a naive DFD would
omit — the **log reader** (a real actor, not a hypothetical: R-CRED-020) and the **shell holder**, whose
reachability the handover records as the one obligation in the entire handover that cannot become an enforced
check (R-RUN-003).

### Level 1a — the API as filter bands

The ordering *is* the design, and it is verified rather than assumed (Spring Security reference, Servlet
Architecture): exploit protection, then authentication, then authorization. Nine processes:

`per-IP limiter` → `AbsoluteSessionLifetimeFilter` → `CsrfFilter` → `forced-change filter` → `Route C login
filter` → `TOTP filters` → `AuthorizationFilter + factor rules` → `handlers` → `audit emitter`

The **pre-routing band** is drawn as its own boundary because everything inside it sees client bytes as
`url.path` and runs before any authorization decision. That is not a presentational choice; it is the structural
fact TM-01 rests on.

### Level 1b — out-of-band privileged channels

Added after the handover design closed. Neither channel issues an HTTP request, holds a session, presents a
factor, or routes through `AdminActionGuard`; both mutate state the factor model otherwise protects. The
authentication pathway inventory (R-MFA-014) marks them "not a pathway" because neither grants a session —
correct for its purpose, and the reason they need modelling separately rather than being assumed covered.

## 2. STRIDE pass per flow

The model's scope names eight flows. Recording all eight, **including the ones that produced nothing new**,
because a flow that came back clean is a result.

### 2.1 Anonymous → login

Credential stuffing, brute force, enumeration, timing, limiter interaction. **Nothing new.** This is the most
heavily worked flow in the design and it holds up: the uniform 401 collapses five account states with `instance`
pinned to a constant so the body is literally equal across them (ADR-031, ADR-033); timing rests on the
framework's dummy-hash mitigation plus three ordering rules and no artificial floor (REJ-056); the two limiter
axes are keyed on the *submitted* string rather than a resolved account, structurally rather than by convention,
because the converter holds the raw string before any repository lookup (ADR-010, ADR-015); the NIST SP 800-63B-4
§3.2.2 cap sits in `preAuthenticationChecks` specifically so its audit reason cannot confirm a correct password
(ADR-013, REJ-019). The one remaining oracle on this flow — presence or absence of `user.id` on a failure row —
is recorded as an unavoidable log-reader oracle and priced (R-AUD-007, REJ-042).

The mass permanent-lockout primitive, handed in pre-worked from the lockout design (36 disables/hour from one host,
discovery via `USERNAME_UNAVAILABLE`, 7× reduction from the cardinality axis, defeated by IP rotation), was
**stress-tested first**, and the arithmetic holds. `3600 ÷ 100 = 36` is right; the escalating ladder is genuinely
throughput-neutral because it changes when the 100 requests land, not how many; and the residual pricing is honest
rather than flattering. It is carried unchanged into the accepted-risk set (R-LCK-005).

### 2.2 Anonymous → registration

Enumeration via the generic response, verification-token guessing, mass account creation, notification harassment.
**Nothing new.** The CVE-2026-48117 pre-hijacking shape is closed at source by moving the credential to
redemption, which also deletes a 50–100× timing oracle rather than masking it (ADR-032). `USERNAME_UNAVAILABLE` is
a deliberate, bounded, recorded failure of ASVS 6.3.8 (L3), and the squat is unavoidable under every alternative
(R-AUTH-001).

### 2.3 Anonymous → password reset request and redemption

Token guessing, token leakage, reset-to-takeover, concurrent-redemption race. **Nothing new on the mechanism.**
256-bit tokens, domain-separated `SHA-256(type ‖ ":" ‖ token)`, single-use via a conditional `UPDATE` so token
rows never enter the lock ordering (ADR-007), three negative assertions on link-origin derivation with a shipped
CVE behind each (REJ-022). The concurrent-redemption race is closed by the conditional update being one statement.

One finding, conditional: **TM-13**, on the *recovery*-code sibling of this flow that the break-glass design
(ADR-070) created.

### 2.4 Authenticated user → protected endpoints

Session fixation, session theft via XSS, CSRF given the session-bound synchronizer token, privilege escalation.
Session fixation is closed by rotation-on-login with `CsrfAuthenticationStrategy` explicitly in the composite —
the session design's most consequential finding (ADR-036, ADR-038; T-SES-005). XSS is bounded by a CSP whose
`script-src 'self'` posture rests on there being no inline script in the production document, with a reopening
trigger if that changes (ADR-059, ADR-060).

Two findings: **TM-06** (the session store's deserialisation side, which nobody examined) and **TM-05** (`GET
/api/hello`, PRD Story 5's only endpoint, which no design decision owned).

### 2.5 Authenticated user → self-service password change

The scope asked specifically whether the change endpoint is also limited, since it is an authenticated bypass of
the login limiter. **It is** — 10/min per source IP — and the design is better than the question implies: the
current-password check uses `passwordEncoder.matches()` directly rather than the `AuthenticationManager`, so it
publishes no events and cannot feed the lockout counter, which closes a session-compromise-to-DoS conversion
(ADR-008). Nothing new.

### 2.6 Admin → user management

Self-action guard bypass, IDOR on target IDs, mass-disable as DoS, tombstone abuse, role escalation. The guard is
one central service method with an architecture test forbidding controllers from reaching repositories, under a
pessimistic lock that counts rows in Java because H2 rejects `FOR UPDATE` on aggregates (ADR-048). IDOR requires
ADMIN plus a live factor and the identifiers are v4 UUIDs, deliberately not v7 because a sortable public
identifier would disclose creation time (ADR-050).

Three findings: **TM-04** (the factor gate is single-layered while the role gate is double-layered), **TM-08**
(the flat ADMIN role permits admin-on-admin takeover, which no decision states as a whole) and **TM-09** (the
tombstone records no role).

### 2.7 Scheduled and background paths

The scope asked whether any remain once hygiene jobs are out of scope. **Three do, and none is a scheduler:**
Spring Session's JDBC cleanup cron (which now also hosts the startup reconciliation sweep, ADR-039), the audit
appender's rolling policy, and the two out-of-band CLI channels. The sweep is the interesting one — it exists
because `PROPAGATION_REQUIRES_NEW` makes a session kill non-atomic with the state change that triggers it, and it
selects every user with `password_disabled_at` set, which is why the data model indexes that column. Nothing new;
recorded because "no scheduled jobs" is true of the deferral and false of the DFD.

### 2.8 Log and audit flow

Log injection via username or email, PII leakage, secret leakage. This is where the model paid.

Injection: `url.path` as the **matched route pattern** deletes the surface on every handler-scoped row and leaves
it only on pre-routing rows, with a CRLF strip and a length cap at the single emitter (ADR-055, REJ-081). PII:
`user.id` only, `source.ip_hash` never the address (ADR-054), closed reason enums with no free text. Secret
leakage: `emit` **exposes no throwable parameter**, which closes the general form of the `.setCause(e)` leak
structurally rather than by assertion.

Three findings: **TM-01** (High — audit volume on pre-routing rows is attacker-set, invalidating published disk
arithmetic), **TM-02** (the correlation join key is caller-supplied) and **TM-10** (row 46's cap, the only bound
on audit volume, has no value, key or test).

### 2.9 Out-of-band privileged channels — added

Not in the original flow list because they did not exist when it was drawn up. **TM-12** (High), **TM-13**,
**TM-14**.

## 3. Findings

Severity is likelihood × impact in the Threat Dragon sense; the column that matters operationally is the last one.

| ID | Flow | STRIDE | Sev | Mitigated by the current design? | Residual |
|---|---|---|---|---|---|
| TM-01 | log/audit; any unbudgeted route | D, I, R | **High** | No. Row 46 bounds the wrong rows. | Audit volume, and therefore disk sizing, set by an unauthenticated attacker |
| TM-12 | out-of-band | I, R | **High** | Partly, and the control does not reach the platform | Credential of last resort on a collected stream, in every profile |
| TM-02 | log/audit | S, T, R | Med | No | Correlation chain forgeable; the audit correlation join key untrustworthy |
| TM-03 | TOTP tier-2 trip | D | Med | No — the pinned order is inverted here | Deadlock pair with `AdminActionGuard`; a correct code rejected |
| TM-04 | admin surface | E | Med | Single-layered | A mis-written matcher removes the factor with no second layer |
| TM-06 | session store | T, E | Med | Unexamined | JDK deserialisation path with unstated filter posture |
| TM-07 | all password paths | D | Med | Per-key only | Aggregate CPU and thread occupancy unbounded across sources |
| TM-08 | admin → admin | E | Med | Not as a whole | Any admin can take over any admin; detection is one audit row |
| TM-13 | recovery | E | Med | Contingent | Containment claim inverts when mail transport lands |
| TM-14 | out-of-band | E | Med | Guarded on mutation paths only | Monitoring diverges from the guard in the incident it exists for |
| TM-05 | `GET /api/hello` | E | Low | **Yes — fails closed** on `denyAll()` | A PRD acceptance criterion with no design owner |
| TM-09 | tombstone | R | Low | No | Privileged deprovisioning loses its evidence at 90 days |
| TM-10 | log/audit | D | Low | Named, not specified | The only audit-volume ceiling has no number |
| TM-11 | log reader | I | High | Yes — ADR-057, ADR-023; R-CRED-020, R-CRED-021 | Inherited, priced, profile-dependent |

### 3.1 Design changes required

Some findings graduated into follow-on decisions; the rest amended existing ones. Which, and why, per the reopening
rule (REJ-048) — *amend unless the amendment weakens a compensating control standing in for a declined `SHALL`, in
which case reopen*. Each item names the IDs that now hold the outcome.

**→ Follow-on decision — TM-01, TM-05 (budget half), TM-10. Now ADR-017, ADR-018, ADR-019; R-OPS-006; T-RL-016,
T-AUD-034, T-ARCH-005.** A new decision rather than an amendment because it spans three existing ones (the
limiter, the audit catalogue's row 46, and the disk arithmetic) and the remedy is a genuine open decision — what
the limiter does for a route nobody listed — not a correction with one right answer. No reopening: row 46 is a
compensating control for an amplification path, not for a declined `SHALL`.

**→ Follow-on decision — TM-02. Now ADR-063; R-OBS-015; T-OBS-002, T-OBS-003, T-OBS-004, T-AUD-028.**
No earlier decision owned inbound trace context; the logging design adopted the OTel starter *for* `trace.id` and
never asked where it comes from. The remedy has real options (restart at the boundary, gate continuation on
authentication per the W3C specification's own suggestion, or accept and stop relying on the join key).

**→ Follow-on decision — TM-12, plus three smaller findings on the same channel. Now ADR-072, ADR-073, ADR-074;
R-AUD-034; T-AUD-027, T-RUN-003, T-RUN-011.** The runner channel came from the lockout design's recovery section
and the prohibited-configuration entry from the configuration design, but neither owned the premise that stdout is
collected, and the remedy is a choice between three mechanisms. Also carries: the runner's audit row is the only
privileged row with no nameable actor; the runner bypasses `AdminActionGuard`; the batch form has no stated
dry-run; and invocation must be argument-gated rather than property-gated or a leftover environment variable
re-fires it on every restart.

**Amend the tier-2 TOTP trip (ADR-027) — TM-03.** Forced, not open: the trip must acquire the `users` row before
the TOTP row, because that is the order the rest of the design already follows (ADR-039, ADR-048). T-MFA-007 owes
the assertion.

**Amend the authorization matrix and the factor rules (ADR-043, ADR-026) — TM-04.** State the layering asymmetry
and give the test plan an assertion enumerated *from* the matrix (T-MFA-002). A method-level `@RequiresFactor` was
considered and is not recommended: it rebuilds the global-bean problem ADR-026 escaped.

**Amend the authorization matrix — TM-05 (matrix half).** One row (REJ-055).

**Amend the two-admin invariant and the observability signal (ADR-048, R-OBS-007) — TM-14.** The invariant is
guarded on mutation paths and *monitored* everywhere; both out-of-band channels join the
zero-authenticable-admins signal.

**Amend the mail-transport trigger (R-CRED-026) — TM-13.** The consequence attaches to a trigger the handover
already records. Explicitly **not** a reopening, and the reasoning is stated in the model so a later reader can
disagree with the call rather than guess at it: the weakening is contingent on a trigger that has not fired.

### 3.2 Build-phase controls

Requirements to carry into the spec. Each is a test or an assertion, not a decision.

1. **TM-06** — confirm the effective `ObjectInputFilter` on Spring Session's deserialisation path as a
   first-implementation check, then install an allowlist over the attribute types actually stored
   (`SecurityContext`, `FactorGrantedAuthority`, `AUTH_INSTANT`, the CSRF token). Handed over as well, because a
   future real database inherits the path. (T-SES-025; R-SES-003.)
2. **TM-04** — the factor assertion enumerated from the authorization matrix: for every admin route and method, a
   session with `ROLE_ADMIN` + `FACTOR_PASSWORD` and no `FACTOR_TOTP` is refused; for every mutation, a factor
   older than 10 minutes is refused. Enumerated, because a hand-written list omits the route added later, which
   is the failure the finding is about. (T-MFA-002.)
3. **TM-03** — assert the tier-2 trip's lock acquisition order. (T-MFA-007.)
4. **TM-07** — pin and document `server.tomcat.threads.max`; keep the Tomcat and Hikari saturation meters, which
   are the only evidence for the thread-exhaustion argument behind declining sleep-based delay (ADR-014).
   (T-OBS-005; R-RL-005.)
5. **TM-13** — assert the recovery-code route and the recovery-address confirmation are structurally unavailable
   while the transport is the stub. (T-CRED-023.)
6. **TM-12** — assert invocation of the rebinding runner is argument-gated, not property-gated: a bound property
   with the runner's name must not trigger it. (T-RUN-003.)
7. **TM-02** — assert `traceparent` is length- and content-validated before use, per W3C Trace Context §7's
   defensive-parsing obligation, independently of the continuation decision (ADR-063).
8. **TM-01** — assert a request to a route with no budget row is nonetheless budgeted. (T-RL-016.)

One narrowing rather than a control: **the credential-flow design's "the unauthenticated-BCrypt resource lever
disappears" is true of registration only.** `POST /api/register/activate` and `POST /api/password-reset/confirm`
both run the full four-BCrypt `PasswordService` sequence unauthenticated, bounded by token possession. The
register carries the narrowed claim so a later reader does not inherit the broad one (R-CRED-016).

### 3.3 Accepted risks

Written as handover rows rather than prose, per the declaration rule — the register and the handover document are
two renderings of one generated table (ADR-069), so an accepted risk written as prose is a row nobody can
generate. The rows this model contributed are R-OBS-013 (audit disk sizing and the truncation-row alert), R-RL-005
(aggregate capacity and saturation meters), R-SES-003 (session deserialisation filter), R-ADM-009 (admin-on-admin
takeover), R-ADM-010 (tombstone role) and R-OBS-015 (inbound trace restart, which replaced the upstream-proxy
item). The collected-stdout item was overtaken by ADR-073, which emits no secret on the runner path (R-AUD-034).

The six listed going in, reconciled against how the design actually resolved:

| Going in | Status now |
|---|---|
| Local HTTP | **Stands.** Plus the sharper form: nobody is nominated to terminate TLS (R-OPS-003). |
| Stubbed email | **Stands, and is two risks, not one** — total leak in `dev`, no channel at all outside it (R-CRED-020, R-CRED-021). |
| No MFA | **Withdrawn.** TOTP is in scope for the admin surface (ADR-023). What survives is *no MFA for regular users*, which is an out-of-scope decision rather than an accepted risk (R-MFA-015). |
| No durable audit store | **Stands** as ASVS 16.4.2 (L2) / 16.4.3 (L2) F, both with named deployer obligations (R-AUD-012, R-AUD-013, R-AUD-022). |
| Single-instance in-memory rate limiting | **Stands** (R-RL-006), and TM-07 adds the aggregate half nobody had stated (R-RL-005). |
| Enumeration via any surviving path | **Stands**, narrowed to one axis: `USERNAME_UNAVAILABLE` at registration, ASVS 6.3.8 (L3), bounded at 5/min per source (R-AUTH-001). |

Three added: **TM-08** (flat ADMIN, no separation of duties; R-ADM-009), **TM-09** (tombstone role; R-ADM-010),
and the mass permanent-lockout residual carried unchanged from the lockout design after stress-testing (R-LCK-005).

**TM-08 widened by ADR-049.** Factor reset is now exempt from the two-admin count, because at exactly two admins the
guard refused legitimate lost-phone recovery. So at exactly two, A can take B over completely (password plus
factor); at three or more it was already open. Detectors: row 18 `ADMIN_RESET`, row 33 `totp-remove`, and — only
if the deployer enables export — the authenticable-admins gauge below 2 (R-OBS-007).

## 4. What I could not verify

Stated so the compliance review does not read confidence into it.

- Whether Spring Session's default conversion service installs **no** deserialisation filter (TM-06). The hook
  exists (`DefaultDeserializer.getObjectInputFilter()`, Spring Framework 7.0.9 javadoc); the default is
  undocumented in the Spring Session JDBC configuration reference. This is why TM-06 is a build-phase check and
  not a reopening.
- Boot 4.1's default trace-context propagator and whether extraction can be disabled without losing outbound
  propagation (TM-02). The standards-level claim is verified against W3C Trace Context; the framework-level one
  was left to the inbound trace-context decision to establish rather than assume (ADR-063; R-OBS-015 records the
  outcome).
- The Threat Dragon model is **schema-shaped and JSON-valid, but has not been opened in Threat Dragon** — no
  instance is available in this environment. Cell geometry is plausible rather than laid out.
