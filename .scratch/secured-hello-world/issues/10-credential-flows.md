# 10 — Decide the credential flows: registration, password reset, self-service change

Type: grilling
Status: resolved
Blocked by: 01, 02, 04, 06, 07

## Question

What are the end-to-end flows for creating an account, recovering an account, and changing a
password — as one coherent design rather than three that reinvent the same token machinery?

These are merged deliberately: all three set a credential, all three must apply one password policy,
all three invalidate sessions, all three notify the owner, and two of them mint single-use hashed
tokens. Designing them apart guarantees drift.

## Settled going in

- Self-registration **stays** (the PRD owns scope) but becomes enumeration-safe (the standard owns
  control behaviour), which means PRD Story 1's "clear validation error (username/email conflict)"
  will **not** be met. That failing acceptance criterion must be visible in the spec, not silent.
- Gating: **stubbed email verification**, not admin approval. It reuses the reset token machinery the
  standard already forces us to build. Admin approval becomes a config flag, default off.
- The enumeration-safe shape: always respond generically; create an unverified account only when the
  email is new; when the email is already registered, create nothing and notify the existing owner
  that someone tried to register with their address.
- Reset tokens: `SecureRandom`, stored as a SHA-256 hash (explicitly **not** an adaptive hash — the
  token is already high-entropy), 30-minute expiry, single use, and issuing a new token immediately
  invalidates any prior pending one.
- Self-service change requires the current password even with an active session, and invalidates all
  sessions on success.

## Inherited from ticket 06 — including real scope growth

[Decide the API error envelope and the enumeration-safe response contract](06-error-envelope-and-enumeration-contract.md)
resolved the `user exist` tension by **splitting the flow**, and in doing so handed this ticket a sub-flow it
did not previously have:

- **Self-registration returns a uniform `202` regardless of outcome, and the account becomes usable only by
  redeeming a hashed single-use activation token issued only when the registration is genuinely new.** This is
  new work for this ticket: token generation, storage, delivery and a redemption endpoint, alongside the reset
  token. Q22 of the Questions file already prescribes activation token hashing, so the corpus contemplates it —
  decide whether activation and reset share one token table and one redemption endpoint or stay separate.
  A bare uniform success with no activation flow was rejected: with no email verification in scope, a colliding
  user would receive a success they could never act on. ADR owed for the deviation from PRD Story 1.
- **`USER_EXISTS` (400) is narrowed to admin-initiated creation only**, where the caller is already privileged.
- **Ordering rule, load-bearing:** the reset-token check must pass **before** any password-quality validation
  runs. Otherwise a specific strength error confirms the token was valid.
- Account-state errors are always generic (one 401 `AUTHENTICATION_FAILED`); submitted-value-quality errors are
  always specific, **including inside the reset and change flows** — `PASSWORD_REJECTED` (400) carrying
  `errors: [{field, rule}]` with `rule` as an enum, never prose. `RESET_TOKEN_INVALID` (400) is a distinct code,
  which is the only thing that lets a client tell a bad token from a weak password on the same status.

## What to decide

**Registration.**

- Token entropy, encoding, and the verification link shape handed to the stubbed `EmailService`.
- Do verification tokens share the `password_reset_tokens` table with a type discriminator, or get
  their own table? A shared table means one expiry/single-use code path; separate tables mean
  clearer constraints. Decide.
- Unverified account representation: `enabled=false`, or a distinct status? This matters because
  admin "disabled" and "never verified" are different states that must not be conflated — an admin
  re-enabling a never-verified account should not bypass verification.
- What happens to an unverified account that is never verified — does anything reap it? (Reaping is
  adjacent to the out-of-scope hygiene jobs; decide whether that makes it out of scope too.)
- Registration rate limiting, since the generic response means an attacker can probe freely.
- Username rules: character set, length, case sensitivity, and whether a username that differs only
  by case collides. Same question for email.

**Password reset — reconciling two different flow models.** The PRD describes user-initiated reset
by email. The App Standard describes **admin-initiated** token issuance with the plaintext token
returned to the admin exactly once, plus a user-facing redemption endpoint, and marks the two-endpoint
split an `[Enforced Constraint]`. Decide: build both issuance paths sharing one redemption endpoint,
or only the PRD's? Building both satisfies the standard and costs one extra endpoint.

- For the admin path: the plaintext token is returned once and must never be logged or cached.
  Decide how that is enforced rather than merely intended.
- Confirm the reset request response is generic regardless of whether the email exists (PRD Story 6
  already requires this, and it aligns with the standard).
- On successful redemption: mark used, update credential, record in history, invalidate all sessions,
  notify the owner.

**Self-service change.** Endpoint shape, current-password verification, and the standard's rule that
a successful self-service change invalidates any pending unused reset token for that account.

**Cross-cutting.**

- One shared password validator across all three paths (see "Decide the password policy").
- Owner notifications via the stubbed `EmailService`: password changed, account locked, reset
  completed. Decide the event list and that failures to notify are logged, not swallowed.
- Which audit events each flow emits — hand the list to "Build the audit event catalogue".

## Done when

All three flows are specified end to end, the shared-vs-separate token table question is answered,
the two reset issuance models are reconciled, and the unverified-vs-disabled state distinction is
explicit.

## Answer

**The credential moves out of registration and out of the administrator's hands. Registration takes a username
and an email and mints nothing but a hashed single-use token; the password is set at redemption. Admin-create
and admin-reset both become token issuance. One `credential_tokens` table with domain-separated hashing serves
all three. The username stays at registration and its conflicts are reported specifically, inside ticket 06's
existing split rather than as an exception to it.**

Three of this ticket's own premises were wrong, and one of them was load-bearing for the gating decision.

### 1. There is no token machinery in the corpus to reuse

The ticket chose stubbed email verification over admin approval because "it reuses the reset token machinery
the standard already forces us to build." That machinery does not exist. A sweep of all four
`Standalone_User_Access_Control_Recipes` files finds no token entity, no `expiresAt`, no `used` flag, no
`SecureRandom`, and no hashing of any token; the only acknowledgement that reset tokens exist anywhere in the
recipe corpus is a References link to the OWASP Forgot Password Cheat Sheet (`Standalone_Privileged...:745`).
The admin-reset recipe implements something else entirely — `PasswordUtil.generateRandomPassword()` returning a
12-character plaintext password in the response body. **All token machinery on this map is built from zero**,
so the stated economy behind the gating choice is fictional. The choice survives anyway, because admin approval
does not scale to a public sign-up endpoint and the PRD asks for self-registration, but it survives on different
grounds and the ADR must say so.

**Seventh standards defect on this map: the Standard mandates two mutually exclusive admin-reset models.**
§2 Happy Path 11:62–64 is the token model — random single-use token, 30-minute expiry, hash stored, "the user
submits the plaintext token with the desired new password" — reinforced by §3.1:239, §4:401 and §5:457–459.
§3.5:357 is the generated-password model — "Administrative password reset must generate a 12-character random
password" — reinforced by §6:619, and it is the one the recipe implements. One action cannot both return a
redemption token and set a credential. Q22:565 then relabels the same 12-character rule as the "Initial
password" for admin-*created* accounts, which is a third reading of one requirement. *Consolidated into the register (ticket 33): R-STD-020. Amend the table by ID, not this list.*

**Neither self-registration nor user-initiated reset is in the Standard at all.** "Self-registration" appears
zero times; the exclusion lives only in Q22:539 of the Questions file. `forgot` matches one line, the reference
link. And §4:401's `[Enforced Constraint]` pins the architecture to exactly two reset endpoints — admin
issuance and user *redemption* — leaving no slot for the PRD's reset *request* endpoint. Both PRD flows are
unblessed design space, not deviations from a prescribed shape. Consequence for the conflict rule in the map's
Notes: "the standard owns control behaviour" cannot arbitrate here, because on the flow the PRD cares about the
standard has no behaviour to own. *Consolidated into the register (ticket 33): R-CRED-008. Amend the table by ID, not this list.*

