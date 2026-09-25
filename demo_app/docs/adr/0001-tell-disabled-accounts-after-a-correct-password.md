# 0001: Tell a disabled account it is disabled, but only after a correct password

**Status:** accepted (2026-09-25)

## Context

The spec originally gave every refused login the same `401 INVALID_CREDENTIALS` ("Invalid username or password"): wrong password, unknown username, locked account and disabled account alike, so an attacker couldn't tell them apart. That left a disabled user with no idea why they couldn't log in, or that they should contact an admin.

## Decision

A disabled account with the **correct** password gets `401 ACCOUNT_DISABLED`, and the login page shows "Your account has been disabled. Please contact an admin." Everything else keeps the generic `401 INVALID_CREDENTIALS`: a wrong password (whatever the account's state), an unknown username, and a locked account.

This relies on the enabled check running **after** the password comparison (`SecurityConfig.authenticationManager`), so timing is unchanged and a guesser without the password learns nothing.

A session that an admin's disable ends still just lands on `/login` with no message; the user sees the disabled message on their next login attempt. Explaining it at the moment the session ends would need the server to remember why each session ended.

## Consequences

- Anyone who holds a disabled account's password (normally its owner, but possibly someone with a leaked password) can learn that the account exists and is disabled. We accept that trade-off for a clear message to the owner.
- Lockout is unaffected: a correct password for a locked account still gets the generic message. An account that is both locked and disabled gets the generic message until the lock expires, because Spring's status checker checks the lock first.
