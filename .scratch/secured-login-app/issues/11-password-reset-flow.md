# 11 — Password reset flow

Type: grilling
Status: open
Blocked by: —
Map: [Secured Login App](../map.md)

## Question

What is the password reset workflow and token delivery strategy?

Answer `Q14` (password reset workflow and token delivery, `Questions.md:335`) against [`Standalone_Privileged_User_Administration_and_Password_Reset.md`](../../../App-Standards/Appfw-User-Standards/User_Standalone/Standalone_User_Access_Control_Recipes/Standalone_Privileged_User_Administration_and_Password_Reset.md).

The PRD's Stories 6 and 7 fix the required behaviour; this ticket settles the mechanism:

- Token generation: source of randomness and length; the **hash** is stored, never the plaintext (`password_reset_tokens.token_hash`) — decide the hash algorithm, and note it is a lookup key, so a salted-per-row BCrypt hash would make lookup impossible. This is a real design constraint, not a detail.
- Expiry (PRD allows 15–30 min) and single-use enforcement via `used_at`.
- Whether an outstanding token is invalidated when a new one is requested, and whether requests are rate-limited (unthrottled, this endpoint is an email-enumeration and mail-flood vector even with a generic response).
- Enumeration resistance: the response is a generic success **regardless** of whether the email is registered (Story 6), which constrains timing as well as body — decide whether timing equalisation is in scope.
- The stubbed `EmailService.sendPasswordResetEmail(...)` that logs the link instead of sending: what it logs, and how that interacts with the logging standard's masking rules — a reset link in a log line is a live credential, so 12 must cover it.
- On confirm: password updated, token marked used, and **all existing sessions for that user invalidated** (Story 7) — this is the criterion that depends on 02's session-store choice supporting find-by-principal.
- Whether the new password is checked against history, per 04's ruling.

Blocked on 04 (password policy and history apply to the new password).

**Amended by [01 — Context, topology and API surface](01-context-topology-and-api-surface.md).** Two path facts and one scoping item from 01's endpoint inventory:

- The PRD's self-service reset endpoints have **no standard path** (the standard's reset is admin-initiated), so 01 placed them under the `/auth` provenance rule: `POST /api/v1/auth/password-reset/request` and `POST /api/v1/auth/password-reset/confirm`.
- **`PATCH /api/v1/users/{userId}/resetPassword`** (`Standalone_Privileged_User_Administration_and_Password_Reset.md:391`) is the standard's *admin-initiated* reset. It serves no PRD story and 01 assigned ownership of it here: rule it in or out, and if out, it belongs on the map's **Out of scope**, not **Decisions so far**.
- Whether a successful reset sets `requirePasswordChange` is ticket 04's to rule on (see its amendment); coordinate rather than duplicate.

**Amended by [05 — Which logging standards actually bind a two-process app](05-logging-standards-applicability.md).** Two logging consequences land on the reset flow specifically, because it is the only workflow in this application that spans more than one request.

**Is the reset *request* auditable at all?** `Structured_Logging_Application_Standard.md:242` makes credential changes including password resets mandatory audit events. But Story 6's enumeration resistance means the endpoint returns a generic success whether or not the email is registered — and for an unregistered email there is no `user.id` to log, while logging the submitted email is prohibited outright (`Std:226`, `Std:276`, `Logging_AuthN_And_AuthZ_Events.md:17`) and a hashed identifier is forbidden as an enumeration signal (`AuthN:31`). So the audit line for an unrecognised reset request may reduce to **`trace.id` plus whatever [18 — Client IP in logs](18-client-ip-in-logs.md) permits** — and if 18 rules the IP out, it carries no distinguishing information at all. Decide here: does the request emit one audit event regardless (identical for known and unknown, preserving enumeration resistance in the *logs* as well as the response), or two different events (which leaks account existence to anyone who can read logs — `AuthN:31`'s explicit concern)? The decision must hold for the **log** as well as the HTTP response; this ticket's existing timing-equalisation question has the same shape.

