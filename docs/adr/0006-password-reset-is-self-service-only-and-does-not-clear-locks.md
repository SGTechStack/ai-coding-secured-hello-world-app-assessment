# Password reset is self-service only and does not clear locks

The standard strictly requires resets to be issued by an admin, with the plaintext token returned to that admin once. The PRD specifies a self-service "forgot password" flow by email instead. Per ADR-0001 we build only the PRD's flow; the missing admin-issued path is a known deviation. It can be added later as one endpoint that reuses the same token service and confirm endpoint.

The standard's rules for reset tokens still apply: 32+ random bytes from `SecureRandom`, stored as a SHA-256 hash only, 30-minute expiry, issuing a new token invalidates any pending one, both endpoints are rate-limited, and the request endpoint does its work asynchronously so its response time doesn't reveal whether the email is registered.

A successful reset does **not** clear a lock or the failed-login counter. Otherwise the reset link would let anyone get around a lockout, and this matches the standard's rule for admin-issued resets. A locked owner waits for the lock to expire or asks an admin to unlock the account.
