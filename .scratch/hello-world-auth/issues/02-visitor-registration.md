# 02: Visitor registration

**What to build:** A visitor can register an account via a React form that posts to the backend; on success an account exists with role USER, enabled, and a BCrypt-hashed password. Duplicate or weak submissions are rejected with clear validation errors and no account is created.

**Blocked by:** 01 (security baseline & user persistence).

**Status:** ready-for-agent

- [ ] `users` table/entity exists: id, username (unique), email (unique), password_hash, role enum (USER/ADMIN), enabled, failed_login_attempts, locked_until (nullable), created_at.
- [ ] Registration with a unique username + unique email + password of length ≥ 12 creates a USER account, `enabled = true`, password stored as a BCrypt hash. (IM8 as-5, as-6)
- [ ] Duplicate username or email is rejected with a clear validation error (conflict); no account created.
- [ ] Password failing the strength policy is rejected with a validation error; no account created. (IM8 as-1)
- [ ] Plaintext password is never logged or stored anywhere. (IM8 lm-19)
- [ ] All inputs validated for type/format server-side; JPA parameterised queries used. (IM8 as-1, as-2)
- [ ] React registration form displays validation/conflict errors; user-controlled output is contextually encoded. (IM8 as-3 AUTO-FIX)