**Reset is the one workflow a single `trace.id` cannot span.** `Structured_Logging_Application_Standard.md:132` makes `correlation.id` contextual — required only "when a single `trace.id` does not span the full workflow (e.g., a process that triggers multiple independent traces across time or systems)". Request and confirm are two HTTP requests minutes apart, so they get two traces. 05 established that `correlation.id` is otherwise **not** required anywhere in this application; this is its only candidate surface. Decide whether a `correlation.id` ties the two halves together, and if so what it is derived from — noting that the obvious candidate, the reset token, is a **live credential** and cannot appear in a log line (this ticket already flags that the stubbed `EmailService` logging the link is the same hazard). A hash of the token as correlation key would need `Std:276`'s system-wide salt or HMAC, which is [15 — Tech baseline and module structure](15-tech-baseline-and-module-structure.md)'s secrets question.

**Keep the `EmailService` stub synchronous.** `Structured_Logging_Application_Standard.md:324` is an `[Enforced Constraint]` requiring a `TaskDecorator` that copies and restores the full MDC map around every `@Async` method. 05 confirmed it is vacuous **only** while nothing is `@Async`. Making a logging-only stub asynchronous would activate that constraint — plus the async-propagation sections of `Recipes/Enriching_Logs_With_MDC.md` and `Recipes/Structured_Logging_Trace_Correlation_And_Context_Propagation.md` — for no benefit. [15](15-tech-baseline-and-module-structure.md) records it as a baseline constraint; this ticket should not reintroduce it.

**Amended by [04 — Password policy and history](04-password-policy-and-history.md).** 04 is resolved, so this ticket is now unblocked. It inherits a policy, a flag lifecycle and one bidirectional interaction.

- **Reset-confirm enforces the same policy as every other write path.** 04 put strength and reuse behind an explicit `PasswordPolicy` + `PasswordHistoryService` called from the domain layer, precisely so registration, reset-confirm, self-service change and admin-create cannot drift. Reset-confirm calls both: min 12 / max 72 / no composition / printable ASCII / denylist, and the four-value history rule (three previous plus current). `Standalone_User_Access_Control_Application_Standard.md:110` makes the history check on reset mandatory, not optional.
- **Reset-confirm *clears* `requirePasswordChange`.** This is not cosmetic. The reset endpoints are public, so the `PasswordChangeFilter`'s `auth != null` guard never evaluates them — a flagged user can route around the forced change entirely via the Story 6/7 email flow. That is acceptable because they end up with a password of their own choosing, but if reset-confirm did not clear the flag they would be permanently trapped behind a filter that blocks everything. 04's lifecycle table has the full set of triggers.
- **Admin-initiated `resetPassword` *sets* the flag** (`Privileged:438`) and must generate a password not already in the account's history (`Privileged:427`). The recipe's retry loop assumes the recipe's three-value history; under 04's four-value rule the exclusion set is one larger. Note also that the admin-generated password **keeps** the one-of-each-class composition requirement (`Standard:357-362`), even though 04 dropped composition for user-chosen passwords — that clause is scoped to the generated password specifically.
- **A self-service change invalidates this ticket's pending tokens.** `Standard:356` requires that a successful self-service password change immediately invalidates any pending unused reset token for that account. 04 put that step in the change-password sequence; this ticket owns the other side of it — the token store and whatever "invalidate" means against it (delete vs mark-used), which must also satisfy `:120`'s "a new token immediately invalidates any previously issued token".
- **`sendPasswordChangedEmail(...)` is a new stub.** `Standard:65` and `:72` require notifying the account owner on *both* admin-initiated reset and self-service change, and the PRD's stub `EmailService` only has `sendPasswordResetEmail`. 04 added the second method; this ticket owns the `EmailService` interface, so the shape of both is yours.
- **Session invalidation is already settled the same way on both flows.** 04 ruled the self-service change kills **all** sessions including the caller's, matching what `:128` already requires of reset — so there is no asymmetry for this ticket to reconcile.