Two smaller corrections, recorded so they are not re-derived. The SHA-256 choice for reset tokens is sound but
**not mandated** — it appears only inside a non-normative italicised Spring Boot note at §2:66, while Q22:543
states it flatly for *activation* tokens, so the Questions file is stricter than the Standard it annotates
(eighth defect). And the 30-minute expiry is stated in four incompatible modalities — fixed at §2:62, "a *Consolidated into the register (ticket 33): R-STD-021. Amend the table by ID, not this list.*
maximum of" at §2:126, testable equality at §5:459, "default" at §6:618 — against the PRD's 15–30 minute range,
so no conformance test can be written against the Standard as printed (ninth defect). Our 30 minutes satisfies
all four readings and the PRD range simultaneously, which is why the number does not move. *Consolidated into the register (ticket 33): R-STD-022. Amend the table by ID, not this list.*

Two independent vindications. Ticket 09's decision to key reset-redemption throttling on source IP rather than
account is **forced**, not preferred: §3.5:379 and §5:452 mandate per-account limiting on an endpoint where the
account is only knowable *after* the token lookup the limit exists to protect (tenth defect). And ticket 07's *Consolidated into the register (ticket 33): R-STD-019. Amend the table by ID, not this list.*
`PasswordService` seam is vindicated hard — in the self-service recipe `PasswordPolicy.validate()` is never
called on any path, the current-password check exists only as a disconnected §5 snippet the controller cannot
reach (`ChangeCurrentUserPasswordCommand` holds only `newPassword`), and `revokeOtherSessions` is defined and
never invoked. As printed, a user can set a one-character password and every other session stays live. *Consolidated into the register (ticket 33): R-STD-023. Amend the table by ID, not this list.*

The username and email rules are **closed by ticket 11 and reused, not re-derived**: `[a-z0-9._-]{3,32}`,
NFC-normalise + trim + lowercase on both identifiers, `@` rejected in usernames, no dot-folding or `+tag`
stripping. Only the email *format* rule was open (§14 below). And session invalidation is **not** uniform across
the three flows as the ticket assumed: ticket 08 made it actor-relative, and self-service change and
forced-change completion are the only two "all others" rows on its table.

### 2. The credential is set at redemption, never at registration

The settled shape had an account takeover path in it, and closing it also deletes a timing oracle.

