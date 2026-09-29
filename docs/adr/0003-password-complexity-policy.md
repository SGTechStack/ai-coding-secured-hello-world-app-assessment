# Password complexity: 3-of-4 character categories (IM8-aligned)

The initial spec set the password policy to length ≥ 12, max 72, with **no complexity requirement** (explicit decision recorded in the spec — "no complexity classes"). This was revisited as a security-hardening enhancement to align with Singapore IM8 password-policy principles.

## Decision

Extend the password policy to require characters from **at least 3 of the following 4 categories**:
- Uppercase letters (A–Z)
- Lowercase letters (a–z)
- Digits (0–9)
- Special characters (any non-alphanumeric character)

The length constraint (12–72) is unchanged. Both conditions must be satisfied.

## Rationale

A 12-character password composed entirely of lowercase letters has ~10^17 candidates (26^12) — adequate in isolation but reduced if an attacker knows the character set. Requiring 3-of-4 categories makes the effective search space substantially larger without imposing per-category presence rules (which users find difficult and which NIST SP 800-63B cautions against for interactive use cases).

3-of-4 rather than 4-of-4 avoids the UX trap of mandatory symbols in systems where users cannot easily type them on all keyboard layouts, while still closing the single-category / two-category weakness gap.

## Implementation

- `PasswordPolicy.java` — single source of truth for validation logic.
- `@ValidPassword` / `PasswordPolicyValidator` — Bean Validation constraint, applied to `RegistrationRequest.password` and `PasswordResetConfirmRequest.newPassword`.
- `AdminBootstrapRunner` — complexity check added for non-dev profiles.
- Frontend `src/utils/passwordPolicy.ts` mirrors the rules for UX feedback; backend remains authoritative.

## Consequences

- All new registrations and password resets must meet both constraints.
- Existing stored BCrypt hashes are unaffected (re-hash on next password change is infeasible and out of scope; policy applies to future changes only).
- All existing test passwords (e.g. `secure-pass-12`, `brand-new-pass-34`) already satisfy 3-of-4 (lower + special + digit), so no test data migration is required.

## IM8 controls deliberately NOT implemented

| Control | Decision |
|---|---|
| Password history (prevent last N reuse) | Requires `password_history` table + BCrypt comparison loop; disproportionate for assessment scope. Documented as a known gap. |
| Password expiration | No project-specific authoritative period; classified as a configurable deployment concern. Not implemented. |
| Inactive account locking | No PRD requirement; would need `last_login_at` + background job. Out of scope. |

Account lockout (Story 3/45) and IP throttling (Story 13) already satisfy the brute-force protection objective from IM8.
