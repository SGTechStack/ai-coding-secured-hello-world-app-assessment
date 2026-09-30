# Compromised passwords are checked against Have I Been Pwned

NIST SP 800-63B-4, which the standard follows, requires new passwords to be checked against a list of known-compromised ones, and the standard's recipes suggest the Have I Been Pwned k-anonymity API. We use Spring Security 7's `HaveIBeenPwnedRestApiPasswordChecker`. Only the first five hex characters of the password's SHA-1 leave the app. It applies wherever a password is set: registration, password-reset confirm, and Bootstrap admin creation. A rejected password gets `validation failed` with `password: must not be a password known from data breaches`.

Two consequences are deliberate:

- **An outage fails closed where a password is set.** Spring's checker treats a failed call as "not compromised", so we give it a client with a timeout (`app.password.compromised-check.timeout`, 3s) that raises an error instead. Setting a password during an outage gets 503 `service unavailable`, and no password is accepted unchecked.
- **Login is checked too, but an outage doesn't block it.** As a bean, the checker is also used by Spring Security at login, so a correct password that has since appeared in a breach is refused with the usual `invalid credentials`. The Regular user recovers through a password reset. During an outage, login skips the check: failing closed there would lock every Account out.

The API is an outbound dependency, so deployment environments must allow HTTPS to `api.pwnedpasswords.com`. Tests never call it: backend tests use an offline stand-in, and the wiring and the end-to-end test run against local stubs of the range API.

## Considered Options

- **A bundled list of the ~10,000 most common passwords, with no network calls.** This was the original decision, rejected on review. With the 12-character minimum only about ten of those passwords are even long enough to matter, so the list added almost nothing beyond the length rule.
- **Failing open during an outage (Spring's default).** Rejected: a weak password could then be set whenever the API was unreachable, without anyone noticing.
- **No check.** Rejected as a needless gap against the NIST rule.
