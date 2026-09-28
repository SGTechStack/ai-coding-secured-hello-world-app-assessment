# 08: Password reset request (Story 6)

**What to build:** A user who forgot their password can request a reset via their registered email without leaking whether that email exists. The request endpoint always returns a generic success message regardless of whether the email is registered. When the email does match a registered user, a single-use reset token is generated, its hash (never the plaintext token) is stored with a short expiry (15–30 min), and the stubbed `EmailService.sendPasswordResetEmail(...)` is called (the stub logs the link instead of sending mail). A React "forgot password" form drives it.

**Blocked by:** 02, 04.

**Status:** done

- [x] Request endpoint returns a generic success message for any email, registered or not (enumeration-resistant)
- [x] Registered email → single-use token generated, token hash stored with a 15–30 min expiry
- [x] Stubbed `EmailService.sendPasswordResetEmail(...)` invoked and logs the reset link
- [x] Plaintext token never stored or logged
- [x] CSRF enforced on the endpoint
- [x] Audit log line for password reset requested
- [x] React request form submits and shows the generic confirmation
- [x] Integration tests: registered vs unregistered email return identical responses; token hash stored with expiry
