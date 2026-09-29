# 11 — Password reset flow

Type: grilling
Status: resolved
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

## Answer

Resolved by grilling, one round. Decisions delegated by the user to the orchestrating session after the evidence was presented.

**`Q14` answered "Both admin-generated and self-service" — the recommended option — unified on one token model. `SecureRandom` 32-char alphanumeric, SHA-256 hashed, 30-minute TTL, single-use. Admin-initiated reset is ruled IN because an enforced constraint requires it, and it issues a token, not a password — which overturns the recipe. The PRD's stubbed `EmailService` logging the reset link is prohibited outright and must be redesigned.**

### The lookup constraint this ticket raised is answered by the standard, in one line

The brief flags that a salted per-row BCrypt hash makes lookup impossible and calls it "a real design constraint, not a detail". It is, and `Std:66` addresses it directly:

> *Spring Boot: Generate the token using `SecureRandom`. Store a **SHA-256** hash of the token using `MessageDigest.getInstance("SHA-256")` rather than a slow adaptive hash. **The token is already a high-entropy random value and does not require the cost of a password hash.***

So: unsalted SHA-256, which is a deterministic lookup key. The usual objection to unsalted hashing — dictionary attack — does not apply to 190 bits of `SecureRandom` output.

**Token format: 32-char alphanumeric**, the option marked "(recommended)" on every branch of `Q14` (`Q:346`, `:349`, `:352`, `:355`, `:358`, `:363`). Over a 62-character alphabet that is ~190 bits, far above any threshold worth arguing about.

**TTL: 30 minutes**, fixed by `Std:126` ("expire after a maximum of 30 minutes"), `Std:62`, `Q:340`, and tested at `Std:459`. The PRD's 15–30 range (`prd:148`) permits it, so there is no conflict to resolve — 30 is the standard's number and the top of the PRD's range.

### `Q14` answered: both flows, one token model

`Q:360` marks "**Both admin-generated and self-service** (recommended for flexibility)" as the only recommended option, and both halves are separately forced:

- **Self-service** is PRD Stories 6 and 7.
- **Admin-initiated is not optional.** `Std:401` is an `[Enforced Constraint]`: "Password reset uses separate endpoints: **one for admin-initiated token issuance** and one for user-facing token redemption." 01 handed this ticket the call of ruling `PATCH /users/{userId}/resetPassword` in or out; `:401` removes the discretion. **Ruled in.**

