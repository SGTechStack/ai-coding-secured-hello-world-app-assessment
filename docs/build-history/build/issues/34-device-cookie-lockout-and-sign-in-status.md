# 34: Device-cookie lockout lanes and the admin sign-in status

**What to build:** Two user-approved changes.

- **Part 1: OWASP device cookies against targeted lockout DoS.** After a correct password, the server issues an
  HMAC-signed device cookie (`HttpOnly`, `Secure`, `SameSite=Strict`), bound to the account and to a stored
  `trusted_devices` row (new Flyway migration), with a 30-day TTL. It is never set or cleared on a failure (ADR-033).
  A cookie presented with another username, tampered, expired or revoked is untrusted. Today's account lock becomes
  the **untrusted lane**; a valid cookie has its own **per-device lock** on the same ladder, persisted in the
  database. The NIST cap still counts every failure in every lane (cap 100 disables, ADR-013 unchanged). A
  trusted-device success resets the untrusted counter, its device counter and the rung. An untrusted-lane lock no
  longer ends the account's sessions; a device lock, the cap and the admin actions still do. Every device of the
  account is revoked on a password change, reset, admin-issued reset, admin disable, delete and runner credential
  reset. Uniform 401, identical headers, one `matches()` per request, the limiters, the cardinality axis, the TOTP
  lockout and the deferred-failure counting all stay. The secret `app.security.lockout.device.secret` follows
  ADR-062. Records: a new ADR, amendments to ADR-010/011/012/033/037, REJ-013 withdrawn, R-LCK-002/003/005 regraded,
  the Std:83 and PRD Story 2 AC3 deviation recorded, `docs/prd-coverage.md` updated.
- **Part 2: Admin "Sign-in status".** The admin list and detail responses carry a sign-in status (states and
  timestamps only): the untrusted-lane lock and its failure count, the locked trusted devices and the next unlock,
  the cap's password disable, the TOTP tier-1 lock and the tier-2 disable. The SPA shows a compact badge on the list
  and the full status on the detail page, separate from Enabled; Unlock is offered only when something is
  unlockable, and admin unlock clears the device locks too.

**Blocked by:** 11, 12, 13, 23, 33

**Status:** done

- [x] A correct password sets an HMAC-signed device cookie bound to the account and a `trusted_devices` row (new
      migration); no failure sets or clears it; tampered, expired, revoked or another username's cookie is untrusted.
- [x] The untrusted lane locks on the existing 5-in-window ladder; a trusted device's correct password passes an
      untrusted-lane lock; each trusted device has its own persisted lock on the same ladder.
- [x] The NIST cap counts every failure in every lane and disables every lane; a trusted-device success resets the
      untrusted counter, its device counter and the cap counter.
- [x] The 401 body and headers are identical whether or not the account exists or a cookie is present; one
      `matches()` per request; limiters, cardinality axis, TOTP lockout and deferred counting unchanged in intent.
- [x] An untrusted-lane lock does not end the account's sessions; a device lock and the cap still do.
- [x] Password change, reset, admin-issued reset, admin disable, delete and the recovery runner revoke every device.
- [x] Device lock state survives a restart.
- [x] `app.security.lockout.device.{secret,ttl,threshold}` bound and validated; the secret follows ADR-062 (refused as
      a published demo value outside dev).
- [x] The admin list and detail responses carry the sign-in status; no hash, secret or cookie value (T-ADM-008/016).
- [x] The SPA shows the sign-in status on the list and detail pages; Unlock appears only when something is
      unlockable, and unlock clears the device locks too.
- [x] Playwright: an attacker in a fresh browser context locks the account while the owner's browser still signs in.
- [x] Records: new ADR; ADR-010/011/012/033/037 amended; REJ-013 withdrawn; R-LCK-002/003/005 regraded; the new
      deviation recorded; `docs/prd-coverage.md`, register renderings and audit inventory regenerated.
- [x] One full `verify` passes. PIT skipped (user decision, deadline); `LockoutLane` and `DeviceCookieCodec` were added to the gate list.

## Closeout

- Decisions: the cookie is `__Host-DEVICE` with `Path=/` (the prefix forbids `/api/login`); `DEVICE` under `dev`.
  Admin unlock clears every lane, trusted devices included. Deferred (contended) failures count in the untrusted lane
  and on the cap. The dev key lives in `application-dev.yml` (T-CFG-020 exempts that file alone; T-CFG-039 refuses the
  value outside dev).
- Recorded residual: logout's mandated `Clear-Site-Data: "cookies"` clears the device cookie, so an explicit sign-out
  returns the browser to the untrusted lane (ADR-075; R-LCK-002). A timed-out session keeps it.
- PIT skipped (user decision, deadline).
