# 06: Forced password change

**What to build:** A person given a Temporary Password signs in and is limited to choosing their own password, typed twice, before doing anything else. Until they do, every endpoint except change-password and sign-out is refused with a clear "password change required" reason. The server rejects mismatched or weak passwords. Once changed, the flag clears and the holder's other sessions end. An unchanged Temporary Password stops working after its expiry.

**Blocked by:** 05 Admin creates and lists Accounts

**Status:** ready-for-agent

- [x] While must-change-password is set, all endpoints except change-password and sign-out return 403 with a "password change required" problem detail
- [x] Change-password takes `newPassword` and `confirmPassword`; a mismatch is rejected server-side and changes nothing
- [x] Weak passwords (under 12 characters) are rejected
- [x] Success clears must-change-password and Temporary Password expiry, and ends the holder's other sessions
- [x] A Temporary Password past its expiry fails sign-in with the generic error
- [x] Audit line for password changed; no plaintext password logged or stored
- [x] SPA change-password page with two fields; sign-in routes to it when required; frontend tests cover it
- [x] Integration tests use the clock and cover: forced-403 elsewhere, mismatch, weak password, expiry, other sessions ended
- [x] Backend and frontend verify pass
