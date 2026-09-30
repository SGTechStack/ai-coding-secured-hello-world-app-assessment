# IP throttling targets password spraying, not lockout abuse

PRD Story 3 says the per-IP throttle stops an attacker from locking out a legitimate user by failing that user's password from one source. It can't: five consecutive failures against one account lock that account whatever the IP throttle does. We keep per-account lockout exactly as the standard defines it (5 consecutive failures, 20-minute self-expiring lock, stored on the account row so it survives restarts). Lockout abuse is limited by that automatic expiry, which is the standard's own reasoning.

The per-IP throttle is a separate layer against password spraying (one source trying many usernames). Next to it sits the standard's per-username rate limit (10 attempts/minute), keyed on the **submitted username string** so unknown usernames are throttled exactly like real ones and a 429 reveals nothing. Both limits return 429 with `Retry-After`. Rate-limit state is held in memory and is therefore correct only for a single instance.

The PRD is left unedited; this ADR is where the corrected reasoning lives.

## Considered Options

- Counting failures per (account, IP) pair was rejected: it contradicts the standard's rule that counters are per account, not per IP, and attackers using several IPs defeat it anyway.
- Setting the IP limit below the lockout threshold was rejected: it penalises shared NAT networks and still fails against distributed attackers.
