# 03: Registration rules and password policy

**What to build:** A Visitor registering sees every password rule up front, gets an error next to each field that fails, and can't register a weak password or a username that differs from an existing one only in case. This ticket adds the Password policy, a single component that ticket 08 (reset confirm) and ticket 09 (Bootstrap admin) also use, and hardens registration and login matching. See ADR-0005 and ADR-0010.

**Blocked by:** 01 (Walking skeleton)

**Status:** ready-for-agent

- [ ] The Password policy rejects passwords shorter than 12 characters, longer than 72 bytes of UTF-8, or known from data breaches. The last is checked with Spring Security's `HaveIBeenPwnedRestApiPasswordChecker`, which fails closed with 503 `service unavailable` if it can't be reached in time (ADR-0010). Tests never call the network.
- [ ] The policy returns every rule that failed, and the `validation failed` response lists each one against the password field.
- [ ] Usernames must be 3–32 characters of `[a-zA-Z0-9._-]` and emails must be well-formed. Anything else gets `validation failed` with a per-field list.
- [ ] Oversized request bodies are rejected with a clear validation error.
- [ ] The username is stored as typed but compared case-insensitively: registering `Alice` when `alice` exists fails, and `Alice` and `alice` both log into the same Account.
- [ ] Emails are lowercased before they are stored and compared.
- [ ] A clash on username (in any case) or on email returns the same 400 `user exist`, which doesn't say which field clashed.
- [ ] The SPA register page shows every password rule before submission and shows each server validation error next to its field.
- [ ] Tests use made-up identities (for example `testuser1@test.example.com`).
