# 06: Account lockout

**What to build:** Brute-force guessing against one Account is blunted. After 5 consecutive failed logins the Account is Locked for 20 minutes. The lock expires on its own, and the owner is notified. Every failure, whether a wrong password, an unknown username, a Locked Account or a Disabled Account, looks identical from outside, including how long it takes. This ticket adds the Login protection module's lockout half and the `EmailService` port with its logging stub.

**Blocked by:** 04 (Structured logging and the audit trail)

**Status:** ready-for-agent

- [ ] The Failed-login counter and `locked_until` are stored on the Account, so locks and counters survive a restart.
- [ ] 5 consecutive failures lock the Account for 20 minutes. The threshold and duration are configuration properties.
- [ ] With the controllable `Clock`, the Account is still Locked at 19 minutes, and at 20 minutes the correct password logs in and resets the counter to zero.
- [ ] A successful login resets the Failed-login counter.
- [ ] A wrong password, an unknown username, a Locked Account and a Disabled Account all return the same 401 `invalid credentials` body. A Disabled Account can be set up directly as a test fixture.
- [ ] A failed login for an unknown username runs a dummy password check, so it takes the same time as one for a real username.
- [ ] The `EmailService` port has `sendPasswordResetEmail`, `sendLockoutNotification` and `sendPasswordChangedNotification`. The stub logs that a message was sent, and tests can substitute a recording implementation.
- [ ] When an Account becomes Locked, its owner receives a lockout notification.
- [ ] A lockout is audited at WARN with `event.action=ATTEMPTS_EXCEEDED` and `error_code 423`, identified by UUID only.
