---
status: accepted
---

# ADR-075: Device cookies split the password lockout into an untrusted lane and per-device lanes

A correct password earns the browser an HMAC-signed device cookie. A sign-in that presents a valid one for the
submitted username counts in that device's own lockout lane; every other sign-in counts in the account's
**untrusted lane**, which is the lock this application had before. An attacker who knows a username can still lock
the untrusted lane, but no longer the owner's trusted browser. A maintainer would plausibly remove this as an
unrequested addition, or "simplify" it back to one lock. Either brings back the targeted-lockout denial of service
that PRD Story 3's third criterion asks us to prevent.

## Context

- One account-wide lock that anyone can drive with five wrong passwords is a denial-of-service primitive against any
  known username (R-LCK-002). The per-source limiter cannot close it without locking out offices behind NAT
  (ADR-010), and the timed ladder only bounds it (ADR-011).
- OWASP's "Slow Down Online Guessing Attacks with Device Cookies" describes the standard remedy: after a successful
  authentication, issue a signed cookie bound to the user; lock out untrusted clients per user, and trusted clients
  per device. NIST SP 800-63B-4 §3.2.2 still requires the attempt limit per account and authenticator, so the NIST
  cap (ADR-013) must count every lane.
- ASVS 5.0 6.1.1 (L1) asks that the documentation show how the controls prevent malicious account lockout, and 6.3.1
  binds the implementation to it (R-LCK-003).
- The governing standard says a locked account cannot log in until the lock expires or an admin unlocks it (Std
  §2:83), and PRD Story 2 AC3 says a locked account refuses the correct password. A trusted device passing an
  untrusted-lane lock deviates from both (R-LCK-015).

## Decision

- **The cookie.** `__Host-DEVICE` (`DEVICE` under `dev`, whose plain HTTP cannot carry `Secure`, like the session
  cookie; ADR-058), `HttpOnly`, `Secure`, `SameSite=Strict`, host-only, `Max-Age` 30 days. Its value is a random
  device id (UUIDv4) and an HMAC-SHA256 over it under `app.security.lockout.device.secret`, a 32-byte key that follows
  ADR-062 (bound as a secret, validated, distinct from every other key, refused outside `dev` if it is the published
  dev value). `Path=/`, not `/api/login`: the `__Host-` prefix requires `/`, and the prefix's host-only integrity was
  preferred over a narrower path. The cookie authenticates nothing; a correct password is always required.
- **The binding.** Each cookie names a `trusted_devices` row (V10), which binds it to one account and holds the
  device lane's lock. A cookie is trusted for a sign-in only if its HMAC verifies, its row exists, has not expired
  and belongs to the account the submitted username resolves to. Tampered, forged, expired, revoked, or another
  account's: untrusted. The claim is read from the cookie alone in the login converter, before the username is
  looked up, so the lookup is the same whether or not the account exists (ADR-001; R-AUTH-004).
- **Two lanes, one ladder, one cap.** The untrusted lane is the account's existing windowed counter and lock, on the
  existing ladder (ADR-011; ADR-012). Each device has its own windowed counter, lock and failures-since-success on
  the same ladder, persisted in its row, so it survives a restart (Std §5:451; Std §5:574); its threshold is
  `app.security.lockout.device.threshold` (5). The NIST cap counter stays on the account and counts every failure in
  every lane; at 100 the password is disabled for every lane (ADR-013 unchanged).
- **The lock check** moves from the pre-authentication checks, which see only the account, to the password provider,
  which sees the token and its device claim. It still runs before the comparison, and a locked lane still costs
  exactly one `matches()` whose result is ignored (T-AUTH-003).
- **Success.** A trusted device's correct password resets its own counters, the untrusted lane's windowed counter and
  the cap counter (NIST §3.2.2 reset); an untrusted lock stays until it lifts, so the owner signing in does not hand
  the attacker a fresh count. A successful sign-in with no valid cookie for the account earns a new device, at most
  ten per account (the oldest go first). A failure never sets or clears the cookie (ADR-033).
- **Sessions (user decision).** An untrusted-lane lock ends no session: anyone can cause one, and it no longer blocks
  the owner. A device lock, the cap's disable and every admin action still end all sessions (ADR-037 amended).
- **Revocation (user decision).** Every device of the account is revoked on a password change, a reset redemption, an
  admin-issued reset, an admin disable, a delete (by cascade) and the recovery runner's credential resets.
