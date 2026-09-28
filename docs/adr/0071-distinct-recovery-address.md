---
status: accepted
---

# ADR-071: A distinct recovery address, not recovery contacts or saved codes

Break-glass recovery (ADR-070) combines one emailed recovery code with the account password. If the recovery
mailbox is protected by that same password, the two elements collapse into one secret. We mitigate this by
requiring the admin's **recovery address to be distinct from the login address**. We rejected recovery contacts and
deferred saved recovery codes. Recovery contacts are a NIST-listed alternative, and a maintainer would reach for them
first.

## Context

NIST SP 800-63B-4 §3.1.3.1 lists "access using only a password" among email's weaknesses. Under §4.2.2.2 option 2,
an issued code sent to a mailbox the attacker can open with the account's own password adds nothing. And this is the
admin account, which is the whole reason MFA is in scope (ADR-023).

The system has a second administrator by design, because two enrolled admins is an invariant (ADR-048). That admin
looks like a ready-made recovery contact.

## Decision

- **Option 2 plus a recovery address distinct from the login address.** This adds nothing beyond what the route
  already owes:
  - §4.2.1.2 requires that "CSPs SHALL allow the subscriber to establish at least two recovery addresses";
  - it also requires that "a recovery address SHALL be established only after the subscriber provides the correct
    confirmation code to the CSP".

  So the mitigation turns an obligation we already carry into the control.
- The residual is accepted and recorded, not hidden. The mitigation is weaker than two independent codes: it is one
  secret plus one mailbox, and nothing the application can check proves that the mailbox's own credential differs
  from the account password.

## Considered options

- **Recovery contacts (§4.2.1.3), with the second admin as the contact. Rejected.** §4.2.1.3 requires the CSP to
  "allow the subscriber to specify one or more addresses of trusted associates", and to "provide methods for
  subscribers to view and manage recovery contacts". Our second admin is designated by the system, nominated by
  nobody, and has no management surface. Naming that admin a recovery contact would satisfy the *shape* of the class
  without satisfying the class. It would also add a contact-list feature, and leave the mail-transport dependency
  unchanged.
- **Saved plus issued recovery codes (option 1). Deferred, not rejected on the merits.** It is the strongest option,
  and it is feasible: §4.2.1.1 puts no constraint on the channel, and scopes issuance to "at enrollment", which is
  exactly when the admin holds a password-plus-fresh-TOTP session. But it adds an authenticator lifecycle feature,
  which ADR-024 defers. If that deferral is ever reversed, this is the branch to take, and it supersedes this ADR.
  Saved codes minted at 112 bits or more also settle an ambiguity about how they must be stored: NIST does not say
  whether they count as look-up secrets, and at that length both storage rules are met.
- **Distinct recovery address (chosen).**

## Consequences

- Nothing is built yet. The recovery-address flow, and its confirmation code, exist only once mail transport exists,
  and they stay structurally unavailable while the transport is the stub (ADR-070; T-CRED-023).
- A single email column cannot hold a second recovery address. This is recorded in the deferral register as one
  residual with three separate address axes:
  - capacity for §4.6's two notification addresses;
  - no delivery outside `dev`;
  - §4.2.1.2's two recovery addresses.
- Recovery-code verification is throttled under §3.2.2 (ADR-070).
