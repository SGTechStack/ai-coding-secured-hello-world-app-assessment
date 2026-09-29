# Password policy is length, not character classes

The PRD requires a password of at least 12 characters. It does not require a mix of cases, digits, or symbols.

## Decision

`PasswordPolicy` accepts a password from 12 to 128 characters. Registration and reset confirmation both use that check, and Bean Validation repeats it on the request body so a short password is rejected before an account or token is changed. The upper bound stops a caller from sending a huge string into BCrypt.

The seeded admin password in dev config has to pass the same length check or startup fails.

## Why not character-class rules

A class rule (one upper, one digit, one symbol) is easy to satisfy with a predictable pattern and is not in the acceptance criteria. Length is the rule the tests and the API both enforce. The frontend `minLength` hint matches it; the server is the check that counts.

History, expiry, and idle-account lock are not part of this policy. Failed-login lockout and the IP throttle are the brute-force controls.
