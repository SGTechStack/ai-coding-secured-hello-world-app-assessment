# 07: Admin resets a password

**What to build:** An admin helps someone who forgot their password by issuing a new Temporary Password for that Account, returned once. The Account's sessions end and its backoff state clears, so the person can sign in at once and go through the forced change. An admin cannot reset their own password this way.

**Blocked by:** 04 Progressive backoff and IP throttle, 06 Forced password change

**Status:** ready-for-agent

- [x] Reset returns a new Temporary Password once, stores only its hash, sets must-change-password and expiry
- [x] The Account's sessions are ended and failed-attempt counter and delay expiry are cleared
- [x] Targeting the admin's own Account is refused
- [x] 403 for non-admins, 401 when unauthenticated
- [x] Audit line for password reset with actor and target and no password
- [x] SPA reset action on the Account list; frontend tests cover it
- [x] Integration tests cover: reset flow into forced change, backoff cleared, sessions ended, self-reset refused
- [x] Admin OpenAPI spec and generated client regenerated
- [x] Backend and frontend verify pass
