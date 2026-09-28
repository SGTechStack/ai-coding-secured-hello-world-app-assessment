---
status: accepted
---

# ADR-005: A zxcvbn score-3 strength gate replaces composition rules

User-chosen passwords face no character-class quotas. Instead they pass a length floor (ADR-002), a byte ceiling
(ADR-003), a breach blocklist, a context-term check, a strength gate at zxcvbn score 3, and a history check. The
recipes carry a composition regex, and a maintainer holding them would put it back. Another maintainer would drop the
strength gate because NIST does not ask for it. Both would be mistakes.

## Context

**The binding standard never imposed composition rules on user-chosen passwords.** This is a recipe deviation, not a
standard deviation:

- §3.5 Password Policy has four bullets. The four character classes appear only under "Administrative password reset
  must generate a 12-character random password", scoped to that path.
- §6's configuration list defines the term: "Password minimum strength requirements (minimum length)". Every other
  mention of password strength in the standard refers back to that configured minimum.
- The Questions file (Q12) offers "No mandatory character types (NIST recommendation)" and recommends against
  composition rules because they produce predictable patterns.

So the regex belongs to the admin recipe alone:
`(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\S+$).{12,}`. It also has three defects of its own:

1. The recipe's admin-create calls it on the admin's supplied string, so the quotas already leak onto a
   user-chosen path.
2. `(?=\S+$)` forbids whitespace, so no passphrase with a space can be set.
3. Its special-character set (`@#$%^&+=`) is narrower than §3.5's, so a password the standard allows is rejected.

**Composition rules are prohibited.** NIST SP 800-63B-4 §3.1.1.1 says other composition requirements "SHALL NOT be
imposed", and §3.1.1.2 repeats it for verifiers. ASVS 5.0 **6.2.5 (L1)** requires that passwords of any composition
be accepted. The rejection is compelled, not preferred.

## Quotas add nothing a strength gate misses

A filter adds security only if it rejects something bad the others accept. Scores below are measured with
`com.nulab-inc:zxcvbn:1.9.0`:

| Password | Four class quotas | zxcvbn score | Gate at 3 |
| --- | --- | --- | --- |
| `Password123!@#$` (15 chars) | pass | 2 | reject |
| `my neighbour keeps unusual bees` | fail (three classes missing) | 4 | accept |
| `aaaaaaaaaaaaaaaaaaaa` | fail | 0 | reject |
| `qwertyuiopasdfgh` | fail | 1 | reject |
| `passwordpassword` | fail | 0 | reject |

Quotas accept the predictable password and reject the strong passphrase. The blocklist and the gate measure
predictability directly instead of using character variety as a proxy. Adding quotas on top only adds false
rejections of passphrases.

## Considered options

- **Keep the recipe regex.** A NIST `SHALL NOT` and an ASVS L1 failure, with three defects of its own.
- **Blocklist only, with an advisory meter.** Meets NIST, but accepts `aaaaaaaaaaaaaaaaaaaa`, which clears any
  length floor and appears in few breach corpora. IM8 as-5 also checks that frontend and backend rules agree, and an
  advisory meter the backend ignores is exactly that mismatch.
- **Quotas as well as the gate.** Rejects nothing the gate misses (table above).
- **Score 4.** Pushes users to bolt on symbols to clear it, which brings composition back by another route.
- **Blocklist, context terms and a score-3 gate (chosen).**

## Decision

The single password-setting component runs these checks, in order, on every path that sets a password:

| Rule | Check |
| --- | --- |
| `MIN_LENGTH` | under 15 code points (ADR-002) |
| `MAX_BYTES` | over 72 UTF-8 bytes (ADR-003) |
| `BLOCKLISTED` | exact match against a version-pinned breach-corpus slice (a NIST `SHALL`) |
| `CONTEXT_TERM` | contains the username, the email local part, the service name, or a documented context word |
| `TOO_WEAK` | zxcvbn score below `app.security.password.min-strength-score: 3` |
| `HISTORY_REUSE` | matches a retained prior hash |

- The estimator is zxcvbn4j, pinned at 1.9.0. It is a direct port of the original algorithm, so it tracks the SPA's
  zxcvbn-ts meter. The backend is authoritative and the meter is indicative, because the two libraries' dictionaries
  differ.
- Username, email local part and service name are also passed to the estimator as user inputs.
- `CONTEXT_TERM` stays a separate rule. It is the one rejection a user can act on at once. The estimator's user
  inputs do not replace it: `SecuredHelloWorld2026!` scores 4, and still 3 with the service name as an input, so only
  the context rule stops it.
- The rejection returns `PASSWORD_REJECTED` with the `rule`. That, plus the meter, discharges NIST's `SHALL` to
  offer guidance and to give the reason for a blocklist rejection.
- **Admin-generated passwords do not exist** (ADR-006), so no path keeps quotas.

## Three misreadings, recorded so they are not re-derived

1. **NIST as a ceiling.** NIST is a floor. Exceeding it with a gate costs an explanation, nothing more.
2. **"The entire password SHALL be subject to comparison, not substrings"** constrains how the *blocklist*
   comparison works. Its purpose is to stop over-rejection (do not refuse `mypasswordishere` because it contains
   `password`). It does not forbid a separate strength control that matches patterns internally.
3. **Appendix A's "no additional requirements are imposed"** is NIST describing its own scope. It does not bind
   implementers.

And one inverse inference to refuse: a `SHALL NOT` is a prohibition, not a minimum. Going stricter elsewhere is no
licence to bring composition back.

## Consequences

- If a reviewer ever insists on quotas, the least harmful shape is length **or** complexity (four classes, or 20 or
  more characters), which still admits passphrases. Not built.
- A zxcvbn4j upgrade can shift scores. T-CRED-004 must pass again before the pin moves.
- The context word list and both list refreshes are a documented obligation (R-CRED-006; ASVS 6.1.2 and 6.2.11, both
  L2).
- Tests: T-CRED-004 (the pattern family at 15 characters or more is rejected with `TOO_WEAK`, legitimate passphrases
  are accepted, the threshold is read from configuration); T-CRED-005 (the password-setting component is the sole
  caller of `encode()`).

## Sources

- Standalone User Access Control Application Standard §3.5 Password Policy; §6 Operational Runbook, password policy
  configuration.
- Standalone User Access Control Application Standard Questions, Q12.
- Recipe: Standalone Privileged User Administration and Password Reset (`PasswordPolicy`).
- NIST SP 800-63B-4 §3.1.1.1 Password Authenticators; §3.1.1.2 Password Verifiers; Appendix A, Strength of
  Passwords.
- OWASP ASVS 5.0, V6.1 and V6.2: 6.1.2 (L2), 6.2.5 (L1), 6.2.11 (L2).
- zxcvbn4j (`com.nulab-inc:zxcvbn`) 1.9.0, Maven Central.
- IM8 as-5, via the `im8-review` control catalogue.
