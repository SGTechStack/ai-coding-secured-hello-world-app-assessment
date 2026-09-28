---
status: accepted
---

# ADR-023: TOTP is required for administrators and offered to no one else

Administrators must enrol a TOTP authenticator before the admin surface opens to them. Regular users cannot enrol
one at all: there is no opt-in, `/settings/mfa` is reachable by administrators only, and the standard's advisory
`MFAPrompt` is not built. This decision can be undone from two directions. A maintainer holding the PRD, which lists
MFA as out of scope, would remove it. A maintainer holding `MFA_Frontend/Standalone`, which gives every user a
setup page and a first-login prompt, would extend it to everyone.

## Context

- **The PRD's Out of scope list names "Multi-factor authentication (MFA/2FA)".**
- **IM8 ac-2** requires MFA for privileged access and has no N/A branch. An admin module without a second factor is
  a failed control, so the PRD exclusion and ac-2 genuinely conflict. The conflict rule that governs this plan is
  that the PRD decides which features exist and the standards decide how a control behaves. A mandatory control
  wins over a feature exclusion here, because the exclusion would otherwise ship a known IM8 failure.
- **MFA_Frontend/Standalone** owns `/settings/mfa` for any user, and specifies `MFAPrompt`: "First-login advisory
  modal shown when the user has not completed MFA setup. Dismissible; does not block navigation or enforce setup."
- **MFA_Core §4.1** makes the active factor set "a deployment decision". TOTP-only, with no PIN factor, is
  sanctioned.
- NIST SP 800-63B-4 §3.1.1.2 sets a 15-character minimum for a password used as a single factor. That floor already
  binds every account (ADR-002), so an opt-in second factor would buy no usability for regular users.

## Decision

- **Scope:** TOTP applies to the `ADMIN` role only. The whole `/api/admin/**` surface requires it (ADR-021,
  ADR-026).
- **Enrolment is admin-only.** `/api/mfa/**` is role-guarded to `ADMIN` and sits outside `/api/admin/**`, so an
  unenrolled admin can reach it. It is off the forced-change allowlist, so a fresh deployment runs: seed, sign in,
  change password, enrol, factor granted, admin surface opens.
- **`MFAPrompt` is not built**, and `/settings/mfa` is not offered to regular users.
- **The challenge is eager.** After password sign-in the SPA reads the session's factor state from the profile
  endpoint and routes an admin straight to the challenge or to enrolment. The server-side gate is what enforces
  ac-2; the eager step is the client meeting it up front, and the lazy path remains for deep links.

## Considered options

- **No MFA, as the PRD says.** Rejected: a known IM8 ac-2 failure on the one surface it governs.
- **Opt-in TOTP for everyone, as `MFA_Frontend/Standalone` implies.** Rejected. An opt-in factor that no
  authorization rule ever demands secures nothing, and it still carries enrolment endpoints, a reset path and tests
  for every user in the system.
- **Required TOTP for everyone.** Rejected as scope. The PRD excludes MFA as a feature, and only the privileged
  surface has a binding control that overrides that.
- **Admin-only, required (chosen).**

## Consequences

- **A PRD deviation**, recorded against the Out of scope list (R-MFA-008).
- **An ASVS L2 claim for the whole application would be false.** ASVS 5.0 6.3.3 (L2) requires MFA, or a combination
  of single-factor mechanisms, to access the application. Regular users have one factor. The plan's target is
  therefore L1 with named L2 and L3 controls adopted where cheap, and this ADR is the cited reason (R-MFA-015).
- **Self-service enrolment is a reopening trigger for registration.** Account pre-hijacking can plant a second
  factor on a pending account and lock the real owner out (CVE-2026-56081 is this class). Registration is immune
  only because enrolment is admin-only and happens after activation. Adding self-service enrolment reopens ADR-032
  (R-CRED-011).
- **The first enrolment is password-only.** NIST SP 800-63B-4 §4.1.2.1 allows binding at the account's current
  maximum AAL when that is lower than the new authenticator's, and a fresh admin account has only AAL1. The
  resulting first-enroller race is a recorded residual (R-ADM-016, ADR-047).
- Tests: T-MFA-002 (every admin route refuses a session without the factor).

## Sources

- PRD (`prd/assessment-prd.md`), Out of scope: "Multi-factor authentication (MFA/2FA)".
- IM8 ac-2.
- `Appfw-Mfa-Standards/MFA_Frontend/Standalone` standard §1.2 Scope (`/settings/mfa`) and the component table
  (`MFAPrompt`); `Appfw-Mfa-Standards/MFA_Core` §4.1 Provider Dispatch.
- OWASP ASVS 5.0, 6.3.3 (L2).
- NIST SP 800-63B-4 §3.1.1.2, §4.1.2.1.
- CVE-2026-56081.
