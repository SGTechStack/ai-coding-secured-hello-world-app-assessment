---
status: accepted
---

# ADR-070: Break-glass recovery as NIST SP 800-63B-4 §4.2.2.2 option 2, under an adopted reading of §4.2.1

An administrator who has lost the authenticator they need recovers access through NIST SP 800-63B-4's account
recovery rules. The target route is §4.2.2.2's second option: **one issued recovery code, plus authentication with
the password that is still bound to the account**. That route needs mail transport, which this build does not
have. Until it exists, the offline operator runner (ADR-072) is the only route. It is recorded as an accepted
failure with a single named cause, not as a compliant method.

This rests on a reading of §4.2.1 that could reasonably go the other way. We adopted the stricter reading on
purpose and write it down here, so a later reader does not find the looser clause and think it was missed.

## Context: this is §4.2 recovery, not §4.1.2 binding

- §4.2 defines account recovery by the situation: a subscriber "recovers from losing control of the authenticators
  that are needed to authenticate at a desired AAL". An admin whose TOTP device is gone is in that situation.
- §4.2 carves out one converse case: replacing a forgotten password when the subscriber can still authenticate with
  another authenticator counts as binding a new authenticator. That is not this case.
- §4.2's requirements are addressed to the CSP. Being the CSP does not exempt us; it makes us the addressee.
- The binding reading would instead require authentication at the account's maximum available AAL (§4.1.2.1),
  which is exactly what someone who has lost their authenticator cannot supply.

## The adopted reading of §4.2.1

§4.2.1 recognises four classes of recovery method: saved recovery codes, issued recovery codes, recovery contacts,
and repeated identity proofing. It says CSPs "SHALL support one or more of these and MAY support an
application-specific method (e.g., interaction with a CSP agent)", and that "the use of alternative methods SHALL be
based on a risk analysis and documented".

There are two readings:

- **Reading A:** the application-specific method is a sanctioned alternative. An operator procedure plus a risk
  analysis would be a compliant recovery route.
- **Reading B (adopted):** the general rule governs the specific one. An application-specific method must still
  land inside one of the classes that §4.2.2 requires.

We adopt Reading B because it is better supported by the text:

- §4.2.2.1 and §4.2.2.2 are unconditional SHALLs over closed lists.
- §4.2.2 contains no carve-out for application-specific methods.
- §4.2's own summary of recovery omits agent interaction.

It is the better-supported reading, not a certain one. The residual doubt rests on the word "alternative" and on
§4.2.2's silence. Under Reading B the operator runner is not a compliant recovery method, so it is graded as the
interim, not as the route.

## Decision

- **The route is §4.2.2.2 option 2.** Our accounts are not identity-proofed, and an admin account can reach AAL2
  (password plus TOTP). §4.2.2.2 lists "one recovery code from the set (i.e., saved, issued, and recovery contacts)
  plus authentication with a single-factor authenticator that is bound to the subscriber account".
  - **The password qualifies** as that single-factor authenticator. §3.1.1 says a password is "something you
    know", and §4.2.2.2 sets no different-factor rule and no bar on the factor the subscriber still holds. §4.2's
    introduction also contemplates recovery "perhaps in conjunction with using an authenticator that is still
    available to the subscriber bound to their subscriber account".
  - **The recovery code is an issued recovery code** (§4.2.1.2), sent by email to a recovery address. Email is
    permitted here: §3.1.3.1 exempts recovery codes from its prohibition on email for out-of-band authentication.
    An emailed code is valid for at most 24 hours.
- **The password/mailbox correlation** that this route opens is mitigated by a recovery address distinct from the
  login address (ADR-071).
- **Until mail transport exists, the offline runner (ADR-072, ADR-073) is the only route.** It is an accepted failure
  whose single cause is the absence of transport. The §4.2.1 risk analysis is written anyway, as the interim
  compensating artefact, not as the compliance route.

## Considered options

- **Reading A, with the runner as the application-specific method.** It would give a compliant route today. It
  banks the looser half of a clause whose stricter half we would otherwise cite, so it was withdrawn.
- **Treat recovery as §4.1.2 binding.** Its binding code "SHALL NOT be communicated over any insecure channel
  (e.g., email)", is single-use, and lasts at most 10 minutes. That forbids the very channel that makes recovery
  possible here.
- **Option 1: two recovery codes by different methods.** It is stronger, but it needs a second recovery method.
  Saved codes are deferred (ADR-024), and recovery contacts are rejected (ADR-071).
- **Option 2 with a distinct recovery address (chosen).**

## Consequences

- **Grade.** The half of recovery that invalidates the lost authenticator is satisfied on the CSP side. The half
  that restores access is a **conditional pass with mail transport as its only prerequisite**, and an accepted
  failure until then. The account-recovery notification SHALL (§4.2.3) remains failed, for the same cause.
- **Declining saved recovery codes is not a NIST failure.** §4.2.1.1's issuance clause is a SHOULD, scoped to "a
  CSP that supports this recovery option".
- **The 15-character password floor binds administrators too** once their password serves as the recovery
  companion, in addition to binding every password-only user (ADR-002).
- **Recovery-code verification is a third throttled counter** under §3.2.2, alongside the password lockout and the
  TOTP factor tiers (§4.2.1.2 imports §3.2.2).
- **When mail transport arrives, the containment of the `dev` reset-link leak inverts for administrators.** Today, a
  reader of the `dev` log can reset an admin's password but cannot get the TOTP factor, so the leak is only partial
  for admins. The recovery code clears the factor. A log reader who obtains both a reset link and a recovery code
  holds option 2 in full, and the leak becomes total for admins. The recovery-address confirmation code travels the
  same channel. So both the recovery-code route and the recovery-address confirmation must be **structurally
  unavailable while the transport is the stub**, not merely unused. Their gate keys on a declared transport
  property, never on the `EmailService` bean type (ADR-067). T-CRED-023 asserts the routes are absent today.
- **Reopening triggers:** mail transport entering scope (this closes the conditional pass and resolves three
  notification SHALLs at once, and brings the inversion above); reversing the saved-code deferral in ADR-024
  (saved plus issued is then the branch to take, minted at 112 bits or more).
