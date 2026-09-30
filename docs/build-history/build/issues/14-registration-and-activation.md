# 14: Registration and activation

**What to build:** A visitor registers with a username and email address, then activates the account from the link and sets a password there (ADR-032).

- **Credential tokens** (ADR-007). 256 bits of Base64url, stored as domain-separated SHA-256, and consumed by a single conditional update. Activation tokens last 24 hours and work once. Redemption consumes first, then sets the password, in one transaction. Hashing and consumption are pure logic, in PIT scope.
- **Identifier canonicalisation** (ADR-045): NFC, trim, lowercase, with no dot or `+tag` folding.
  - A username that canonicalisation would change is rejected.
  - An email address is converted, and the whole address is lowercased.
  - Usernames can't contain `@` (REJ-027), and the reserved-name denylist applies (REJ-021).
- **`POST /api/register`** always returns the same 202 whether or not the email address is in use (R-CRED-018; T-AUTH-014). A taken username returns 400 `VALIDATION_FAILED` with rule `USERNAME_UNAVAILABLE`. A tombstoned username or email is treated as taken. A repeat registration before activation replaces the earlier *pending registration* (R-CRED-010).
- **`POST /api/register/activate`** takes the token and a password, runs it through `PasswordService`, and sets `activated_at`. A failed redemption returns 400 `RESET_TOKEN_INVALID`.
- **Rate limit:** add the register and activate source rows.
- **SPA:** the register and activate pages, with the strength meter and per-rule messages. In `dev` the activation link reaches the developer through the stub email logger.

**Blocked by:** 13

**Status:** done

- [x] Registering with a used and an unused email gives identical responses.
- [x] `Alice`, ` alice` and a non-NFC username are rejected rather than changed. `Bob@Example.COM` is stored lowercased.
- [x] A reserved name or a name containing `@` is rejected.
- [x] The activation token works once. After 24 hours on the `Clock` it gets `RESET_TOKEN_INVALID`.
- [x] A policy-violating password at activation gets `PASSWORD_REJECTED`, and no password-rejected audit row comes before a successful token check.
- [x] A pending account can't sign in (uniform 401).
- [x] A Playwright test covers register → activate → sign in.
