# ADR 0005 — Lawful basis and retention for the personal data this system holds

- **Status:** Accepted, pending confirmation of the applicable regime
- **Date:** 2026-09-24
- **Context:** privacy

## Decision

The system holds four items of personal data, each with a stated purpose, a
recorded basis and a retention period. The notice is configuration
(`app.privacy` in `application.yml`), served from `GET /api/privacy-notice`, and
shown at the point of collection on the registration form.

| Item | Purpose | Retention |
| --- | --- | --- |
| Username | Identifies the account at sign-in and in audit records | Until the account is erased |
| Email address | Deliver a password reset link the account holder asks for — nothing else | Until the account is erased |
| Password (BCrypt hash) | Authentication. The password itself is never stored or logged | Until the account is erased |
| Sign-in activity (failure counts, lockout times) | Detect and slow brute-force attacks | Until the account is erased |
| Password reset requests | Operate and investigate the reset flow | 7 days past expiry or use (`app.retention.reset-token-grace`) |
| Security and application logs | Detect and investigate attacks | 90 days (`logging.logback.rollingpolicy.max-history`) |
| Administrative audit records | Accountability for irreversible and privilege-changing actions | 1 year, pseudonymous references only |

**Lawful basis:** performance of a contract. The email address is required to
operate the account it belongs to. No marketing, no profiling, no third-party
disclosure.

**Rights implemented:** access, via `GET /api/account/export`; erasure, via
`DELETE /api/account`. Both act on the caller's own account only and require the
password to be re-entered for erasure.

## Why this is an ADR and not just configuration

The PRD declares no compliance regime, so the values above are drafted against
general PDPA/GDPR-style expectations. That makes them *input* to a compliance
review rather than its output, and the distinction matters enough to record: a
future reader finding populated retention periods could easily mistake a sensible
default for a decision somebody was accountable for. Nobody has signed off these
periods.

What is genuinely decided, and would not change under a different regime:

- Email is used for password reset and nothing else. Any other use is a new
  purpose needing its own basis.
- The password is never stored or logged in recoverable form.
- Logs and audit records identify accounts by a keyed pseudonym rather than by
  username, so the least-governed store stops accumulating a second permanent
  copy of who-is-who. See ADR 0006.
- Erasure is a real delete, not a flag.

## Consequences

- **Account retention is "until erased", which is not a period.** Automated
  deletion of dormant accounts is a policy decision with a real cost — deleting
  somebody's account because they were away — and is deliberately not implemented.
  Erasure is user- or admin-initiated. This is the weakest part of the position
  and is where a compliance review should start.
- **The masked email in the admin listing keeps the domain.** On a large public
  mail host that reveals almost nothing; on a small or single-tenant domain it can
  narrow an account to one organisation. Masking the domain too would leave the
  field with no disambiguating value, which is equivalent to removing it — and
  removing the only field that tells two similarly-named accounts apart, on a
  screen whose actions are irreversible, trades a privacy problem for a safety one.
- **The notice and the code can drift.** Two tests exist to stop that:
  `PrivacyNoticeTest` asserts the notice states a period for each category and
  names only rights that have endpoints behind them, and
  `ProductionProfileTest` asserts the log retention the notice claims matches the
  configured rotation. The notice is the half users read, so a mismatch is a false
  statement to a data subject rather than a documentation nit.
- Registration remains enumerable (ADR 0002), which interacts with holding email
  addresses: a list of addresses can be tested to learn which of those people use
  the system.

## Related

- Threat model TM-30, TM-31, TM-34, TM-35, TM-36, TM-37
- ADR 0006 (pseudonymous identifiers in logs)
- ADR 0002 (registration enumeration)
