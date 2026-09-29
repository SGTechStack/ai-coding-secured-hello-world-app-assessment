---
status: accepted
---

# ADR-032: Enumeration-resistant two-step registration: uniform 202, password set at activation

Self-registration takes a username and an email address and always answers `202 Accepted` with the same body. It
never says whether the email is already registered. No password is collected at registration. The password is set
when the activation token is redeemed, and the account becomes usable only then. PRD Story 1 says the opposite: an
account created and enabled on submit, and "a clear validation error (username/email conflict)". A maintainer
holding the PRD would restore both.

## Context

- The governing standard's §2 Decision Logic requires authentication-related endpoints to return consistent bodies,
  status codes and response times whether or not the account exists. ASVS 5.0 **6.3.8 (L3)** names error messages,
  status codes and response times, and extends the protection to registration explicitly. The same standard's §3.2
  lists a `user exist` error. The PRD makes a specific conflict error an acceptance criterion. These cannot all hold.
- With no email delivery in scope, a bare uniform success with no follow-up would leave a genuine registrant with a
  success they cannot act on. An activation step is needed anyway. The standard's Questions file already prescribes
  hashing for activation tokens, so the corpus expects this flow.
- **Collecting the password at registration opens a takeover.** An attacker re-registers a victim's pending address
  with their own password, and the fresh activation link still goes to the victim's inbox. The victim clicks it and
  activates an account carrying the attacker's password. CVE-2026-48117 shipped this shape: registration with a
  victim's email and an attacker-chosen password before the victim finished activating. GHSA-qq9h-g4jm-xgf3
  (better-auth) is the same pre-account-hijacking class. Its fix treats proof of mailbox control as authoritative
  and removes any password set before that proof.
- **It also opens a timing channel.** Hashing a password at BCrypt cost 12 (ADR-001) takes hundreds of milliseconds.
  The path for a new address would hash, and the path for an existing one would not, so the uniform 202 would leak
  through time. The framework's dummy-hash mitigation exists only on the login path.

## Considered options

- **PRD as written: create and enable on submit, specific conflict errors.** Enumerates every registered email.
- **Uniform 202, password at registration, activation token to confirm.** Leaves the takeover and the timing
  channel, and a decoy hash to hide the timing would be a permanent cost on an anonymous endpoint.
- **Uniform 202, password at activation (chosen).** Deletes both problems at their source.

## Decision

- `POST /api/register` takes `{username, email}`. It always returns the same 202. A **new** address creates a pending
  registration and mints an activation token (ADR-007). A pending address is **replaced**, with a new token. An
  address belonging to an activated account, or to a deleted-user tombstone, creates nothing.
- `POST /api/register/activate` takes the token and the password. Only whoever controls the mailbox can set the
  password. A failed redemption returns `RESET_TOKEN_INVALID`. A distinct code would tell an anonymous caller which
  kind of token they hold.
- **The username axis stays specific.** A taken username returns `VALIDATION_FAILED` with rule
  `USERNAME_UNAVAILABLE`. This applies the error contract's split rather than making an exception to it. Username
  availability is a property of the submitted value, like password strength. Email existence is account state and is
  never observable. The username is checked **before** the email axis. Every registration that passes it holds the
  username whatever the email's state (amended 2026-09-29; before, a request that would collide on email reserved
  nothing, which made the username axis an email oracle).
- `USER_EXISTS` is used only on admin-initiated creation, where the caller is already privileged. It covers tombstone
  hits there too. Admin creation issues an invite token (ADR-006).

## Consequences

- **ASVS 6.3.8 (L3) is knowingly failed on the username axis**, bounded by the per-source registration budget. A
  silent 202 on a username collision was rejected. A squatted username can never be freed (the tombstone blocks
  reuse, ADR-044), so the only question is whether the victim is told. The same signal lets an attacker confirm
  usernames cheaply, which is the discovery step ADR-015's cardinality axis is sized against.
- **Replace-on-pending leaves a noisy race.** An attacker looping registrations against a victim's pending address
  invalidates each new token within one registration interval, so the victim must redeem quickly. This trades the
  permanent lockout a squatted address would otherwise cause for a bounded race.
- The PRD Story 1 criteria met differently (enabled on submit; the email-axis conflict error) are recorded in the
  deferral register.
- The registration row in the audit log is specific (new account or existing address). Only the wire is uniform.
- **Reopening trigger:** any self-service second-factor enrolment. CVE-2026-56081 (Cap-go) is this class with a
  different payload: the attacker enables 2FA on the pre-registered account and locks the owner out. That is closed
  here only because enrolment is admin-only and happens after activation (ADR-023).
- Tests: T-AUTH-014 (identical 202 and zero password-encoder calls across new, pending and activated addresses),
  T-AUTH-006, T-AUTH-018 (the two-request username probe), T-CRED-026 (the 24-hour lapse) and T-CRED-027
  (re-registration and activation serialised).

## Amendment (2026-09-29): every registration holds its username, and pending registrations lapse

**The defect (review of tickets 14 to 16, H1).** "A request that would collide on email never reserves a username"
made the username axis an oracle for the email axis. Registering a fresh username with the victim's address, then
the same username with the attacker's address, answered 400 `USERNAME_UNAVAILABLE` if the first request had created a
pending registration (the address was new or pending) and 202 if it had created nothing (the address belonged to an
activated account, an invite or a tombstone). Two requests told an anonymous caller whether an address had an
account, which this ADR says is never observable.

**Decision (the user's).**
- **Every well-formed registration holds its username for the pending period, whatever state the email is in.** A
  `username_holds` row (username, canonical email, expiry) is taken or renewed by each registration that passes the
  username check. While it lasts, the username is unavailable to any other address, and the same address may repeat.
  Both steps of the probe now answer alike for a new, pending, activated, invited or tombstoned address: 202, then
  400 `USERNAME_UNAVAILABLE`. The hold is state the email axis never reads, so it reveals nothing.
- **Pending registrations lapse after 24 hours**, the activation token's lifetime (ADR-007), counted from the last
  registration. A lapsed self-registered pending registration no longer blocks its username: the next registration of
  that username from another address deletes it and takes the name. Expired holds are purged by each registration,
  so a squatted username frees up within a day instead of never. A lapsed pending registration is deleted without a
  tombstone: it never held a credential, and a tombstone would squat the name for good (ADR-044 covers deleted
  accounts). An administrator's pending invite never lapses this way.
- **A re-registration and an activation of one pending registration are serialised** on the account's row lock
  (`SELECT ... FOR UPDATE`, R-DATA-014), which both take before touching its tokens, in the same order. Before, a
  replace could rename an account whose activation had just committed, and mint an activation token for an activated
  account (review of tickets 14 to 16, M2).

**Consequences.** Any registration, of any address, squats its username for up to 24 hours: the cost a pending
registration already carried, now bounded. The username axis still discloses whether a username is taken or held
(R-AUTH-001); it no longer discloses anything about the email.


## Sources

- PRD Story 1, first and second acceptance criteria.
- Standalone User Access Control Application Standard §2 Decision Logic, §3.2 Error Contract; the standard's
  Questions file on activation-token hashing.
- OWASP ASVS 5.0, V6.3, 6.3.8 (L3).
- CVE-2026-48117 (DroneAware account pre-hijacking), NVD and SentinelOne entries.
- GitHub advisory GHSA-qq9h-g4jm-xgf3 (better-auth, pre-account hijacking on magic-link and email-OTP sign-in).
- CVE-2026-56081 (Cap-go, pre-registration followed by 2FA enrolment), SentinelOne entry.
