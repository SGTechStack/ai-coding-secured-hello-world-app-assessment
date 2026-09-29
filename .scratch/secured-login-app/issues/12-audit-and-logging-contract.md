# 12 — Audit and logging contract

Type: grilling
Status: resolved
Blocked by: 05, 18, 19 — all closed
Map: [Secured Login App](../map.md)

## Question

What exactly does this application log, in what shape, and what is retained?

Take the applicable requirement list produced by [05](05-logging-standards-applicability.md) and turn it into this application's concrete audit and logging contract, satisfying the standard's §3.3 Audit Contract and §3.4 Logging Contract (`Standalone_User_Access_Control_Application_Standard.md:269,294`).

Answer `Q28` (audit event retention period) and `Q30` (notification strategy for security events) of the standard's question set. On `Q30`, note the PRD stubs email entirely and MCNS notification standards are out of scope — so the answer may be "log only", but say so deliberately.

Settle:

- The event list, starting from the PRD's audit NFR (`prd/assessment-prd.md:123`): login success, login failure, lockout triggered, password reset requested, password reset completed, role change, enable/disable, delete — each with **actor and target**. Reconcile against the standard's own required event set from 05; where the standard demands more events, the standard wins.
- The field/key schema per `Log_Schema.md`, including how actor and target are represented, and what identifies an anonymous actor on a failed login (username attempted? IP? both — and does recording an attempted username at INFO create an enumeration risk in the logs themselves?).
- Masking rules: passwords never logged (PRD, Story 1), plus reset tokens/links from 11, session ids, and email addresses if the privacy policy requires it.
- Whether a typed audit module is required (per 05's finding on `Centralising_Audit_Logging_With_A_Typed_Module.md`) and where it sits relative to the service layer under §4 Separation of Concerns.
- Retention: `Q28` for audit events, and its relationship to 10's tombstone retention — if an audit row names a deleted user, the two retention periods interact.
- Where audit output goes: the PRD says no dedicated table is required, structured log lines suffice.

Blocked on 05, which establishes which of the 7,169 binding lines actually have surface here.

**Amended by [05 — Which logging standards actually bind a two-process app](05-logging-standards-applicability.md).** 05 resolved which of the 7,169 binding lines have surface here; this ticket now has a concrete, cited starting position rather than an open field. Read 05's Answer in full before grilling — the items below are the parts that change what this ticket must decide. Short names as in 05 (`Std`, `LS`, `Q`, `AuthN`, `Typed`, `Mask`, `MDC`).

**Now also blocked by [18 — Client IP in logs](18-client-ip-in-logs.md) and [19 — Structured log format and the custom encoder](19-log-format-and-custom-encoder.md).** Neither can be folded in here: 18 is a contradiction in the binding set with a PRD story riding on it, and 19 decides whether the custom encoder is built — which determines what this contract can even express.

**Settled by 05; do not re-litigate.** The mandatory-on-every-line field set and its "do not set manually" character (`Std:123-129`); NDJSON to stdout, UTF-8, RFC-3339, **Singapore Time UTC+8**, and never dropping an event for missing required fields (`Std:163-170`); level assignment INFO/WARN/**ERROR for lockout** (`AuthN:37`); `trace.id` and `span.id` on every line via mandatory Micrometer Tracing (`Std:321`, an `[Enforced Constraint]`) with **no** exporter, collector or trace platform needed (`Trace:14`); a mandatory MDC filter clearing in `finally` (`Std:323`); a mandatory global exception handler logging once at the boundary (`Std:325`); and the PRD's "plaintext password never logged" criterion satisfied **by prevention, not masking** (`Mask:24,38-58,87,150`).

**The event list is larger than the PRD's, and the size is not optional.** `Std:239` — "Audit logs must record the following:" — binds independently of whether a recipe supplies a template. Five of the PRD's seven events (reset requested, reset confirmed, role change, status change, delete) have **no** recipe template and are mandatory anyway via `Std:242,244,245`; the nearest templates cover only the *admin-initiated* paths (`Standalone_Privileged_User_Administration_and_Password_Reset.md:183-188,451-456`). Conversely roughly ten mandatory events are absent from the PRD's list: logout, session start and end (`AuthN:159,216,248`), authorisation denial and authorisation success on privileged operations (`AuthN:359,438`), `profile-read`, user creation, application startup and shutdown (`Std:252`), self-service password change (`Std:242`), repeated validation failures (`Std:249` — where IP throttling belongs), and password-change enforcement. **The audit surface is roughly 2.5× the PRD's list.** This ticket's brief already rules that "where the standard demands more events, the standard wins", so the decision is not *whether* but the concrete enumeration — produce it explicitly, because it sizes the build. `AuthN:265` pre-empts the obvious objection to session-start/session-end duplicating login/logout: it calls the duplication "expected behaviour".

**Three hard schema gaps — the core of this ticket.** Each is a contradiction inside the binding set, so none resolves by authority order.

1. **No target/resource field exists.** `Std:257` requires "what resource was affected" on every audit event, but `LS:100-106` offers only `user.id`, `user.name` and `session.hash`. The binding documents bind `user.id` **oppositely**: `AuthN`, `Typed` and `MDC` use it for the **actor** — and `MDC:320` writes it onto *every* line from inside the security chain — while `Standalone_Privileged_User_Administration_and_Password_Reset.md:185,213,244,453` uses it for the **target**. The PRD's "actor + target" requirement (`prd/assessment-prd.md:123`) has nowhere to go. Options to weigh: a non-schema `target_user_id` key; follow the privileged recipe and lose the actor; or suppress the MDC-injected `user.id` on `/api/v1/users/**` so the field can mean target there. The first looks least bad but breaks schema conformance — decide deliberately and record it as a deviation.
2. **`event.action` cannot distinguish the two admin mutations.** `LS:136` is a closed enum of ~45 values with no `user-status-change`, no `user-role-change`, no throttling action and no unlock action; both collapse to `user-administration`. `event.category` is worse — its enum is `configuration`, `network`, `database`, `batch`, `interface`, `process` (`LS:135`), with **no authentication or security category at all**; nearest is `process`. **This invalidates a stated rationale in [01 — Context, topology and API surface](01-context-topology-and-api-surface.md)** — 01's `Q9` claimed sub-resource verbs make `event.action` one-to-one with operations, which is false. 01's decision stands on its other grounds, but this ticket must supply the actual discriminator. The valves that exist: **`event.reason`** (a short machine-readable string explaining `event.outcome`) and **`event.severity`** (`low`/`medium`/`high`/`critical`, routable independently of `log.level`), both in `LS`'s Event table.
3. **A failed login against an unknown username has no permitted subject field.** `user.id` must be omitted (`AuthN:31`), a hashed username is explicitly forbidden (`AuthN:31`), and `session.hash` — defined for exactly this purpose by `LS:104,106` — is prohibited by `Q:94` "in any form, including hashed". **`trace.id` is the only field left standing**, and whether the client IP joins it is [18](18-client-ip-in-logs.md)'s call. If 18 says no, decide here what a brute-force campaign looks like in the audit trail when it is an unattributable count.

**A fourth, softer contradiction: may `user.name` ever be logged?** `LS:105` permits it "only ... in authentication/authorization contexts where explicitly permitted by policy" — precisely this application's contexts — while `Std:226`, `Std:276` and `AuthN:17` prohibit raw usernames absolutely. A policy escape hatch with no policy behind it. Either invoke it deliberately (and say which policy) or shut it; leaving it ambiguous means an implementer decides by accident.

**The typed audit module is RECOMMENDED, not mandatory** — verified across all 343 lines of `Typed`, whose §7 is titled "Key decisions". But `Std:327` **is** an `[Enforced Constraint]`: audit logs must route to a dedicated appender and destination, which needs a stable logger name to route on, and `Typed:100`'s `getLogger("audit")` is exactly that hook. So the recommended module is the cheap route to a mandatory constraint. Note also `Typed:322`: `error_*` keys use **underscores** because ECS pre-seals `error.*`. If the module is built, its placement under §4 Separation of Concerns is already in this ticket's brief.

**`Std:327`'s separate destination likely forces a `logback-spring.xml`** — it cannot be expressed in `application.yaml` alone. That is [19](19-log-format-and-custom-encoder.md)'s to confirm; this ticket consumes the answer.

**Retention is narrower than feared.** `Std:287` note: retention TTL "is enforced by the centralised log management platform ... Integrators must configure the applicable retention policy" — **not** the application. So `Q28` here is about *stating the required period for an integrator*, not implementing expiry. What does remain the application's: rotation for local file logging (`Std:260`) and a durable local rolling JSON file for a forwarding agent rather than shipping over the network from the app (`Std:275`, provided nearly free per `Std:304`). Still unowned and needing an explicit line drawn against the hosting exclusion: **`Std:259`** (audit trails must use integrity controls preventing tampering or deletion) and **`Std:262`** (alert immediately when audit writing fails — "Operations that occurred without an audit record are a compliance risk"). Rule each in scope or out with a reason; do not leave them unmentioned.

**Correlation-id ownership — decide here.** `Std:323` contemplates a correlation id arriving in a request header such as `X-Correlation-ID`, but `Std:50-51` and `MDC:46-47` make it conditional: the backend mints one when none is present. 05's recommendation is **backend mints, SPA sends nothing** — an SPA-supplied header is untrusted input with no gateway to strip it, and accepting it triggers `Std:276`'s CRLF-sanitisation obligation against log forging (CWE-117). Confirm or overturn. Note `correlation.id` itself is *not* a required field (`Std:132`); it applies only where one `trace.id` cannot span a workflow — which is [11 — Password reset flow](11-password-reset-flow.md)'s case, not the general one.

**Two MDC traps to write into the contract** so they are not rediscovered during the build: `putCloseable` closes **before** a `catch` block runs and **removes** rather than restores a pre-existing key (`MDC:233-235`) — both of which bite precisely on the failed-login and lockout paths this contract is built for; and `MDC.clear()` strips Micrometer's `traceId`/`spanId` and has no legitimate use in this application. Also `MDC:98`: Logback's built-in `MDCInsertingServletFilter` is "not recommended under this standard" — do not reach for it as a shortcut.

**Bean Validation log level is unsettled in the binding set**: `Std:97` says `ERROR`, `Q:298` says `WARN`. Pick one; it interacts with the §3.2 error contract this ticket's sibling fog patch owns.

**One artifact obligation, easy to miss.** `Structured_Logging_Application_Standard.md:283` requires maintaining "a log inventory that documents what is logged, where it is stored, how it is used, how access is controlled, and how long logs are kept". That document is essentially this ticket's output written down, so produce it as the deliverable rather than as a separate task — and note the access-control and storage columns interact with `Std:327`'s separate audit destination and with whatever line this ticket draws against the hosting exclusion.

**Amended by [18 — Client IP in logs](18-client-ip-in-logs.md).** One of this ticket's two remaining blockers is cleared; [19 — Log format and custom encoder](19-log-format-and-custom-encoder.md) is still open, so this ticket stays blocked. 18 settled `source.ip` and hands down one settled ruling, two new obligations and one correction.

- **Settled; do not re-litigate.** `source.ip` is logged **in cleartext**, sourced **only** from `WebAuthenticationDetails.getRemoteAddress()` / `request.getRemoteAddr()` (never `X-Forwarded-For`), stored **verbatim** with no IPv6 normalization, and **omitted entirely** — not set to the recipe's `"unavailable"` sentinel (`AuthN:121-126`) — on events with no request context. It appears on exactly **six** event classes: authN success, authN failure, lockout, logout, authorisation denial, and IP-throttle rejection. It appears on **no** request-start/request-end line and **never enters MDC**, which is the reading that lets `Std:52`/`Std:359` and `MDC:94`/`:98` all be followed. Write the six-event scope into the field schema; 18 carries the full clause-by-clause status and the four recorded deviations.
- **New obligation 1: define the IP-throttle rejection event.** No recipe in the binding set templates it, so its entire field set is this ticket's to author — `event.action` in particular, since 05 already found `event.action`'s closed enum lacks values for the role- and status-change cases. Check whether it lacks one here too; if so this is a *fourth* schema gap, not a third. Coupled to [07 — Lockout and IP throttling](07-lockout-and-ip-throttling.md)'s `429`-versus-generic-error decision: the response shape bounds what the audit line can say without confirming the throttle's existence to a prober.
- **New obligation 2: the audit appender's classification is now load-bearing.** Because `source.ip` lands only in the audit stream, that stream is classified **above** the application log. This is a real obligation on the integrator and it belongs in the `Std:283` log-inventory deliverable this ticket already owns — specifically in its access-control and storage columns, alongside `Std:327`'s separate-destination requirement.
- **Correction to this ticket's own citation.** It cites `Std:257` for "what resource was affected"; the clause is at **`Std:255`**. Also note what `:255` does *not* say: client IP is absent from its required-context list, which is why 18 found the `source.ip` mandate to be recipe-level only and treated the six-event scope as a deliberate choice rather than an inherited clause.
- **One `MDC:98` reinforcement.** This ticket already warns against `MDCInsertingServletFilter` as a shortcut. 18 supplies the sharper reason: `:98` rejects it *specifically because* it populates client IP (`req.remoteHost`, `req.xForwardedFor`) — so reaching for it would breach 18's scope split at its single most important point, putting IP on every line.

**Amended by [15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md).** Two structural facts this ticket inherits rather than decides. **The audit logger is bound by logger name, not by marker** — 15 chose this deliberately so that its ArchUnit row ("only `com.assessment.auth.audit` may log to the audit logger") is statically enforceable, which a marker-based binding would not be. And **`com.assessment.auth.audit` is the single owner** of audit emission; the typed module remains *recommended, not mandatory* per 05, but its home is now fixed either way. 15 placed the dedicated audit appender (`Structured_Logging_Application_Standard.md:327`) in a minimal `logback-spring.xml` writing to a rolling local file, which is where the 90-day retention evidence (`:291`) physically lives given no forwarding agent is in scope.

**Amended by [04 — Password policy and history](04-password-policy-and-history.md).** 04 added two events to this ticket's inventory and sharpened a third.

- **Password change is a mandatory `INFO` audit event, in both variants.** `Standalone_User_Access_Control_Application_Standard.md:283-284` lists "password changes (both admin-initiated and self-service)" in the audit surface and `:324` fixes the level at `INFO`. 04 ruled self-service change in scope, so this is now two distinct events with different actors: admin-initiated carries actor ≠ subject, self-service carries actor = subject. This is a live instance of 05's finding that **no target/resource field exists** in the schema — the admin-initiated variant has a target user the closed field set cannot express.
- **The forced-change `403` is an authorisation denial**, one of the six `source.ip`-bearing event classes 18 fixed. Every blocked request from a `requirePasswordChange=true` user produces one, which means a flagged user browsing a SPA generates a burst of them. Decide whether that is logged per request or suppressed, and note the volume hazard before it becomes a runtime surprise.
- **`event.action`'s closed enum has no value for either.** 05 already flagged that the enum lacks role-change and status-change values; password change and forced-change denial join that list. Whatever this ticket does about the enum gap has to cover all four.
- **Plaintext passwords are prevented, not masked** — 05's ruling, and 04 reinforces it: the change endpoint accepts `{currentPassword, newPassword}`, neither of which may reach a log line, and `Self-Service:262-265` says the same. No masking rule is needed because the values never enter the logging path. **Password *hashes* are also forbidden** (`Standard:319-320` bans reset-token hashes by the same logic), which matters now that 04 has created a `password_history` table full of them: nothing in the reuse-check path may log the compared hashes.
- **Policy-violation rejections may name the violated rule.** 04 settled that `:110`'s specific-error requirement does not collide with `:247`'s identical-response rule, because `:247` scopes itself to authentication outcomes. So a rejected password change can log *which* rule failed (too short, in history, denylisted) without creating an enumeration surface.

**Amended by [19 — Structured log format and the custom encoder](19-log-format-and-custom-encoder.md).** 19 resolved the last of this ticket's three blockers, and it hands over one responsibility plus three inherited shapes.

- **This ticket owns the masked-field list, and the custom encoder is where masking lives.** 19's decisive finding: `Structured_Logging_Application_Standard.md:328` is an `[Enforced Constraint]` whose three permitted mechanisms reduce, under ECS with Spring Boot's native structured logging, to **a structured log encoder extension** — the same artifact 19 built for error-field nesting. So there is exactly one boundary-masking hook and it already exists; this ticket supplies the field paths, not the mechanism. Note this is *not* in tension with 05's "plaintext password never logged is satisfied by prevention" or 04's reinforcement of it: `Sensitive_Data_Masking_For_Logs.md:146`'s "skip masking and rely on prevention" hatch is scoped to that recipe's `logstash-logback-encoder` technique, **not** to `:328`'s clause, which stands regardless. Prevention remains the answer for credentials; `:328` still requires a defence-in-depth mechanism to exist.
- **Masking cannot reach `message` text** (`Sensitive_Data_Masking_For_Logs.md:230`). So this ticket must still mandate sanitisation at the log site, and must not present the encoder as covering the message field. The same line rules out relying on masking for error-related fields, which must be sanitised at the throw site.
- **Every audit log site sets `error_category` explicitly.** 19 dropped the recipe's `ErrorCategoryResolver` (`Custom_Structured_Log_Encoder.md:128-243`) because `:334` fires it only when a throwable is present, and this application's three highest-value security events have none. There is therefore **no automatic categorisation to fall back on** — an omitted `error_category` is simply absent, not inferred.
- **The error triplet's shape is now fixed and uniform.** Log sites use the underscore keys `error_code` / `error_category` / `error_follow_up_action` (`Structured_Logging_Application_Standard.md:188`); output carries them dotted and nested inside `error`. Critically, 19 deviated from `Custom_Structured_Log_Encoder.md:408` so this holds on **throwable-less** events too: the encoder creates the `error` object rather than stripping the keys, because the recipe's silent drop violates `Structured_Logging_Application_Standard.md:167`. This ticket can therefore specify one field shape for every audit event, with no exception-present/absent branch — and it should record why, since the uniformity is bought by a deliberate departure from the recipe.
- **A third appender exists.** 19 corrected 15: `CONSOLE` + rolling `APPLICATION` + rolling `AUDIT`, because `:275`'s durable local buffer (all logs, for a forwarding agent) and `:327`'s separate audit destination are distinct clauses. This ticket's retention-evidence reasoning still lands on the `AUDIT` file, but it can no longer assume that file is also the general durable buffer.

**Amended by [08 — Authorization matrix](08-authorization-matrix.md).** One audit-trail hole named at source rather than left for 14 to find, and a small vocabulary this ticket inherits.

- **Tier-0 preemption blanks the authorization-failure trail for flagged accounts.** `Standalone_Privileged_User_Administration_and_Password_Reset.md:611` places the `PasswordChangeFilter` before `AuthorizationFilter`, and 08 ruled that ordering is the policy, not just the mechanism: if the filter rejects, no matrix row is consulted. So a user with `requirePasswordChange=true` probing admin paths emits `password-change-enforcement` / `failure` lines (`Priv:595-596`) and **no `403 Forbidden` authorization-failure lines at all** — `Structured_Logging_Application_Standard.md:325`'s authorization-failure clause and 18's authorisation-denial `source.ip` class simply do not fire for those accounts. This ticket decides whether `password-change-enforcement` carries `source.ip` to compensate. Note the tension with 18's deliberate six-class scope: adding a seventh class is a change to 18's answer, not a detail of this one.
- **Four error codes are fixed and are audit field values, not just response fields:** `PASSWORD_CHANGE_REQUIRED` (`403`), `ACCESS_DENIED` (`403`), `LAST_USER_MANAGER` (`409`), `CURRENT_PASSWORD_INVALID` (`400`). 08 fixed the codes; [21 — Error contract shape](21-error-contract-shape.md) owns the response envelope. Recall 05's schema gap: `event.action`'s closed enum has no status-change or role-change value, so these codes cannot be smuggled in there.
- **`CURRENT_PASSWORD_INVALID` is a `400`, deliberately not a `401`.** 08's reasoning is `Standalone_User_Access_Control_Application_Standard.md:437`'s global SPA logout interceptor, not a logging concern — but it matters here because a failed current-password check is an *authentication-shaped* event that is not an authentication outcome, so it must not be logged as a failed login or it will pollute the brute-force signal `:249` depends on.
- **The `401` for unauthenticated requests has an empty body** (`HttpStatusEntryPoint`), so there is no error code on that path to log. Correlation for those lines rests on `session.hash` and `trace.id`, exactly as `:331` requires where no `user.id` is resolved.

## Answer

Resolved by grilling, one round. Decisions delegated by the user to the orchestrating session after the evidence was presented.

**Twenty-three audit events. `user.id` always means the actor; the target rides on `target_user_id`, an underscore key following the standard's own `error_*` precedent. `event.reason` is the discriminator the closed `event.action` enum cannot supply. `user.name` is never logged — the policy hatch is shut. The typed audit module is built. `Q28` is 90 days, stated as an integrator obligation. The seventh `source.ip` class is added, amending 18.**

### The three hard gaps

**Gap 1 — there is no target field, so we add one.** `Std:255` requires "what resource was affected" on every audit event; `LS:100-106` offers only `user.id`, `user.name` and `session.hash`; and the binding documents use `user.id` in **opposite** senses — `MDC:320` writes it from inside the security chain on every line (the **actor**), while `Priv:185`, `:213`, `:244`, `:453` use it for the **target**. The PRD's "actor + target" (`prd:123`) has nowhere to sit.

**`user.id` is the actor, always.** The alternative — suppressing the MDC-injected value on `/api/v1/users/**` so the field can mean target there — makes one field name mean two things depending on path, which is the kind of thing that is correct on the day it is written and wrong six months later. It also fights `MDC:320`, which injects unconditionally.

**The target is `target_user_id`.** This looks like a schema violation and is a much smaller one than it appears, because **the standard already does exactly this**: `Typed:322` uses underscore keys (`error_code`, `error_category`, `error_follow_up_action`) precisely because ECS pre-seals the dotted `error.*` namespace, and 19 built the encoder that nests them. An underscore key for a field ECS has no slot for is house style in this standards set, not an invention. `target_user_id` is a UUID, PII-safe on the same reasoning `Std:304` gives for `user.id`.

**Recorded as a deviation from `Std:255`** regardless — the clause implies a field the schema does not define, and we are resolving it by extending the schema rather than by dropping the requirement. Unhedged, in the manner 18 established.

It applies to the eight events with a target distinct from the actor: user created, enabled, disabled, role changed, deleted, unlocked, admin reset-token issued, admin-initiated password change.

**Gap 2 — `event.action`'s closed enum cannot name what happened, so `event.reason` does.** `LS:136`'s ~45 values contain no `user-status-change`, no `user-role-change`, no unlock, no throttling action; `event.category`'s enum (`LS:135`) has **no authentication or security category at all**. By the time this ticket ran the list of un-nameable operations had grown from 05's two to **seven** (role change, status change, unlock, password change, forced-change denial, throttle rejection, bootstrap seed).

**We do not break the enum.** Every event takes the nearest permitted `event.action` — `user-administration`, `user-authentication`, `password-reset`, `profile-read` — and **`event.reason` carries the discriminator** as a short machine-readable string. `event.reason` exists in `LS`'s Event table for exactly this ("a short machine-readable string explaining `event.outcome`"), and 05 identified it as the available valve. `event.severity` (`low`/`medium`/`high`/`critical`) routes independently of `log.level`.

The `event.reason` vocabulary is closed and exhaustive — an unlisted value is a bug:

`role_changed` · `status_enabled` · `status_disabled` · `account_unlocked` · `account_locked` · `password_changed_self` · `password_changed_admin` · `password_change_required` · `reset_requested` · `reset_redeemed` · `reset_token_issued` · `rate_limit_account` · `rate_limit_ip` · `user_created` · `user_deleted` · `bootstrap_seed` · `invalid_credentials` · `session_started` · `session_ended` · `authz_denied` · `self_action_denied`

**This confirms 12's own note that 01's `Q9` rationale was wrong** — sub-resource verbs do *not* make `event.action` one-to-one with operations. 01's decision stands on its other grounds; the discriminator lives here instead.

**Gap 3 — the unknown-username failed login is attributable after all.** 05 left this as possibly "an unattributable count": `user.id` omitted (`AuthN:31`), hashed username forbidden (`AuthN:31`), `session.hash` prohibited in any form including hashed (`Q:94`).

**18 rescued it.** `source.ip` is logged in cleartext on authN failure — one of its six classes — so the line carries `trace.id` **and** `source.ip`, and a brute-force campaign aggregates by source address. `Std:249`'s repeated-validation-failure audit mandate is therefore satisfiable, which is the outcome 18 itself relied on when it rejected the literal reading of `Std:230`.

The line carries **no subject field whatsoever** — no `user.id`, no `user.name`, no hash, not even a null. Omission, not a sentinel, per 18.

### Gap 4 — `user.name` is shut, not invoked

`LS:105` permits `user.name` "only … in authentication/authorization contexts where explicitly permitted by **policy**" — which is every context this application has — while `Std:226`, `Std:276` and `AuthN:17` prohibit raw usernames absolutely.

**Never logged. The hatch is closed.** Invoking it means authoring a policy whose only purpose is to unlock a field, and the field buys nothing: `user.id` identifies an account better than a mutable-looking string, and `username` is immutable here anyway (10), so the UUID resolves to it via the database or the tombstone. A policy escape hatch with no independent policy behind it is a way of deciding by accident, which is what 12's brief warned against.

Consequence for investigators, stated plainly: **no log line in this application contains a username or an email address.** Reading the audit trail requires database access to resolve UUIDs. That is the privacy posture the standard asks for, and it has a real operational cost.

### The event list — 23 events

`Std:239` binds independently of whether a recipe supplies a template, so this is the enumeration, not a selection. All at `INFO` unless marked.

**Authentication and session** — `event.action: user-authentication`
1. Login success — `user.id`, `source.ip`
2. Login failure, known user — `user.id`, `source.ip`, `reason=invalid_credentials`, **`WARN`**
3. Login failure, unknown username — `trace.id` + `source.ip` only, **`WARN`**
4. Account lockout triggered — `user.id`, `source.ip`, `reason=account_locked`, **`ERROR`** (`AuthN:37`, and 05 settled the level)
5. Logout — `user.id`, `source.ip`
6. Session started — `user.id` (`AuthN:159`)
7. Session ended — `user.id` (`AuthN:216`, `:248`)

`AuthN:265` pre-empts the objection that 6 and 7 duplicate 1 and 5: it calls the duplication "expected behaviour".

**Authorization** — `event.action: user-authorization`
8. Authorization denial — `user.id`, `source.ip`, `reason=authz_denied`, `error_code=ACCESS_DENIED`, **`WARN`**
9. Authorization success on a privileged operation (`AuthN:438`) — `user.id`, `target_user_id`
10. Forced-change enforcement denial — `user.id`, `source.ip`, `reason=password_change_required`, `error_code=PASSWORD_CHANGE_REQUIRED`, **`WARN`** — see the seventh-class amendment below
11. Self-action denial — `user.id`, `target_user_id` (= `user.id`), `reason=self_action_denied`, `error_code=SELF_ACTION_NOT_ALLOWED`, **`WARN`**

**Credentials** — `event.action: password-reset`
12. Self-service password change — `user.id`, `reason=password_changed_self`
13. Admin-initiated password change — `user.id` (actor), `target_user_id`, `reason=password_changed_admin`
14. Reset requested — **identical for known and unknown email** (11's ruling): `trace.id`, `source.ip`, `reason=reset_requested`, no subject field
15. Reset redeemed — `user.id`, `reason=reset_redeemed`
16. Admin reset token issued — `user.id` (actor), `target_user_id`, `reason=reset_token_issued`
17. Password policy rejection — `user.id`, the violated rule named (04: `:110` and `:247` do not collide), **`WARN`**

**User administration** — `event.action: user-administration`
18. User created — actor, `target_user_id`, `reason=user_created`
19. Enabled / 20. Disabled — actor, `target_user_id`, `reason=status_enabled|status_disabled`
21. Role changed — actor, `target_user_id`, `reason=role_changed`, old and new role
22. Deleted — actor, `target_user_id`, `reason=user_deleted` (the tombstone write is part of this event, not a second one)
23. Unlocked — actor, `target_user_id`, `reason=account_unlocked`

**Rate limiting** — `reason=rate_limit_account` / `rate_limit_ip`, `source.ip`, **`WARN`** (`Std:325`: rate-limit breaches at WARN "with endpoint"). Folded into events 2/3's action space rather than given their own, since `event.action` has no value for them.

**System** — `profile-read` (`SelfRead:56-60`); application startup and shutdown (`Std:252`); **bootstrap seed** with a **system actor** (16) — the only actor-less audit event, and `actor=system` is a literal string in `user.id`'s place rather than an omission, because omitting it would make the event indistinguishable from a malformed one.

Roughly **2.5× the PRD's seven**, exactly as 05 predicted, and the size is what sizes the build.

### The seventh `source.ip` class — an amendment to 18, not a detail

08 named the hole: `Priv:611` puts the `PasswordChangeFilter` before `AuthorizationFilter`, so a flagged user probing admin paths emits `password-change-enforcement` lines and **no authorization-failure lines at all**. `Std:325`'s authorization-failure clause and 18's authorisation-denial `source.ip` class simply never fire for those accounts.

**Event 10 carries `source.ip`, making seven classes where 18 fixed six.** 18 explicitly flagged that adding one is "a change to 18's answer, not a detail of this one", so it is recorded as an amendment with its reason: for a flagged account, event 10 **is** the authorization denial — it occupies the same position in the request lifecycle and answers the same investigative question. Leaving it IP-less would mean an attacker who triggers a forced-change state has a blind spot to work in.

**Volume: every occurrence is logged, none suppressed.** A flagged user browsing the SPA generates a burst — but a *correct* SPA never produces one, because 10 exposes `requirePasswordChange` on `/currentUser` and the client routes on it before calling anything else. So a burst is itself a signal: either a stale client or someone driving the API directly. Suppressing it would hide exactly the case worth seeing. The hazard is named here so it is not a runtime surprise.

### The typed audit module is built

`Typed`'s §7 is titled "Key decisions", so the module is **recommended, not mandatory** — 05 verified that across all 343 lines. But `Std:327` **is** an `[Enforced Constraint]` (audit logs route to a dedicated appender and destination), that needs a stable logger name to route on, and `Typed:100`'s `getLogger("audit")` is precisely that hook. 15 then chose **logger-name binding over marker binding** specifically so its ArchUnit row — only `com.assessment.auth.audit` may log to the audit logger — is statically enforceable.

So the recommended module is the cheap route to a mandatory constraint, and the alternative (scattered `LoggerFactory.getLogger("audit")` calls) defeats 15's ArchUnit row on day one. **Built, minimal**, in `com.assessment.auth.audit`, which 15 already fixed as the single owner of audit emission.

Under §4 Separation of Concerns it sits **beside** the service layer, not beneath it: services call it explicitly. Not an AOP aspect and not a decorator — 12 of the 23 events have no method boundary that corresponds to them (the unknown-username failure happens inside a filter; the throttle rejection happens before authentication), so an aspect would cover half the surface and create the illusion of covering all of it.

### Masking: the field list, and what masking cannot reach

19 handed over the mechanism and this ticket supplies the paths. `Std:328` is an `[Enforced Constraint]` and under ECS with Spring Boot's native structured logging its three permitted mechanisms reduce to one — the encoder extension 19 already built for error nesting. One artifact, two clauses.

**Masked paths:** `password`, `currentPassword`, `newPassword`, `rawPassword`, `passwordHash`, `password_hash`, `passwordHistory`, `token`, `resetToken`, `token_hash`, `csrfToken`, `_csrf`, `authorization`, `cookie`, `set-cookie`, `sessionId`, `JSESSIONID`, `SESSION`.

**This is defence in depth, not the primary control.** 05 ruled and 04 reinforced that "plaintext password never logged" is satisfied by **prevention** — the values never enter the logging path. 19 correctly noted that `Sensitive_Data_Masking_For_Logs.md:146`'s "skip masking and rely on prevention" hatch is scoped to *that recipe's* technique and does not discharge `:328`'s clause. Both hold: prevention is how we are safe, masking is how we prove we tried.

**Two things masking cannot do**, which must be written into the contract rather than assumed away:

- **It cannot reach `message` text** (`Sensitive_Data_Masking_For_Logs.md:230`). So log sites sanitise their own messages; the encoder is not a backstop for string interpolation. This is the rule that would otherwise be discovered by finding a token inside a message field.
- **It cannot infer `error_category`.** 19 dropped `ErrorCategoryResolver` because `Encoder:334` fires it only when a throwable exists, and our three highest-value security events have none. **Every audit site sets `error_category` explicitly**; an omitted one is absent, not defaulted.

**Password hashes are forbidden too** — `Std:319-320` bans reset-token hashes by the same logic, and 04's `password_history` table is now full of hashes. Nothing in the reuse-check path logs a compared hash.

### The error triplet, uniform

Log sites set `error_code` / `error_category` / `error_follow_up_action` (underscores, `Std:188`); output carries them dotted inside `error`. **One shape for every audit event, with no throwable-present branch** — bought by 19's deliberate departure from `Encoder:408`, which strips the keys when no throwable exists and thereby violates `Std:167`'s "MUST NOT suppress or drop it silently". The uniformity is worth recording as purchased rather than free.

The four codes 08 fixed plus 10's fifth are audit field values, not just response fields: `PASSWORD_CHANGE_REQUIRED`, `ACCESS_DENIED`, `LAST_USER_MANAGER`, `CURRENT_PASSWORD_INVALID`, `SELF_ACTION_NOT_ALLOWED`.

**`CURRENT_PASSWORD_INVALID` is logged as a password event, never as a failed login.** 08 made it a `400` for SPA-interceptor reasons; the logging consequence is that it is authentication-*shaped* but is not an authentication outcome, so routing it to event 2 would pollute the brute-force signal `Std:249` depends on. It is event 17.

### `Q28`, `Q30`, and the two unowned clauses

**`Q28` — 90 days minimum** (`Q:695`, the Base Standard default; `Std:291`). Stated as a **requirement on the integrator**, not implemented: `Std:287`'s note puts retention TTL on "the centralised log management platform", so the application's obligation is rotation of the local rolling files (`Std:260`, `:275`) and nothing more.

**Its interaction with 10's tombstones is a real asymmetry.** Tombstones are retained **indefinitely** (10, `Q:708`); audit events naming a deleted user expire at 90 days. So after 90 days the tombstone is the *only* surviving record that the account existed — which is an argument for indefinite tombstones rather than against, and it is why 10's choice and this one are coherent together. Written into the log inventory.

**`Q30` — "Email, synchronous", stubbed.** 11 answered it and this ticket confirms rather than re-deciding: `Q:723` is the only option compatible with 05's no-`@Async` constraint. The notifications required by `Std:65`, `:71` and `:517` are emitted as log events by a stub; the reset URL never enters the logging pipeline at all (11's `dev`-only `System.out` channel). **So "log only" is the honest description**, stated deliberately as 12's brief asks, but it is `Q30`'s email option with a stub behind it, not a refusal of the question.

**`Std:259` (integrity controls preventing tampering or deletion) — out of scope as an application obligation, in scope as a stated integrator obligation.** Append-only storage, WORM media and log signing are infrastructure controls, and containerization/hosting is a PRD exclusion. What the application *can* guarantee and does: no endpoint anywhere reads, mutates or deletes log files, and the audit appender is write-only. Recorded in the log inventory's access-control column rather than left unmentioned.

**`Std:262` (alert immediately when audit writing fails) — in scope, partially, with the limit named.** "Operations that occurred without an audit record are a compliance risk" is too sharp to wave through. Logback's status system is wired to report appender failures to the `CONSOLE` appender, so a failed audit write is visible. **The honest limit: there is no alerting channel** — no monitoring platform is in scope — so this is detection, not alerting, and an operator must be watching stdout. Ruled in at the level we can actually deliver, with the gap stated rather than claimed as discharged.

### Correlation, MDC, and settled matters

**Backend mints the correlation id; the SPA sends nothing.** 05 recommended it, 09 ruled the chain does not read an inbound `X-Correlation-ID`, and this ticket ratifies as 09 asked. The reason 09 added is the decisive one: with no gateway to strip it, an attacker can set their correlation id to collide with a victim's and poison the audit trail — worse than `Std:276`'s CRLF-sanitisation obligation, which is also triggered.

**`correlation.id` appears nowhere in this application.** `Std:132` makes it conditional on a workflow one `trace.id` cannot span, and 11 — the only candidate — declined it because every derivable key is a prohibited value (`Std:319` plaintext token, `Std:320` token hash). So 05's "otherwise not required" holds with no exception, and the two halves of a reset are linkable in the database, not the logs.

**Two MDC traps, written into the contract:** `putCloseable` closes **before** a `catch` block runs and **removes** rather than restores a pre-existing key (`MDC:233-235`) — both bite precisely on the failed-login and lockout paths this contract exists for, so audit sites use explicit put/remove in `finally`, never `putCloseable`. And **`MDC.clear()` is banned outright**: it strips Micrometer's `traceId`/`spanId` and has no legitimate use here. `MDCInsertingServletFilter` is not used (`MDC:98`) — 18 supplied the sharper reason, that it populates client IP on every line and would breach 18's scope split at its single most important point.

**Bean Validation level is not re-opened.** 21 established that `Std:97` and `Q:298` do not conflict — `:97` is conditional and `Q:295` restates it as the required rule, while `:298` is a scoped recommendation. `WARN` for client input validation, `ERROR` for business rules, `ERROR` at the boundary.

**Three appenders** (19's correction to 15): `CONSOLE`, rolling `APPLICATION` (`Std:275`'s durable buffer, all logs), rolling `AUDIT` (`Std:327`'s separate destination). The 90-day retention evidence lives on `AUDIT`; `APPLICATION` is the forwarding-agent buffer and they are not the same file.

**Citation correction carried from 18:** this ticket cited `Std:257` for "what resource was affected"; it is **`Std:255`**.

### The deliverable: a log inventory

`Std:283` requires "a log inventory that documents what is logged, where it is stored, how it is used, how access is controlled, and how long logs are kept" — which is this ticket's output written down. Produced as **`docs/logging/log-inventory.md`**, content specified here, **file left for 15 or 17 to land** alongside 18's ADR, same treatment and same reason (it waits on the module layout).

Required content: the 23-event table with fields and levels; the `event.reason` vocabulary; the masked-path list; the three appenders and their destinations; **access control** — the `AUDIT` stream is classified **above** the application log because `source.ip` lands only there (18), and `Std:259`'s integrity controls are the integrator's; **retention** — 90 days minimum on the integrator, rotation on the application, and the tombstone asymmetry above.

### Amends

- **18** — a **seventh** `source.ip` class: forced-change enforcement denial. Recorded as an amendment to 18's answer.
- **01** — its `Q9` rationale about `event.action` being one-to-one with operations is confirmed false; the decision stands, `event.reason` supplies the discriminator.
- **15** — lands `docs/logging/log-inventory.md`; the ArchUnit audit-logger row now has a concrete owner (`com.assessment.auth.audit`) and a concrete violation to catch.
- **16** — the bootstrap seed's system actor is `user.id = "system"`, a literal, not an omission.
- **21** — no change; its `ERROR`/`WARN` ruling is consumed as settled.
- **14** — tests for: no log line anywhere contains a password, a password hash, a reset token (plaintext or hashed), a session id, a username or an email; the unknown-username failure carries no subject field; all 23 events fire on their trigger; `event.reason` never takes an unlisted value; audit lines route to the `AUDIT` appender and application lines do not; `trace.id` and `span.id` present on every line; MDC empty after every request including failure paths.