- **Admin unlock** clears every lane's lock: the untrusted lane and each trusted device, with the factor's tier 1 as
  before, and never the cap or tier 2 (REJ-072). One audited unlock that left a device locked would send the
  administrator back for a second one.
- **Audit.** A device lock and its lift are rows 3 and 4 with their own reasons, `TRUSTED_DEVICE_THRESHOLD_REACHED`
  and `TRUSTED_DEVICE_AUTO_LIFT`. Revocation has no row of its own: it always accompanies a trigger that has one, as
  the session ends on those triggers do.
- **Admin sign-in status.** The admin user list and detail show each lock and disable, times and counts only, apart
  from Enabled; the SPA offers Unlock only when a lock an unlock clears is in force.

## Consequences

- PRD Story 3 AC3 is met for trusted browsers: an attacker can no longer lock the owner out of a browser they have
  signed in on. A browser that never signed in, or whose cookie was cleared, is untrusted and still lockable.
- **Sign-out keeps the device cookie** (amended below, ticket 35). Logout sends `Clear-Site-Data: "cache","storage"`
  without the `cookies` directive, and expires the session cookie explicitly, so a user who signs out stays trusted.
- **Residual (accepted).** The cap's disable is still reachable: an attacker who keeps the untrusted lane locked
  climbs the cap counter at the ladder's pace and disables the password in about 14 hours if the owner never signs in
  successfully in between (R-LCK-005). A trusted success resets the cap.
- A stolen device cookie gives its thief the device's own lane alongside the untrusted one, so failures toward the cap
  can arrive at up to twice the ladder's pace. A valid cookie needs the account's password at some point, and any
  credential change revokes it.
- A sign-in concurrent with a password reset can earn a device after the reset revoked the others; the same window
  exists for its session (R-SES-012).
- Deferred failures (ADR-011 amendment of 2026-09-30) lose their lane and count in the untrusted lane and on the cap.
- Tests: T-LCK-024 to T-LCK-035, T-AUTH-019, T-SES-017, T-SES-038, T-CRED-032, T-ADM-036, T-FE-029, T-E2E-006, with
  T-LCK-002, T-LCK-004 and T-LCK-005 amended.

## Amendment (2026-09-30, ticket 35): sign-out keeps the device; an administrator's device waits for the code

- **Sign-out (user decision; deliberate deviation from Std §3.5:391).** The standard mandates
  `Clear-Site-Data: "cache","cookies","storage"` on logout, but the `cookies` directive also clears the device cookie,
  so every explicit sign-out left the browser untrusted and lockable from one source (the residual this ADR first
  recorded). Logout now sends `"cache","storage"` only. The cookies the directive existed to clear are expired
  explicitly instead: the session cookie by Spring Session's serializer, a `Set-Cookie` with `Max-Age=0` under the
  session cookie's own name, path and attributes; there is no CSRF cookie, since the token lives in the session
  (ADR-036). A user who wants a browser forgotten changes the password, which revokes every device. SPA-origin cookies
  are no longer reached by the header either; the SPA clears its own state on sign-out (REJ-010; R-HDR-008).
- **Issuance waits for the whole sign-in (user decision).** An account whose sign-in needs the second factor (an
  administrator: both factors, ADR-023) earns its device cookie only when the TOTP code verifies (verification or
  enrolment confirmation, through the factor grant), after that success's audit row and on the same best-effort terms;
  a renewal from a browser already trusted for the account earns none. Its correct password alone neither issues nor
  evicts: otherwise a password-only attacker would earn a trusted device of their own and, with the ten-device cap
  and oldest-first eviction, could untrust every browser the administrator owns. An account that needs no second
  factor still earns it at the correct password.
- Tests: T-LCK-033 (sign-out keeps the device), T-LCK-034 (administrator trusted only at the code), T-LCK-035 (no
  eviction by password alone), T-E2E-007, with T-SES-007, T-HDR-005 and T-LCK-031 amended.

## Sources

- OWASP, "Slow Down Online Guessing Attacks with Device Cookies",
  https://owasp.org/www-community/Slow_Down_Online_Guessing_Attacks_with_Device_Cookies.
- NIST SP 800-63B-4 (July 2025) §3.2.2 (attempt limit per subscriber account; reset on success).
- OWASP ASVS 5.0: 6.1.1 (L1), 6.3.1 (L1), 6.3.8 (L3).
- Standalone User Access Control Application Standard §2:83, §3.5:391, §5:451, §5:574.
- draft-ietf-httpbis-rfc6265bis §5.7 (the `__Host-` prefix requires `Path=/`).