**The takeover.** With the password collected at registration and the account usable only after activation, the
overwrite-on-unactivated rule can be turned around, because the attacker chooses the ordering. Victim
registers; their password is stored and link T₁ reaches their inbox. Attacker re-registers the same address;
the credential becomes the attacker's, T₁ is invalidated, and fresh link T₂ is sent — to the *victim's* inbox.
Victim clicks the newest mail and activates an account carrying the attacker's password. This is not the narrow
race originally recorded as a residual: the attacker never needs to know the victim is registering, and can
loop at 5/min per IP indefinitely. Shipped precedent: [CVE-2026-48117](https://www.sentinelone.com/vulnerability-database/cve-2026-48117/)
is this exact shape — registration with a victim's address and an attacker-chosen password before the victim
finished activating. [GHSA-qq9h-g4jm-xgf3](https://github.com/advisories/GHSA-qq9h-g4jm-xgf3) is the same class
with the canonical fix: on finding an account whose address was never confirmed, remove the password and revoke
sessions *before* marking it verified.

**The timing oracle, which is the same problem.** ASVS 5.0 **6.3.8 (L3)** names error messages, HTTP response
codes *and different response times*, and extends the protection to registration and forgot-password
explicitly. With the password at registration, "address is free" runs a BCrypt-12 encode and "address belongs
to an activated user" creates nothing — 300–400ms against single-digit milliseconds, from ticket 07's own
figure (it costed a five-BCrypt change at 1.5–2 seconds). That is a 50–100× oracle behind the uniform 202 that
ticket 06 fought hardest for. Ticket 06's mitigation cannot help: the dummy hash is `DaoAuthenticationProvider`
behaviour and exists only on the login path.

**Decision: registration stores no credential.** `POST /api/register` takes `{username, email}`, and
`POST /api/register/activate` takes `{token, password, passwordConfirm}`. The Forgot Password Cheat Sheet's two
sanctioned remedies for the timing channel are asynchronous work or following the same logic rather than a quick *Consolidated into the register (ticket 33): R-CRED-009. Amend the table by ID, not this list.*
exit; taking the BCrypt out of the endpoint means **the oracle is deleted at source rather than masked by a
decoy hash**, which would otherwise have been a permanent 300–400ms tax on an anonymous endpoint to hide
something removable. Three further consequences, all in the same direction:

- **Pre-hijacking closes outright.** A pending record holds no credential, so there is nothing for a second
  registrant to plant. Whoever proves mailbox control sets the password, and only they can.
- **The unauthenticated-BCrypt resource lever disappears**, reversing ticket 07's note that self-registration
  becomes the most expensive unauthenticated endpoint in the application.
- **Re-registration against an unactivated record replaces it** and re-mints the token. This is retained even
  though nothing needs overwriting, because it is what lets the address owner recover a record an attacker
  created.

Residual, stated plainly because it is inherent to replace-on-unactivated rather than to this choice: an
attacker looping registrations invalidates the victim's live token roughly every 12 seconds at the 5/min per-IP
budget, so the victim must read mail and redeem inside that window. This is **an improvement on the settled
design**, where a squatted address locked the owner out permanently; we trade a permanent lockout for a noisy,
bounded race. Not mitigated further, because per-token attempt counting is machinery a reference app does not
earn. *Consolidated into the register (ticket 33): R-CRED-010. Amend the table by ID, not this list.*

**Reopening trigger, recorded here rather than in ticket 19.** [CVE-2026-56081](https://www.sentinelone.com/vulnerability-database/) (Cap-go)
is this class with a different payload: the attacker pre-registers the victim's address and then **enrols 2FA on
the pending account**, permanently locking the real owner out. We are immune only because ticket 19 made TOTP
enrolment admin-only and placed it *after* the forced password change. If self-service enrolment is ever added,
an unactivated record can accumulate a factor and this section's "nothing to overwrite" argument dies. *Consolidated into the register (ticket 33): R-CRED-011. Amend the table by ID, not this list.*

Also retired: the earlier claim that hardening activation is the largest available mitigation for §12's log
leak. It is not, for a bigger reason than any detail — reset is already a total-compromise channel for anything *Consolidated into the register (ticket 33): R-CRED-011. Amend the table by ID, not this list.*
activated, so activation hardening protects only mid-registration accounts. It is justified on pre-hijacking
alone, which is sufficient.

### 3. The username stays at registration, and its conflicts are specific

Moving the password without moving the username leaves a gap: an attacker registers username `alice` against
their own address and never activates; the real Alice registers `alice` with a new address, so there is no
pending record to replace and the username collides. Three behaviours were possible and none had been decided —
a uniform 202 with nothing created (Alice silently never has an account), a unique-constraint violation (a 500
on the one endpoint whose design goal is uniformity), or a specific conflict.

**The squat is unavoidable under every option.** There is no reaping, and ticket 11's tombstone blocks username
reuse permanently, so admin deletion makes a squat *worse* and nothing on this map can free one. The real
question is therefore whether the victim is told, which eliminates the silent 202 outright.

Once that is conceded, claiming the username at activation buys delay rather than prevention — both options
leak username existence — and the gate is weaker than it looks: a collision at activation must not consume the
token or a bad guess burns the user's only link, so failures must roll back, and one registration then buys
unlimited probes at ticket 09's 10/min.

**Decision: username at registration; a collision returns `VALIDATION_FAILED` with
`errors: [{field: "username", rule: "USERNAME_UNAVAILABLE"}]`, evaluated before the email axis is touched.**
The argument that settles it is ticket 06's own split, not a cost comparison: account state is always generic,
submitted-value quality is always specific. **Username availability is a property of the submitted value,
exactly like password strength; email existence is account state.** So this sits *inside* the existing rule
rather than being a second exception to it, and the axes separate cleanly and testably — username conflicts are
specific, email existence is never observable. Checking the username first also guarantees no username is ever
reserved by a request that collided on email. *Consolidated into the register (ticket 33): R-AUTH-001. Amend the table by ID, not this list.*

Consequences: `USER_EXISTS` stays scoped to the privileged admin path exactly as ticket 06 decided, **nothing is
added to the closed 13-code enum**, and PRD Story 1 keeps its field set so one ADR covers one restructuring
rather than two. Recorded residual: **6.3.8 (L3) is deliberately failed on the username axis**, bounded at 5/min
per source IP, with the silent-202 alternative rejected on the grounds above. *Consolidated into the register (ticket 33): R-AUTH-001. Amend the table by ID, not this list.*

### 4. Both admin credential paths become token issuance, and they converge

Ticket 11 reserved this: "Left to ticket 10: admin-initiated single password reset issuance." §4:401 makes
admin-initiated issuance an `[Enforced Constraint]`, and ASVS **6.4.6 (L3)** states the position directly — an
administrator may initiate a reset but must not be able to change or choose the user's password, precisely so
they never know it.

**Decision: `POST /api/admin/users/{uuid}/password-reset` mints a `PASSWORD_RESET` token, plaintext returned
once.** It redeems through the `/api/password-reset/confirm` endpoint the PRD requires anyway, so an
`[Enforced Constraint]` is satisfied for one endpoint and zero new machinery. Guard and factor rules inherit
from ticket 11 unchanged — `hasRole('ADMIN')`, TOTP re-verified within 10 minutes — and `actor ≠ subject` does
**not** apply, since an admin resetting their own password is legitimate.

**And admin-create becomes an invite token, amending ticket 11.** Ticket 11 chose a 20-character generated
password because the stubbed transport cannot deliver a link — but that objection died the moment Q1 accepted
the identical reasoning for admin-reset: with a stubbed transport the admin reads the link exactly as they read
the generated password, and relays it the same way. Leaving the two paths inconsistent is now the expensive
option, because it keeps both alive. Taking it **deletes** rather than adds:

- The 20-character generator goes away, and with it ticket 07 §9's re-specification of it.
- Admin-create's forced-change branch goes away — the user sets their own password at redemption, so there is
  nothing to force.
- `POST /api/admin/users` drops its password field.
- The invite reuses the **`ACTIVATION`** token type rather than earning a third, because redemption behaviour is
  identical once §3 keeps the username at issuance in both paths. Only delivery differs: emailed for
  self-registration, returned once to the admin for an invite.
- **Admin-create and self-registration converge on one pending-record-plus-token-plus-redemption path**,
  differing only in who initiates, how the token is delivered, and whether the email axis is uniform.
- Q4's `enabled && activated_at != null` now covers admin-created users too, so the composition has no special
  case.

**6.4.1 (L1) then stops biting, so no new deadline is needed.** 6.4.1 covers system-generated initial passwords
*and activation codes*, requiring secure random generation, policy conformance, and expiry after a short period
**or** after first use. Ticket 11's original shape satisfied it only via the second limb, and imperfectly — a
relayed plaintext credential kept working for up to 30 days if the user logged in and abandoned the change.
After this change **no system-generated secret is relayed by a third party anywhere in the build**: the
remaining forced-change cases are the bootstrap seed (operator-configured) and re-enable (the user's own prior
hash, no new credential issued). `credentialIssuedAt` keeps its 30-day job for those two on ticket 11's
original grounds.

**One trap this introduces, and it is the kind that ships.** An invited-but-unredeemed admin must not count
toward ticket 11's two-enrolled-admins invariant. Under the old model an admin-created admin had a credential
immediately and `activated_at = now`; under the invite model they are unactivated and unenrolled. If the guard
counts admin rows, one real admin plus one pending invite reads as two, and the real admin can demote
themselves to **zero** usable admins — defeating the invariant ticket 11 took a pessimistic row lock to
protect. The predicate becomes `activated_at IS NOT NULL` **and** TOTP-enrolled, both. *Consolidated into the ADR routing (ticket 34): ADR-048 (attached amendment). Amend by ID, not this list.*

### 5. One token table, domain-separated hashing, and a conditional consume

`credential_tokens`, with `type ∈ {ACTIVATION, PASSWORD_RESET}`. One table earns the single
expiry / single-use / single-invalidation code path this ticket's merge was justified on. The framing of
"shared table versus clearer constraints" missed the actual risk: with one table, the only thing between an
activation token and a reset token is a `type` predicate in a `WHERE` clause, and one dropped condition is
account takeover.

**So the hash is domain-separated: `SHA-256(type_label || ":" || token)`, not `SHA-256(token)`.** Cross-type
redemption then fails cryptographically rather than because a query was written correctly, converting the
failure mode from silent takeover to "token not found". Base64url's alphabet excludes `:`, so the separator
cannot be spoofed by a crafted token.

- **Generation:** 256 bits from `SecureRandom`, Base64url without padding (43 characters).
- **Storage:** the domain-separated SHA-256 of the encoded string. Plain SHA-256, not an HMAC — ticket 19 built
  the key facility so a keyed hash is cheap, but at 256 bits there is nothing to brute-force from a stolen
  table, so the marginal benefit is zero. **Recorded so a later reviewer does not file it as a finding.**
- **Lifetime:** `PASSWORD_RESET` 30 minutes; `ACTIVATION` **24 hours**, per 6.4.1 (L1)'s short-lifetime limb,
  corroborated by NIST's enrolment-code guidance for an emailed code (24 hours, versus 10 minutes for SMS) —
  noting the provenance honestly, which is the 800-63-3 implementation resources for 63A-3 §4.4.1, not 63A-4.
- **Single use:** a **conditional update** —
  `UPDATE credential_tokens SET used_at = :now WHERE token_hash = ? AND type = ? AND used_at IS NULL AND expires_at > :now`
  — proceeding only if one row was affected. Atomic in one statement, so **token rows never participate in the
  lock ordering at all**, which is cheaper than extending ticket 11's "user rows before session rows" rule to a
  third row type. JPA caveat pinned because it is exactly the thing added helpfully later: the bulk update
  bypasses the first-level cache, so it needs
  `@Modifying(clearAutomatically = true, flushAutomatically = true)` or a native query with no entity load on
  that path.
- **Reissue:** issuing any token first invalidates every pending token of the same type for that account, in the
  same transaction.
- Redemption runs inside one transaction: consume first, then set the credential through `PasswordService`. A
  validation failure rolls back the consume, so a rejected password never burns the token — which preserves
  ticket 06's ordering rule (token before password quality) without a separate verify-then-consume step.

**No entropy floor is being claimed from ASVS, and that matters.** There is no entropy requirement for reset
tokens anywhere in ASVS V6. 6.6.3's 64-bit figure is phrased as a "consider" and its binding half is the rate
limiting; the Forgot Password Cheat Sheet says only "long enough to protect against brute-force attacks". So
**256 bits is a first-principles number**, and 6.5.2's 112-bit line — below which a password-storage hash with a
salt is required, at or above which a standard hash suffices — is cited **by analogy for the hash-choice
question only**, since it is scoped to lookup secrets in the MFA section. Recording the scope prevents the
mismatch being read as a false claim. Constant-time token comparison is not needed: the lookup is hash equality
in a SQL predicate against a full-entropy value. A redemption-specific brute-force limit is theatre at 256 bits;
ticket 09's per-IP budget is sufficient.

Spring Security's one-time token support (`org.springframework.security.authentication.ott`,
`oneTimeTokenLogin()`, from Security 6.4 / Boot 3.4) is **declined on behaviour, not on storage**: OTT mints a
session on redemption, which is precisely what §8 forbids after a reset, and Security 7 grants `FACTOR_OTT` on
success, which would collide with ticket 19's factor model. Named in the ADR so a reviewer does not ask.

### 6. The link origin is never request-derived — three negative assertions

The Forgot Password Cheat Sheet is explicit that reset URLs must not be built from the `Host` header, and that
the origin should be hard-coded or validated against trusted domains. Three live advisories show three distinct
vectors: [GHSA-7pvc-gxc4-chmc](https://github.com/cubecart/v6/security/advisories/GHSA-7pvc-gxc4-chmc) (classic
`Host`), [GHSA-gv7r-3mr9-h5x8](https://github.com/advisories/GHSA-gv7r-3mr9-h5x8) (`X-Forwarded-Host`), and
[GHSA-h854-c3m3-mh5v / CVE-2026-55207](https://github.com/pimcore/pimcore/security/advisories/GHSA-h854-c3m3-mh5v)
(a caller-supplied reset-URL *body field*, with the real token appended to whatever the attacker sent).
[GHSA-5xc4-j99p-cp4m](https://github.com/getgrav/grav/security/advisories/GHSA-5xc4-j99p-cp4m) is the same class
again.

**Rule, generalised and testable: the link origin comes from configuration and is not overridable by any
request-controlled input — not `Host`, not `X-Forwarded-*`, not a body field, not a query parameter.** Three
negative assertions, one test each. The forwarded vector is closed by inheritance, since ticket 09 already
prohibited framework-level forwarded-header processing; the body-field vector is closed by never accepting one;
`Host` is closed by never reading it. The property itself goes to ticket 24 alongside the other configuration
that must exist and must not have a default. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-016, T-CRED-017, T-CRED-018. Amend the table by ID, not this list.*

**Token in the URL fragment, not the query string** — `#token=...`. A fragment is never sent to any server, so
it never reaches an access log, an intermediary, or a `Referer`. This is not a cheat-sheet recommendation (it
describes query-string tokens and prescribes `Referrer-Policy` plus rate limiting), but it is consistent with
§3.4's prohibition on secrets in query parameters and with ticket 08's narrowing of CSRF resolution to the
header alone. **It does nothing for §12's log leak** — the stub logs the whole URL, fragment included — and must
not be recorded as mitigating it. `Referrer-Policy: no-referrer` on the activation and reset pages is kept as
well, since the fragment protects the token and the header protects everything else on the page.

### 7. Issuance predicate, and redemption against a locked, disabled or unactivated account

The Standard leaves redemption-versus-account-state undefined (§2:131 covers only that an admin reset does not
clear a lock) — eleventh defect. The sharp version: a token consumed while a lock persists leaves the user with
no token and no access. *Consolidated into the register (ticket 33): R-STD-024. Amend the table by ID, not this list.*

**Issuance predicate, complete rather than case-by-case:** mint a `PASSWORD_RESET` token only if the account
exists, is activated, is enabled, and is not soft-deleted; otherwise **no-op behind the uniform 202**. Deciding
this at issuance rather than at redemption means §9's soft-delete invalidation and this section's disabled
refusal are no longer cleaning up tokens that should never have been minted. A "forgot my password" against a
never-activated account is therefore a no-op; re-sending the activation link instead was rejected because it *Consolidated into the register (ticket 33): R-STD-024. Amend the table by ID, not this list.*
makes the reset endpoint a second activation-delivery channel, doubling the paths that can mint an
`ACTIVATION` token and putting activation mail behind a budget sized for reset mail. Accepted UX cost: the *Consolidated into the register (ticket 33): R-CRED-012. Amend the table by ID, not this list.*
owner's route to a fresh activation link is re-registration, which costs one form.

**Redemption clears the lockout.** The lock exists to stop guessing at a credential that no longer exists after
redemption, so holding it past a successful reset protects nothing and only costs availability. The cheat sheet
agrees from both directions: accounts should not be locked in response to a forgotten-password attack, and no
account change should happen until a valid token is presented. Clearing after valid-token presentation
satisfies both. **Named rather than left to arrive by accident: this is WSTG-ATHN-03's tier-2 self-service
unlock**, reached through a channel we already have, gated on mailbox control and bounded by ticket 09's 3/hour
per-identifier budget. It closes the malicious-lockout residual ticket 09 accepted, so the map's *Consolidated into the ADR routing (ticket 34): ADR-009 (attached amendment). Amend by ID, not this list.*
owner-notification patch loses self-service unlock as a candidate control. It does **not** weaken ticket 09's
three arguments for the 20-minute auto-lift, because all three concern an administrator's own recovery path and
an admin without their authenticator cannot use an email-gated route either.

**Which lockout, precisely — and this sentence is what keeps §12's containment claim true.** ASVS **6.4.3 (L2)**
requires that the forgotten-password process not bypass any enabled multi-factor mechanism. Ticket 22 found a
separate TOTP failure window with no unlock path anywhere in the corpus. So: **redemption clears the password
failure counter, `last_failed_at` and `locked_until`, and never touches any TOTP counter or enrolment state.**
Stated as a positive assertion rather than an absence: redemption does not clear, reset, or re-issue TOTP
enrolment, and ticket 19's admin-resets-admin path remains the only route. Without this, an email-gated reset
would lift an MFA lockout, and a log reader could then retry TOTP against an admin freely. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-LCK-017. Amend the table by ID, not this list.*

**Disabled, unactivated or soft-deleted at redemption** (reachable only by a race against the issuance
predicate) is **refused with `RESET_TOKEN_INVALID`, identical to a bad token, and without consuming it** — so a *Consolidated into the register (ticket 33): R-STD-024. Amend the table by ID, not this list.*
later re-enable leaves the user recoverable, and no "this account is disabled" oracle exists for a token holder.
Disable is standing admin intent and a reset must not override it.

**Redemption never mints a session.** The cheat sheet is explicit that the user should log in through the usual
mechanism afterwards, because auto-login grows authentication and session-handling complexity. Redemption sets
the credential, terminates all sessions, and returns no session. The cheat sheet offers asking the user before
invalidating sessions as an alternative to doing it automatically; **automatic is chosen deliberately**, and
recorded as a choice rather than an omission. *Consolidated into the register (ticket 33): R-SES-002. Amend the table by ID, not this list.*

### 8. Self-service change and forced-change completion: one endpoint, no exemptions

**`PATCH /api/profile/password`.** Ticket 09's spelling wins over ticket 11's passing mention of `POST`,
because it sits in a table of ten concrete routes and matches the corpus's own
`PATCH /currentUser/changePassword`. Ticket 11 amended.

ASVS **6.2.3 (L1)** requires both current and new password on a change, which §3.1:240 and §2:129 also demand
"even when the caller has an active session". **The current password is verified with
`passwordEncoder.matches()` directly, not through the `AuthenticationManager`**, so the endpoint publishes no
authentication events and **does not feed the lockout counter** — ticket 09 sources counting exclusively from
`AuthenticationFailureBadCredentialsEvent`, and routing this through the manager would let anyone holding a
hijacked session lock the victim out of their own account, converting a session compromise into a denial of
service. The guessing risk it would mitigate is already bounded by 10/min per IP and by the attacker needing a
session first. Failures log at `WARN`.

The verify sits **outside** ticket 07's seam, which owns "set a password", not "check a password". Flow:
verify → `PasswordService.setPassword(...)` → terminate all **other** sessions → rotate the surviving session
id **without re-stamping `AUTH_INSTANT`** (ticket 08's single-wiring-point invariant). Cost, from ticket 07:
five BCrypt operations at cost 12, a 1.5–2 second request.

**Forced-change completion uses the same endpoint, always requires the current password, and always writes
history.** §2:166–167 omits both while §3.1:240 grants no exemption — the Standard's twelfth defect on this map,
acute because §2:130 flags re-enabled accounts. The exemption solves a problem we do not have: after §4 the *Consolidated into the register (ticket 33): R-STD-025. Amend the table by ID, not this list.*
remaining forced-change cases are the bootstrap seed (operator-configured) and re-enable (the user's own prior
password), and in both the caller demonstrably holds the credential. Removing the exemption removes a second
endpoint and a second code path, and history is automatic because the seam always inserts — which closes the
§2:167-versus-§2:204 inconsistency without a separate decision. *Consolidated into the register (ticket 33): R-STD-025. Amend the table by ID, not this list.*

**Redemption clears `forcePasswordChange`**, and admin-create no longer sets it (§4). Under §4 the user chooses
their own password at redemption, so forcing an immediate change would make them re-enter the password they just
chose as the "current" one. Ticket 11 also stamped `credentialIssuedAt` on "admin reset"; under §4 admin reset
sets no credential, so it stamps neither flag and the token's own 30-minute expiry is the deadline.

Deviation taken knowingly: §2:121 read literally ("Password changes must always use a dedicated, separate
password-reset flow") cancels the self-service endpoint, and §4:401's `[Enforced Constraint]` enumerates two
reset endpoints without including it. The self-service change endpoint is a PRD-side feature the Standard's own
architecture clause does not admit — thirteenth defect. *Consolidated into the register (ticket 33): R-CRED-013. Amend the table by ID, not this list.*

### 9. What invalidates a pending token — five triggers, enforced structurally

§3.5:356 requires only that a successful self-service change invalidate pending reset tokens. The rest was
undecided, and the gaps are asymmetric in a way that matters.

1. Any successful credential set through `PasswordService` — self-service change, forced-change completion,
   redemption — invalidates all pending `PASSWORD_RESET` tokens for that account.
2. Issuing a token invalidates prior pending tokens of the same type.
3. **Admin disable** invalidates pending tokens of both types.
4. **Admin soft-delete** invalidates pending tokens of both types.
5. Re-registration against an unactivated record invalidates its prior `ACTIVATION` token (§2).

Triggers 3 and 4 are absent from every source and are the ones that bite: without them a user disabled or
deleted while holding a live token can still redeem it, and delete would leave a token pointing at a vanished
row. The invalidation lives inside `PasswordService` and inside ticket 11's admin mutations, so it is enforced
structurally rather than remembered — mirroring ticket 08's single-seam discipline. Two of ticket 11's eight
endpoints gain a side effect.

### 10. CSRF: no exemptions on the four anonymous endpoints

Ticket 08 settled session-bound, header-only CSRF and did not exempt logout, but said nothing about anonymous
token-bearing endpoints — a gap this ticket inherits. §3.1:236 grants no exemption and PRD line 118 names
register and password reset explicitly. **Decision: no exemptions.** `GET /api/csrf` becomes a prerequisite of
registration, activation and both reset endpoints, each minting an anonymous session row; ticket 09 already
raised that budget to 30/min for exactly this traffic, taking live anonymous rows to roughly 450 per source
against the 15-minute idle window, so the cost is already paid.

Vindicated by name: the CSRF Prevention Cheat Sheet treats login CSRF as a real risk developers commonly
dismiss, names pre-sessions plus a token as the mitigation — ticket 08's deliberate pre-login session — and
warns that a pre-session must be destroyed and replaced on authentication to avoid session fixation, which
ticket 08's rotate-on-login already does. It also lists re-authentication and one-time tokens as strong
defences for security-critical operations such as password changes, which is a second justification for §8's
current-password requirement.

Reviewer-facing note for ticket 25: a curl-driven reviewer gets a 403 on `POST /api/register` until they fetch
a token first, and that will read as a bug.

### 11. Notifications: seven events, after commit, never able to fail the operation

§5:517 requires notification on password change, lockout and reset completion — and it exists **only in the test
section**, never in a behaviour clause (fourteenth defect). The Standard is silent on content, silent on whether
failure fails the operation, and has **no notification at token issuance at all**, so an administrator could *Consolidated into the register (ticket 33): R-STD-026. Amend the table by ID, not this list.*
otherwise issue a reset and kill a user's sessions with the owner never told.

1. `ACTIVATION` token issued (self-registration) — the token delivery itself.
2. Registration attempted against an activated address — the owner's only signal.
3. Reset token issued by the user — the token delivery itself.
4. **Reset token issued by an admin** — the sole detector of admin abuse, closing the §2:192–196 gap.
5. Reset completed.
6. Password changed (covers self-service and forced-change completion).
7. Account locked.

Dispatch via `@TransactionalEventListener(AFTER_COMMIT)`, so a notification failure can never roll back a
committed credential; logged at `ERROR` with `event.action`; never propagated. **After-commit dispatch is also a
deliberate timing property, not a convenience** — it is the cheat sheet's "asynchronous calls" remedy, and it is
what keeps the reset-request endpoint's exists/does-not-exist paths comparable now that §2 has removed the only
expensive asymmetry.

ASVS **6.3.7 (L3)** covers notification after updates to authentication details and is satisfied by rows 5 and
6. It does **not** reach admin unlock or re-enable, whose text is about authentication details rather than *Consolidated into the register (ticket 33): R-STD-026. Amend the table by ID, not this list.*
account state; ticket 11 already placed disable, delete and unlock on the map's owner-notification patch, so
only **re-enable** is added there as a fourth. **6.3.5 (L3)** — notification of a successful login after several
failures — is taken, since the listener exists and the cost is one predicate.

Channels, retry and delivery-failure handling stay on the map's owner-notification patch, which already owns
them. Admin-invite delivery is **not** a notification: the token is returned once in the response, as in §4.

### 12. The log leak is total for users and partial for admins, and that makes ticket 19 load-bearing

The stubbed `EmailService` logs the link it was asked to send, so **anyone who can read application logs can
take over any activated account** by requesting a reset. This is inherent to PRD line 20, not introduced here,
and it is the worst security property of the build. It is deployment-blocking for ticket 25, not a footnote.

The one containment fact worth stating, and it is more useful than any mitigation we could add: **the leak is
total for ordinary users and partial for administrators, and the only thing containing it is ticket 19's TOTP.**
A log reader who resets an admin's password authenticates with `FACTOR_PASSWORD` only, and every
`/api/admin/**` row requires the TOTP factor — including `DELETE /api/admin/users/{uuid}/totp`, so they cannot
bootstrap their own factor. That makes ticket 19 load-bearing for a risk it was never scoped against. Two
caveats the handover must carry: containment is **confidentiality-only**, since the attacker can still change
the admin's password and lock the real admin out until that admin recovers through the same email channel; and
the fragment in §6 does not help here, because the stub logs the whole URL. *Consolidated into the register (ticket 33): R-CRED-020. Amend the table by ID, not this list.*

### 13. ASVS: the level this build targets, and what is knowingly failed

Citing ASVS without declaring a level was sloppy — four of the seven requirements load-bearing on this ticket
are L3. **Target: L1, with named L2 and L3 controls adopted where they are cheap.** A whole-application L2 claim
would be false, and the reason is already a decision on this map: **6.3.3 (L2)** requires MFA, or a combination
of single-factor mechanisms, to access *the application*, and ticket 19 scoped TOTP to the admin surface with
MFA for regular users ruled out of scope. *Consolidated into the register (ticket 33): R-STD-027. Amend the table by ID, not this list.*

Satisfied and worth recording, because a reviewer will look: **6.2.3 (L1)** current-and-new password (§8);
**6.4.1 (L1)** initial secrets and activation codes — after §4 the only system-generated secrets are tokens with
24-hour and 30-minute lifetimes, so the *short-lifetime* limb is satisfied on its own terms rather than the
first-use limb, and the mapping no longer depends on which limb is tested; **6.3.2 (L1)** default accounts —
already satisfied by ticket 11 line 384, "credentials from the environment only … no default, no
generate-and-log fallback", and more strongly than 6.3.2 asks, since it also refuses to re-seed around a
deliberate disable; **6.2.8 (L1)** verify-as-submitted — already satisfied and documented by ticket 07, which
round-trips NFC at set *and* verify (line 467), declines trimming, case folding and space collapsing on record,
and rejects over-72-byte input rather than truncating it; **6.2.4 (L1)** blocklist scoped to passwords matching
the policy, which is exactly ticket 07's breach corpus sliced at ≥15 characters and exactly why its earlier
top-100k-filtered sizing was wrong; **6.2.10 (L2)** no periodic rotation; **6.2.12 (L2)** breached-password
check; **6.4.3 (L2)** reset does not bypass MFA (§7); **6.5.1/6.5.3 (L2)** single-use and CSPRNG for tokens;
**6.1.1 (L1)** already cited by ticket 09.

Knowingly failed, each with the cause named:

- **6.3.8 (L3)** on the username axis at registration (§3), bounded at 5/min per IP; the alternative produces an
  unfixable squat. *Consolidated into the register (ticket 33): R-AUTH-001. Amend the table by ID, not this list.*
- **6.2.9 (L2)** — "passwords of at least 64 characters are permitted" is stated in *characters* while ticket
  07's ceiling is 72 *bytes*. 64 ASCII characters fit; 64 characters of CJK text is roughly 192 bytes and is
  rejected, as is a 40-character accented-Latin passphrase. The cause is the pre-hash ticket 07 examined and
  declined because a peppered hash cannot be rotated — so this is an **unrecorded consequence of a decision
  already taken**, not a new one. Affected population: non-Latin-script users. Written down deliberately,
  because "we quietly reject long passwords in Chinese" is a finding to own rather than to have found, and the
  map's PDPA framing makes it worse if discovered.
- **6.4.6 (L3)** is now *satisfied* by §4 rather than failed, which was the main reason to take the invite
  token.
- **6.1.2 / 6.2.11 (L2)** — the *mechanism* already exists: ticket 07 passes username, email local-part and
  service name to zxcvbn as user inputs and has a `CONTEXT_TERM` rule. What is missing is the **documented**
  list extended to organisation, product and project-codename permutations. Ticket 07 amendment, near-free, and
  it catches `SecuredHelloWorld2026!` which no breach corpus will.
- **6.2.7 (L1)** — paste, browser helpers and external password managers must work. Mostly a ticket 14 item and
  mostly about not being clever, but it also means correct `autocomplete` values on every password field
  (`new-password` on registration-activation and change-new, `current-password` on login and change-current).
  The autocomplete specifics are inference from the requirement, not its text. Manager compatibility is the
  actual test, and getting it wrong is invisible until someone tries. *Consolidated into the register (ticket 33): R-FE-001. Amend the table by ID, not this list.*
- **6.3.4 (L2)** — where multiple authentication pathways exist, none may be undocumented and controls must be
  consistent. After §2, §4 and §8 this build has password login, activation redemption, admin-invite redemption,
  user-initiated reset redemption, admin-issued reset redemption, forced change, and the bootstrap seed. So
  ticket 25 owes a **pathway table**: one row per route to a credential or a session, with the proof required,
  the factors enforced, the rate limit, and the notification emitted. Half a page, the artefact a reviewer asks
  for anyway, and building it surfaces remaining inconsistency mechanically rather than by argument. *Consolidated into the register (ticket 33): R-MFA-014. Amend the table by ID, not this list.*

### 14. Email format, and the one canonicalisation assertion left

Email is **mandatory**; Jakarta `@Email` plus an explicit **254-byte** cap (RFC 5321's path limit, which also
gives ticket 12 a column width), applied after ticket 11's NFC-normalise-trim-lowercase. Reject an empty local
part and a domain with no dot, which is `@Email`'s known permissiveness and cheap to close. No deliverability or
MX check: the transport is a stub, so a check that cannot be exercised is a check that rots. A 64-byte local-part
cap is one more line if ticket 12 wants it.

One negative assertion owed as a test: **ticket 11's identifier canonicalisation helper is never applied to a
password field.** It is the obvious thing for a later contributor to reuse on the wrong argument, and ticket 07
already established that any set/verify asymmetry locks a user out with no error that could explain it. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-007. Amend the table by ID, not this list.*

### Deviations owed as ADRs

1. **Self-registration is restructured into two steps.** PRD Story 1 keeps its field set but the password moves
   to activation, and the "clear validation error (username/email conflict)" criterion is met on the username
   axis and deliberately not on the email axis. One ADR covering the uniform 202, the activation step, and the
   credential's move; cite CVE-2026-48117 and GHSA-qq9h-g4jm-xgf3, and 6.3.8 (L3). *Consolidated into the ADR routing (ticket 34): ADR-032. Amend by ID, not this list.*
2. **Admin-create becomes an invite token**, amending ticket 11. Cite 6.4.6 (L3) and 6.4.1 (L1); record that
   ticket 11's "an undeliverable link is a bricked account" objection is retired by the same reasoning that
   admitted the admin-reset token. *Consolidated into the ADR routing (ticket 34): ADR-006 / 11-X-1 dropped (superseded; see its routing §4). Amend by ID, not this list.*
3. **Admin-initiated reset issuance exists and is token-based**, satisfying §4:401 and resolving the Standard's
   own §2-versus-§3.5 contradiction against §3.5. Cite 6.4.6 (L3). *Consolidated into the ADR routing (ticket 34): ADR-006. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-STD-020. Amend the table by ID, not this list.*
4. **One `credential_tokens` table with domain-separated hashing**, deviating from the PRD's named
   `password_reset_tokens`. Record why plain SHA-256 rather than an HMAC, why 256 bits is first-principles
   rather than standards-derived, and why Spring Security's OTT support was declined on behaviour. *Consolidated into the ADR routing (ticket 34): ADR-007. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-DATA-002. Amend the table by ID, not this list.*
5. **Self-service change endpoint exists** despite §2:121 and §4:401, and always requires the current password
   with no forced-change exemption, deviating from §2:166–167. *Consolidated into the ADR routing (ticket 34): ADR-008. Amend by ID, not this list.* *Consolidated into the register (ticket 33): R-CRED-013. Amend the table by ID, not this list.*
6. **Redemption clears the password lockout** but never TOTP state, deviating from §2:131's silence and
   deliberately implementing WSTG-ATHN-03 tier 2; note it supersedes ticket 09's accepted residual. *Consolidated into the ADR routing (ticket 34): ADR-009. Amend by ID, not this list.*
7. **6.2.9 (L2) is failed for passwords over 72 UTF-8 bytes**, caused by ticket 07's declined pre-hash. *Consolidated into the ADR routing (ticket 34): ADR-003. Amend by ID, not this list.*
8. **Reset and activation links are never built from request-controlled input** — three negative assertions with
   one test each. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-016, T-CRED-017, T-CRED-018. Amend the table by ID, not this list.* *Consolidated into the ADR routing (ticket 34): REJ-022. Amend by ID, not this list.*

### Glossary terms owed

`pending registration`, `activation token`, `invite token`, `credential token`, `domain-separated token hash`,
`link origin`, `authentication pathway`.

### Handoffs

- **12 (data model):** `credential_tokens` shape — `id`, `user_id`, `type`, `token_hash`, `expires_at`,
  `used_at`, `created_at`; `users.activated_at` nullable; email column at 254 bytes; whether a 64-byte
  local-part cap is worth a constraint. Token rows take no locks.
- **13 (audit catalogue):** registration attempted / activation issued / activation redeemed / reset requested /
  reset issued (user and admin variants) / reset redeemed / password changed / invite issued / invite redeemed,
  plus the `USERNAME_UNAVAILABLE` rejection. `user.target.id` applies to the admin-initiated variants.
- **14 (frontend):** confirm-the-password-twice on activation and reset; `Referrer-Policy: no-referrer` on both
  pages; fragment-read-then-POST for both tokens; `autocomplete` values per 6.2.7; the two-step registration
  form; and the 72-**byte** counter ticket 07 already owed.
- **16 (test plan):** cross-type token redemption fails; conditional-consume race; rollback restores the token
  on a rejected password; identical registration response bodies and comparable timings across all account
  states on the email axis; `Host`/`X-Forwarded-Host`/body-field link-origin assertions; redemption clears the
  password counter and never TOTP; an invited-but-unredeemed admin does not satisfy the two-admin invariant.
- **20 (origins):** `Referrer-Policy: no-referrer` may live with the other headers if preferred.
- **24 (secrets/config):** the link-origin property — must exist, must not have a default, must not be
  request-overridable.
- **25 (handover):** the log leak as deployment-blocking, with the TOTP containment sentence and its
  confidentiality-only caveat; the 6.3.4 pathway table; the curl-403 note. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-011, T-CRED-014, T-CRED-015, T-AUTH-014, T-ADM-010, T-CRED-016, T-CRED-017, T-CRED-018, T-LCK-017. Amend the table by ID, not this list.*

### Amendments to other tickets

- **06:** `VALIDATION_FAILED` gains rule `USERNAME_UNAVAILABLE`; the enum stays at 13 codes and `USER_EXISTS`
  stays privileged-only; the activation-redemption failure reuses `RESET_TOKEN_INVALID`; the illustrative
  `rule` list is superseded by ticket 07's closed six. *Consolidated into the ADR routing (ticket 34): spec §Error contract. Amend by ID, not this list.*
- **07:** §9's 20-character admin generator is **deleted** with admin-create (§4); the documented
  context-specific word list is extended per 6.1.2; 6.2.4 and 6.2.10 recorded as satisfied; 6.2.9 recorded as
  failed for multibyte passwords. *Consolidated into the ADR routing (ticket 34): ADR-006 / ADR-003 / REJ-004. Amend by ID, not this list.*
- **08:** the invalidation table's "admin reset token issuance" row is correct as named; add activation and
  invite redemption as vacuous (no sessions exist); `api.base-path` is `/api`, not `/api/v1` — ticket 08 is the
  stale outlier against tickets 09, 10 and 11. *Consolidated into the ADR routing (ticket 34): ADR-037 (attached amendment). Amend by ID, not this list.*
- **09:** cross-reference §7's self-service-unlock consequence, which supersedes its accepted malicious-lockout
  residual; the self-service change endpoint does not feed the lockout counter; `/api/register` and
  `/api/register/activate` budgets unchanged.
- **11:** admin-create becomes an invite token and drops its password field; the two-enrolled-admins predicate
  becomes `activated_at IS NOT NULL` **and** TOTP-enrolled; `credentialIssuedAt` is no longer stamped on admin
  reset; forced-change cases reduce to bootstrap seed and re-enable; change-password verb is `PATCH`; admin
  disable and soft-delete gain token invalidation; 6.3.2 recorded as satisfied by its bootstrap decision. *Consolidated into the ADR routing (ticket 34): ADR-046 (attached amendment). Amend by ID, not this list.*
- **19:** recorded as load-bearing for the log-leak containment it was never scoped against; self-service TOTP
  enrolment is a named reopening trigger for §2 (CVE-2026-56081). *Consolidated into the register (ticket 33): R-CRED-011. Amend the table by ID, not this list.*
- **22:** its TOTP-lockout finding is why §7 pins which counter redemption clears.

### Done when

All three flows are specified end to end (yes), the shared-versus-separate token table question is answered
(one table, domain-separated), the two reset issuance models are reconciled (both exist onto one redemption
endpoint, both token-based), and the unverified-versus-disabled distinction is explicit (`activated_at`
orthogonal to `enabled`, composed once in `isEnabled()`).

---

## Amendment from ticket 12 (data model reconciliation)

**Delete the admin-soft-delete limb from §9's invalidation triggers. Five become four.** This is an amendment rather
than a note precisely because a note would get implemented anyway.

Ticket 11's delete removes the `users` row outright — "removed from the active table with a tombstone retained", with
a reuse check spanning two repositories — so ticket 12 puts `ON DELETE CASCADE` on `credential_tokens.user_id`. The
token rows therefore **vanish with the user**, which is strictly stronger than stamping `used_at`, and stamping them
first is a second code path for a guarantee the database already makes. The remaining four triggers are unchanged:
any successful credential set through `PasswordService`; issuance invalidating prior pending tokens of the same type;
admin disable; and re-registration against an unactivated record.

The concern behind §9's trigger 4 — "delete would leave a token pointing at a vanished row" — is now answered
structurally rather than behaviourally, and the reason matters: **a surviving token row after deletion is a live
redemption path for a deleted account**, which is the worst failure available in this schema, so it is worth having
the database guarantee rather than a remembered call.

Three smaller confirmations from ticket 12's table set, none of which change a decision here:

- `credential_tokens` is exactly §5's shape — `id`, `user_id`, `type`, `token_hash`, `expires_at`, `used_at`,
  `created_at`. `used_at` is the nullable timestamp this ticket decided, not the `used` flag its "settled going in"
  text described.
- `token_hash` is **lowercase hex in `VARCHAR(64)`**, with lowercase part of the contract because an equality lookup
  that misses on case fails open on the reset path. `type` is `@Enumerated(STRING)` in `VARCHAR(16)` behind a named
  check constraint over the two values. The unique index is on **`token_hash` alone**: domain separation makes a
  cross-type collision cryptographically unavailable, so the `type` term in the consume predicate is defence in depth
  in the query rather than in the index.
- The email column is **254 bytes** as this ticket specified. The optional 64-byte local-part cap is **declined** —
  it is a validation rule, and expressing it in DDL would need an expression check H2 cannot index against and
  Postgres spells differently, for something Jakarta `@Email` already carries.

One DDL comment is owed on this table and is not decorative: **it stores only a hash.** It is the column most likely
to acquire a "for debugging" plaintext sibling later, and the one place where that would be catastrophic rather than
untidy. *Consolidated into the ADR routing (ticket 34): ADR-007 (attached amendment). Amend by ID, not this list.*

---

## Amendment from ticket 09 (§R, the ticket 21 reopening) — redemption now clears more, and your §12 leak has an upside and a limit

### 1. Redemption clears the NIST cap as well as the lockout

Your amendment already had redemption clear `failed_login_attempts`, `last_failed_at` and `locked_until`. It now also
clears **`consecutive_failures_since_success` and `password_disabled_at`**, ticket 09 §R's cap counter and
authenticator-disabled state — because setting a new password **is** the rebinding NIST §3.2.2 requires of a disabled
authenticator, and §4.2 names account recovery as the route where no other authenticator is available.

Unchanged: it still **never touches any TOTP counter or enrolment state**, so ASVS 6.4.3 (L2) holds and ticket 23's
tier 2 is unreachable from here. Token *issuance* clears nothing, which keeps ticket 09's ADR 8 intact, and ticket
11's `unlock` endpoint clears nothing either — unlock destroys no credential, so it is not rebinding.

**Ticket 09 §R.3 adds a second redemption source, not a second mechanism:** an operator-invoked runner
(`--rebind=<username>`, scoped, batched) that invalidates the hash and mints a single-use token through **your**
machinery. No new token type, no new table, no new expiry. It exists because of §2 below. *Consolidated into the ADR routing (ticket 34): ADR-009 (attached amendment). Amend by ID, not this list.*

### 2. Your §12 leak is why the cap needed an operator entrypoint, and ticket 13 is why

Ticket 09 initially concluded that a capped sole admin could recover by reading the reset link from the application
log — your §12 finding. **That is true only under `dev`.** Ticket 13 §13 confines the stub's link to a separate
non-audit logger emitting only under the `dev` profile, enforced three ways (a ticket 24 prohibited-configuration
entry, an `ApplicationReadyEvent` check reading the effective level through `LoggingSystem.getLoggerConfiguration`,
and `/actuator/loggers` absent or read-only). So **outside `dev` a reset request produces no deliverable artefact at
all** — not emailed, not logged, not returned. *Consolidated into the register (ticket 33): R-CRED-021. Amend the table by ID, not this list.*

Worth recording for whoever reads your §12 next: the total-leak finding and the no-channel finding are **the same
design seen from two profiles**, and both are true. Your prohibition on logging the **admin-issuance** plaintext
token is a third, separate thing, and ticket 09 conflated it with the emailed link before checking. *Consolidated into the register (ticket 33): R-CRED-020. Amend the table by ID, not this list.*

### 3. Your specific username conflict is the discovery step for a mass primitive, and it is not revisited

Ticket 09 §R.2 quantifies a single-source mass permanent-lockout primitive: ~100 unauthenticated requests per
account, 36 permanent disables per hour from one host at its permitted rate. **Its discovery step is
`USERNAME_UNAVAILABLE`** — 240 usernames *confirmed* rather than guessed, in ~48 minutes at the 5/min registration
budget.

**Your decision stands and was argued correctly**: the squat is unavoidable under every option, ticket 11's tombstone
blocks reuse forever, and a silent 202 leaves the victim with no account and no explanation. It is named in ticket 09
so the attack chain is complete, not to reopen the trade. The consequence lands elsewhere instead — a reserved-name
denylist in **one set with two readers**, ticket 11's bootstrap validator and **your registration username
validator**, justified as an availability control against targeting and explicitly not as anti-enumeration, since
availability of a name stays confirmable either way.

### 4. One invariant on your redemption path, worth more than the tests around it

**Assert that the reset-request and redemption paths never route through `AuthenticationManager`.** They do not today
— ticket 09 §R.4's checker lives in the provider, and your self-service change endpoint calls
`passwordEncoder.matches()` directly — but if redemption is ever routed through the manager, the disabled password
authenticator blocks **the only operation that can clear it**. A recovery path that is self-blocking fails exactly
when it is needed, and silently. Ticket 16 carries the test; the reasoning lives here because this is the path. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-CRED-009. Amend the table by ID, not this list.*

---

## Amendment from ticket 25 — the token machinery acquires a second identity, and a recovery-address axis

**1. `credential_tokens` gains a fourth type with a NIST identity attached.** Ticket 25 resolved that admin
break-glass is NIST **§4.2 account recovery**, and that at AAL2 §4.2.2.2 option 2 is satisfied by an **issued
recovery code** plus the admin's password — which qualifies, because §3.1.1 types a password as "something you
know" and nothing in §4.2.2.2 excludes the factor the subscriber still holds.

The consequence for this ticket: ticket 09 §R.3's runner mints its token **through this ticket's machinery**, so the
domain-separated hashing (`SHA-256(type ‖ ":" ‖ token)`) and the conditional-update single-use path already cover
it. What is new is that the token is no longer only an internal recovery artefact — it is an **issued recovery
code** in NIST's sense once delivered, which attaches three requirements this ticket's four token types did not
previously carry:

- **Validity is capped by channel**: 24 hours when sent to an email address, per §4.2.1.2 — longer than this
  ticket's 30-minute reset expiry, so the type needs its own TTL rather than inheriting.
- **Verification is throttled**: "The verification of issued recovery codes SHALL be subject to the throttling
  requirements in Sec. 3.2.2" — a third counter, recorded on ticket 09.
- **Channel is permitted, explicitly.** §3.1.3.1 carves out that codes "issued as recovery codes (see Sec. 4.2.1.2)
  are not authentication processes and not affected by the above prohibition" on email for out-of-band
  authentication. So emailing this one is sanctioned where emailing a §4.1.2.2 binding code is prohibited outright.

**2. A recovery-address axis, distinct from the notification-address one.** §4.2.1.2 carries its own SHALL — "CSPs
SHALL allow the subscriber to establish at least two recovery addresses" — plus "A recovery address SHALL be
established only after the subscriber provides the correct confirmation code to the CSP", and confirmation codes
carry "the same characteristics as a recovery code". This is **not** §4.6's two-notification-address SHALL:
different subject, different verb, no cross-reference between them.

It lands here because establishing and confirming an address is credential-flow work, and because ticket 25's chosen
mitigation depends on it: the correlation §3.1.3.1 names ("Access using only a password") is broken by requiring the
admin's **recovery address to differ from the login address**, which converts an obligation already owed on this
route into the mitigation rather than adding a feature.

**3. The reset-link log leak gets sharper, not milder.** This ticket established that the stubbed transport logs the
link. The same transport is now the delivery channel for a recovery code that restores admin access after
authenticator loss — so in `dev` the leak reaches the one credential whose whole purpose is surviving TOTP loss.
Ticket 13's `dev`-only confinement with three enforcement points is what contains it, and ticket 25 records the
real-mail-transport requirement as **deployment-blocking** partly on this basis. *Consolidated into the ADR routing (ticket 34): ADR-007 (attached amendment). Amend by ID, not this list.*

--- *Consolidated into the register (ticket 33): R-CRED-020. Amend the table by ID, not this list.*

## Amendment from ticket 30 (sole-admin bootstrap premise)

Two stale lines with one root cause, both from before ticket 28:

- **10:727–729** ("invalidates the hash and mints a single-use token through **your** machinery") and **10:775**
  ("ticket 09 §R.3's runner mints its token through this ticket's machinery"): the runner **mints nothing**. Ticket 28
  §3 inverted the channel — the operator supplies the new password on stdin or a prompt, it goes through
  `PasswordService`, `force_password_change` is set, and the batch leg only invalidates. The in-place record is
  ticket 25's TM-12 section (25:891–902). The issued-recovery-code token type (§ from ticket 25) is unaffected, but
  no longer has the runner as its minting source.
- **Consequence for this ticket's admin-reset endpoint:** [ticket 30](30-sole-admin-bootstrap-premise.md) makes it
  ADR 13's route 2 for a forgotten password or a single-account cap, whenever another `authenticable` admin exists.
  Redemption clearing the cap (10:719) is now load-bearing for that route. *Consolidated into the ADR routing (ticket 34): ADR-007 (attached amendment). Amend by ID, not this list.*
