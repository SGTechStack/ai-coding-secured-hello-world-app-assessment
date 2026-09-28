---
status: accepted
---

# ADR-010: Dual rate limiting: per-account lockout and per-source throttling as independent limiters

The login path has two limiters that know nothing about each other. The per-account lockout counts failures on one
account, and the per-source throttle counts requests from one source key. Neither one reads, resets or refunds the
other. A maintainer could plausibly drop the per-source half, because the governing standard's own decision guidance
advises against IP-based limiting for internal applications behind NAT. That would remove the only control on one
source working across many accounts.

## Context

- PRD Story 3 asks for both. Its third criterion requires throttling from one IP address across many usernames,
  "independently of any single account's lockout state".
- The governing standard contradicts itself. §2 Decision Logic says failed-login counters and authentication rate
  limits apply per account, "not per IP address, to prevent brute-force bypass via IP rotation". The §2 sequence
  diagram, a few lines later, checks the rate limit "per IP". The standard's decision questions (Q16) then say not to
  implement IP-based limiting behind a corporate proxy, VPN, NAT gateway or shared cloud egress, because it "blocks
  entire office". No recipe in the corpus implements per-IP limiting.
- The two limiters defend different attacks. The account axis defends one account against many guesses. It cannot
  see one source trying one password across many accounts (password spraying), because each account records only a
  single failure. It also cannot see requests for usernames that have no account row at all. The source axis sees
  both, and it is the one that sits ahead of the expensive BCrypt verify.
- Q16's objection is real, and it has a price. A source budget tight enough to protect one account would lock out
  an office. So the source budget is sized for an office, not for protecting a single account. Account protection
  stays with the lockout.

## Decision

- **Both limiters, as one component invoked from separate call sites.** The per-source check runs in an early
  filter ahead of authentication, keyed on the source key (ADR-020). The per-account (submitted-username) check runs
  in the authentication converter, straight after the username is parsed and before `authenticate`. Placement follows
  from what each one needs. The source check needs nothing from the body. The username check needs the body, and the
  converter is the only place it is read, so this avoids buffering it.
- **Independence is structural, not a convention.**
  - A per-source refusal never reaches the provider, so no failure event fires and the lockout counter cannot move.
  - A per-account refusal is thrown in the converter, before `ProviderManager`, with the same effect.
  - Admin unlock clears the account's lockout fields and never touches a bucket. Otherwise an attacker could clear
    their own throttle by provoking an unlock.
  - A successful login refunds no source token. Otherwise an attacker behind shared NAT could ride their colleagues'
    successful logins.
- **Login budget on the source axis:** burst 60, then 1 per second, greedy refill. It is sized for a shared egress,
  and the account axis supplies per-account strength. The account axis keeps the standard's 10 a minute (burst 10,
  then 1 every 6 seconds).
- Two further per-source axes build on this one: the lockout-cardinality axis (ADR-015) and the session-miss budget
  (ADR-017).

## Consequences

- Q16's cost is accepted, not avoided. A shared egress shares one source budget. At 60 a burst and 1 a second, it
  takes an unusual office to reach that on logins alone, and the cardinality axis's NAT exposure is priced separately
  (R-RL-003).
- The uniform-401 failure handler carries a deliberate `429` branch for the account axis. A later "always 401"
  simplification would silently delete that limiter's only observable behaviour, so a comment marks the branch and
  T-RL-002 pins it.
- PRD Story 3's third criterion is met on its first clause (state independence) and not on its promised outcome.
  One source can still keep a known username locked (R-LCK-002, REJ-013).
- The account-axis 10-a-minute budget is unreachable by pure-failure traffic, because the lockout fires at 5. It is
  kept for its other jobs: probing usernames that have no account, abuse on mixed traffic, and write contention on one
  user row (R-STD-018).
- Every limiter is in memory in one instance (R-RL-005, R-RL-006).
- Tests: T-RL-001 (source refusal precedes authentication), T-RL-003 (account refusal leaves the counter unchanged),
  T-RL-002 and T-RL-010 (budget bindings).

## Sources

- PRD `prd/assessment-prd.md`, Story 3, third acceptance criterion.
- Standalone User Access Control Application Standard §2 Decision Logic (per-account counters and rate limiting), §2
  Sequence Diagram (per-IP rate-limit check), §3.5 Security Contract (Rate Limiting: 10 attempts per account per
  minute).
- Standalone User Access Control Application Standard Questions, Q16 ("What IP-based rate limiting strategy is
  needed?").
- NIST SP 800-63B-4 (July 2025) §3.2.2: the mandatory limit is per subscriber account and per authenticator, and IP
  address appears only among the optional risk-based techniques.
