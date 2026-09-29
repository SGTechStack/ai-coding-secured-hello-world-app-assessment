# 12: IP-level login throttling

**What to build:** Throttling that keys on the **source** rather than the target account, so an
attacker spraying many usernames from one address is slowed down even though no single account
crossed its lockout threshold. Crucially this must operate **independently** of account lockout,
which is what closes the abuse the PRD calls out explicitly: an attacker must not be able to
lock a legitimate user out of their own account merely by failing that user's password
repeatedly from one source.

Covers PRD Story 3, IP-throttling half. Independent of ticket 11 and can be worked in parallel
with it.

**Blocked by:** 08.

**Status:** ready-for-agent

**IM8 controls:** `as-4` Authentication Mechanism Rate-Limiting; `as-13` Exposure of Internal
System Details; `lm-4` Audit Logging; `pm-6` System Documentation. *ASVS: V2.2 General
Authenticator, V11 Business Logic, V13 API.*

- [ ] Failed login attempts are counted per source IP address across **all** usernames, not per
      account
- [ ] Exceeding the per-IP threshold throttles further attempts from that address
- [ ] The throttle decision is made **independently** of any account's lockout state: a
      throttled IP does not lock the accounts it targeted, and a locked account does not
      throttle the IP
- [ ] An attacker failing one victim's password repeatedly from one IP gets their own IP
      throttled without locking the victim out — this is the specific scenario the PRD names and
      it needs its own test
- [ ] Threshold, window and throttle duration come from configuration with documented values
- [ ] The client-facing response for a throttled attempt does not disclose the threshold or the
      remaining cooldown in a way that helps an attacker pace themselves
- [ ] The source address is resolved in a way that is documented and honest about proxy
      headers — if a forwarded-for header is trusted, the trust boundary is written down, since
      an untrusted forwarded header makes the throttle trivially bypassable
- [ ] Throttle counters are bounded so an attacker cannot exhaust memory by spraying from many
      addresses
- [ ] A throttling audit event is emitted recording the source
- [ ] Test: repeated failures across multiple usernames from one IP engage the throttle
- [ ] Test: the victim account in that scenario remains usable by its legitimate owner from a
      different source
- [ ] Test: an account lockout on one account does not throttle an unrelated IP
