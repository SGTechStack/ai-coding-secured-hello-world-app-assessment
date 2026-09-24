# 10 — Decide the credential flows: registration, password reset, self-service change

Type: grilling
Status: open
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