So three endpoints, sharing one token store and one confirm path:

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST ${api.base-path}/auth/password-reset/request` | public | self-service; generic response always |
| `PATCH ${api.base-path}/users/{userId}/resetPassword` | `USER_MANAGER` | admin issues a token, returned **once** |
| `POST ${api.base-path}/auth/password-reset/confirm` | public | redeem token, set new password |

Matrix rows: the two `/auth/**` rows are 08's existing `permitAll` entries; the admin row sits inside 08's `/users/**` backstop, keyed `USER_MANAGER`, behind the tier-0 filter and the `SelfActionGuard` (10) — an administrator resetting *their own* password uses `/currentUser/changePassword`, not this.

### The recipe's admin reset is overturned: a token, not a generated password

`Priv:414-463` implements admin reset as **generate a random password, set it, return the plaintext**. There is no token anywhere in the recipe; the `password_reset_tokens` table has no counterpart in it.

That contradicts the standard in three places — `Std:401` (enforced: "admin-initiated **token issuance**"), `Std:62-66` (the flow: generate token, store hash, user submits token *with the desired new password*), and `Std:239` ("An admin-initiated password reset must return the newly generated plaintext **token** exactly once in the response body").

**The standard wins, on the usual precedence** — and the payoff is real rather than merely conformant:

- **One token store, one confirm endpoint, one policy check.** The recipe's model would need a second code path that sets a password directly, bypassing the confirm endpoint where 04's `PasswordPolicy` and `PasswordHistoryService` live.
- **The administrator never learns the user's password.** Under the recipe they generate it, read it, and transmit it. Under the token model the user chooses their own at confirm time, and the admin holds only a 30-minute bearer token.

**Consequence: `Std:357-361`'s admin-generated-password rules do not apply.** The clause requires a 12-character random password with one of each character class, and `Priv:120` implements it — but under `:401`'s token model **no password is ever generated by the system**, so the clause has no subject. Recorded as *inapplicable*, not declined. (Note this also removes the last surviving composition requirement; 04 dropped composition for user-chosen passwords, and `:357-361` was the one place it still lived.)

**`requirePasswordChange` is NOT set by admin-initiated reset.** `Priv:438` sets it, because under the recipe's model the user logs in with a password someone else chose. Under the token model the user picks their own password at confirm, so the flag would trap them in a filter demanding a change they just made. Removed deliberately, with the reason recorded, since it looks like a dropped requirement otherwise.

**`Std:131` beats `Priv:441-442` on lock state.** The clause: "An administrator-initiated password reset for a locked account is permitted. The reset token is issued and **the lock is not automatically cleared**. The account remains locked until the lock expires or the administrator explicitly unlocks it." The recipe clears it (`failedLoginAttempts=0`, `accountNonLocked=true`). Clause over recipe — and it is the safer direction, since a password reset is not evidence that the brute-force attempt which caused the lockout has stopped. **This resolves the contradiction 07 flagged**: the administrator has a dedicated unlock endpoint (07) if they want the lock gone, which is exactly what `:131` points at.

### Enumeration resistance covers timing, body and the audit log

**Body.** `POST /auth/password-reset/request` returns an identical generic `200` whether or not the email is registered (`prd:74`, `Std:125`, `Std:247`).

**Timing is explicitly in scope** — this ticket's brief asks whether to include it, and `Std:247` does not leave room: "must return identical HTTP status codes, response bodies, **and response timing**", with `Std:506` making it an acceptance test.

The honest mechanism, and its honest limit: the unregistered path performs no database write and no email send, so it is naturally faster. We equalise with a **fixed response floor** — the handler records a start instant and sleeps to a constant budget before responding — rather than by faking work. This is approximate: it bounds the *mean* difference but a loaded server still leaks jitter, and a determined attacker with enough samples can still distinguish. **Recorded as a partial discharge of `:247`, not a complete one**, because claiming constant-time here would be false. The same floor applies to `POST /auth/login`, where `:247` binds equally.

**The audit log must not leak what the response does not.** 05 posed this and it is decided here: **one event, identical fields, for every reset request** — registered or not. `Logging_AuthN_And_AuthZ_Events.md:31` treats a hashed identifier as an enumeration signal, `Std:331` forbids raw emails, and `Std:319-320` forbid the token in plaintext **or hashed**. So the line carries `event.action=password-reset`, `event.outcome=success`, `trace.id`, and `source.ip` per 18 — and for an unregistered email that is genuinely all it can carry.

**Two events would leak account existence to anyone who can read logs**, which is `AuthN:31`'s explicit concern and would make the response-level resistance decorative. Accepted cost: the audit trail cannot distinguish a real reset request from a probe. The **redemption** event at confirm carries `user.id`, so the half of the flow that proves an account exists is fully attributable.

### No `correlation.id` linking request to confirm

05 identified this as `correlation.id`'s only candidate surface in the whole application (`Std_Logging:132` — required only "when a single `trace.id` does not span the full workflow"), and request and confirm are two requests minutes apart.

**Declined, because every derivable key is prohibited.** The only value common to both halves is the token: `Std:319` forbids logging its plaintext and `Std:320` forbids logging its **hash**. An HMAC under `Std:276`'s system-wide key would technically clear `:320`, but it reintroduces exactly the key-rotation objection 18 used to reject HMAC'd IPs — a key that never rotates is a permanent secret, and one that rotates severs the correlation the field exists for. Deriving it from `user.id` is impossible at request time for an unregistered email, and doing it only for registered emails would recreate the two-event enumeration leak above.

So `correlation.id` appears **nowhere in this application**, and 05's "otherwise not required" holds without exception. The consequence is stated rather than hidden: **the two halves of a reset are not linkable in the logs.** They are linkable in the database, via `password_reset_tokens.user_id` and `used_at`, which is where an investigator should look.

### The PRD's `EmailService` stub is prohibited as specified

`prd:75` says the stub "**logs the link** instead of sending mail", and `prd:20` repeats it. **A reset link contains the plaintext token, and `Std:319` forbids logging "password reset token plaintext" without qualification** — reinforced by `Std:63` ("must not be logged or cached"), `Std:239`, `Std:515` (a test: "Logs never contain … password reset tokens") and `Priv:730`.

This is not a soft conflict. The PRD's convenience mechanism is the single most explicitly prohibited thing in the logging standard, and 19's ECS pipeline would carry it into the durable file appender and, for the audit logger, into the separately-classified audit destination.

**Redesign, keeping the demo capability:**

- `EmailService.sendPasswordResetEmail(...)` emits a **structured log line with no token and no link** — `event.action=password-reset`, `user.id`, outcome. That is the auditable record.
- The reset URL is written, **in the `dev` profile only**, to `System.out` directly — *not* through SLF4J. Nothing routed through Logback means no appender, no ECS document, no audit file, and no chance of the token reaching a log aggregator. A profile-gated bean, absent entirely from `prod`.
- `sendPasswordChangedEmail(...)` — the second stub 04 added, required by `Std:65` and `:71` ("notifies the account owner") on **both** admin reset redemption and self-service change, and tested at `Std:517`. It logs an event and never a credential, so it needs no `dev` carve-out.

`Q30` (notification strategy) is answered **"Email, synchronous"** (`Q:723`) — no option is marked recommended, and it is the only one compatible with 05's constraint that nothing become `@Async` (which would activate `Std_Logging:324`'s `TaskDecorator` `[Enforced Constraint]` and 15's ArchUnit ban). Both stubs stay synchronous.

### Token lifecycle

- **Issued:** `SecureRandom` 32-char alphanumeric; SHA-256 stored in `password_reset_tokens.token_hash`; `expires_at = now + 30m` (UTC, injectable `Clock`); `used_at` null.
- **Superseded:** issuing a new token **deletes** any prior unused row for that account. `Std:112` and `:126` require the previous token to be invalidated immediately; deletion is unambiguous where marking `used_at` would conflate "redeemed" with "replaced" and corrupt the single-use audit signal. `used_at` therefore means exactly one thing: this token was redeemed.
- **Redeemed:** `used_at = now`, atomically with the password update. A second submission finds `used_at != null` and is rejected.
- **Invalidated by a self-service change:** `Std:356` and `:111` require pending unused tokens to die when the user changes their password themselves. Same deletion. 04 owns the calling side.
- **Rejected:** expired *or* already used returns **`400`** with a single merged error — `Std:261` defines one code for both ("`password reset token expired or invalid`"), and distinguishing them would tell an attacker whether they had guessed a real token. `Std:460` tests exactly this.

**On successful confirm**, in order: validate token → `PasswordPolicy` + `PasswordHistoryService` (04; `Std:110` makes the history check on reset **mandatory**, four blocked values under 04's rule) → write the credential → write the `password_history` row → mark `used_at` → **invalidate all sessions** for that account (13's repository deletion; `Std:128`, `prd:79`) → **clear `requirePasswordChange`** (04) → `sendPasswordChangedEmail`.

**The flag-clearing is load-bearing, not cosmetic**, and 04's reasoning is preserved here because this ticket owns the endpoint: the reset endpoints are public, so the `PasswordChangeFilter` never evaluates them. Without the clear, a flagged user who routes around the filter via the email flow ends up with a password of their own choosing and *still* trapped behind the filter forever.

### Rate limiting, and a keying deviation

`Std:113` and `Std:379` require it on **both** token issuance and token redemption, with `429` + `Retry-After` (`Std:452` tests it). No number is given for reset endpoints — `Std:378`'s 10/min is scoped to `/login`.

**Mechanism is 07's** (Bucket4j over Caffeine, `429` + `Retry-After` via 21's writer). **Keying deviates from `Std:124`'s "per account", by necessity:**

- `/auth/password-reset/request` — **keyed on IP**, 5/min. There is no account to key on: the whole point is that we do not reveal whether the submitted email resolves to one, and keying on the submitted email would itself be an enumeration oracle (different throttle behaviour for real and fake addresses).
- `/auth/password-reset/confirm` — **keyed on IP**, 10/min. The caller presents a token, not an identity; this is the brute-force surface `Std:379` names.
- `PATCH /users/{userId}/resetPassword` — per-account, 10/min, reusing 07's per-account limiter, since the caller is authenticated and the target is known.

**Recorded as a deviation from `Std:124`** with the reason, rather than pretending per-account keying is possible on an anonymous endpoint. It inherits 07's NAT caveat: behind shared egress, one IP is the whole office.

Unthrottled, this endpoint is also a mail-flood vector — which the stub makes harmless today and which becomes live the moment an integrator wires real SMTP. Worth its line in 15's deployment assumptions.

### Amends

- **01** — `PATCH ${api.base-path}/users/{userId}/resetPassword` is confirmed in the inventory, with a token response body, not a password.
- **02** — `password_reset_tokens` per the PRD's columns, this ticket's changelog file; `token_hash` unique-indexed as the lookup key.
- **04** — its self-service change calls this ticket's token-deletion helper; `Std:357-361`'s composition rule is now inapplicable everywhere, closing the last composition question.
- **07** — the `Std:131`-versus-`Priv:441` lock-clearing contradiction 07 flagged is settled: **the lock is not cleared**; the unlock endpoint is the remedy. Reset endpoints join its limiters, IP-keyed.
- **12** — three event classes: reset requested (identical for known and unknown), reset redeemed (with `user.id`), admin token issued (actor and target). **No token value, plaintext or hashed, in any of them.** Plus the explicit note that `correlation.id` is used nowhere.
- **13** — confirm calls the session-revocation helper.
- **15** — the `dev`-only `System.out` channel for the reset URL is a profile-gated bean; add the mail-flood note to deployment assumptions.
- **21** — reset failures use the standard envelope with `code`; the merged expired-or-used case is one code.
- **14** — tests for: token single-use; expiry on an injected `Clock`; a new token invalidating the prior one; confirm invalidating all sessions; history enforced on confirm; identical body **and** bounded timing for registered versus unregistered email; the audit line for an unknown email carrying no account-distinguishing field; **and a test asserting no log line anywhere contains a reset token** (`Std:515`, `Priv:730`).
