# 31 — Decide IPv6 source keying for every per-source limiter

Type: grilling
Status: resolved
Blocked by: 09, 26, 29

## Question

Every per-source control on this map keys on the **full client address**. Under IPv6, one host with a single /64
controls about 2⁶⁴ addresses, so "source" costs nothing to rotate. **What is the source key under IPv6: the full
address, a fixed prefix, or a configurable prefix, and what does the choice cost the shared-egress cases ticket 09
was careful about?**

Graduated from [ticket 29](29-anonymous-session-row-growth.md) §10. The word "IPv6" appears nowhere on this map
before that ticket.

## Why this is its own ticket

It reopens ticket 09's keys for more than one resource:

- every per-source-IP row in ticket 09's budget table (`09:310-320`);
- the third axis, distinct accounts driven into lockout per source (`09` §R.6);
- ticket 26's session-miss budget (`26:194-202`);
- ticket 26's tier-1 keyed audit rows and row 46's distinct-source cap;
- `source.ip_hash` in ticket 13's audit schema (correlation across a prefix).

Ticket 29 bounded its own resource with a count cap precisely because `S` is free. The other controls have no such
fallback, and several of them (09 §R.6's "7× reduction, defeated by IP rotation") record rotation as an accepted
residual that was priced for IPv4 costs.

## What to decide

- **The key.** Full address, /64, /56, or a property. State which RFC or operational source justifies the prefix,
  and verify it under the map's verification rule.
- **Per-control or global.** Whether all limiters share one derived key, or some (e.g. the third axis) need a
  different granularity.
- **Shared egress.** The IPv4 analogue of a /64 is a NAT. Price what a /64 key costs a legitimate network with many
  hosts in one prefix, against ticket 09's NAT reasoning at login.
- **Where the key is derived.** Ticket 09's client-IP customizer already owns `socket` vs `proxy`. Decide whether
  prefix derivation lives there, so that no limiter can key differently by accident.
- **Audit consequence.** Whether `source.ip_hash` hashes the key or the address, given ticket 13's correlation use
  and ticket 24's HMAC key for that stream.

## Done when

The source key is stated as one property with its derivation site, every limiter and keyed audit row names which
key it uses, each residual that ticket 09, 26 or 29 recorded against IP rotation is re-priced or confirmed, and the
choice is verified against a primary source.

## Answer

**Every per-source control keys on one value, the source key: the IPv4 address as a /32, or the IPv6 address
masked to `app.security.client-ip.ipv6-prefix-length` (default 64, startup-validated to [48, 128]). It is derived
from parsed bytes, never from the address string, by one `SourceKeyResolver`, and `source.ip_hash` hashes the key,
not the address.** There is one granularity, not two: a second, coarser /56 key for ticket 09's third axis is
declined. Nothing on the map is newly bounded by the choice of key. What bounds the mass-lockout primitive *Consolidated into the register (ticket 33): R-RL-016. Amend the table by ID, not this list.*
regardless of source count is **ticket 09's escalating ladder**: about 14 h to a permanent disable, and about
9.7 h between the alert and permanence. That bound is now enforced at startup, because it is what every decline in
this ticket rests on.

Verification asset: [research/ipv6-source-keying-verification.md](../research/ipv6-source-keying-verification.md)
§§1–12, including the §6.1 AWS addendum and the §7.3 correction. Resolved over four grilling rounds with the user,
who corrected this resolver's drafts in every round.

**Premises of this ticket, or of its drafts, that were wrong:**

- **"One host with a /64" understates it, but not uniformly.** A /64 is one LAN, not one subscriber. RIPE-690
  recommends /56 residential and /48 business (§3). **But APNIC permits anything from /64 to /48 per end site**
  (APNIC-114 §10.1, research §11.1), so in this app's region a /64 may *be* the subscriber, and a /56 may hold 256 of
  them. Singapore ISP delegation sizes are **UNVERIFIED** for Singtel, StarHub, M1, MyRepublic and ViewQwest (§11.2). *Consolidated into the register (ticket 33): R-RL-021. Amend the table by ID, not this list.*
  Mobile bearers get one /64 each, which a reconnect typically replaces (§4); carrier reuse on reconnect was not
  established. **No single key makes rotation expensive for every attacker without overblocking someone.**
- **The derivation cannot live in ticket 09's client-IP customizer.** It is a `WebServerFactoryCustomizer` and runs
  once at startup. The property sits beside it; the per-request derivation does not.
- **"A bug today" was overstated, and is a constraint on the new parser instead.** `RemoteIpValve` passes the chosen
  `X-Forwarded-For` token verbatim (§7.3), but that token is written by our own trusted proxy, which spells a given
  client the same way every time, and the same string reaches both readers. So today's string keys do not split a
  client; they merely cannot be masked. DNS exposure appears only once something calls `getByName`, which nothing
  does today.
- **No IETF document recommends a fixed prefix for throttling.** RFC 9977 (Standards Track, May 2026) says every
  fixed prefix overblocks or underblocks someone and publishes per-range data instead (§8.4, §12). **APNIC Whois has
  no `prefixlen:` attribute** (§11.4), so that data is not a practical source here. /64 is operational practice
  (Cloudflare's previous rate limiting, Spamhaus CSS), not an RFC rule. NIST SP 800-63B-4 §3.2.2 mandates only the
  per-account limit; IP is a MAY (§9).
- **Ticket 09 §R.6's "7× reduction" is a units error, and it ran against the axis.** "36/hour to 5/hour" compares
  **disables** per hour with **distinct accounts driven into lockout** per hour. A disable needs 19 lockouts of one
  account across about 14 h, so one bucket carries about 5 chains at once: 5 ÷ 14 h ≈ **0.36 disables an hour per
  bucket, a 100× reduction, not 7×**. The per-bucket figure was undersold; the ceiling was oversold, because it
  assumed one bucket per attacker. §2.
- **Ticket 09's ladder figures carry a fencepost.** The 100th failure disables at once, so 19 locks precede it,
  not 20. §3.
- **Recovery route (c) in round 4, the user's own reset after the disable, does not exist in this build.** 09 §R.3
  specifies it, but outside `dev` "a reset request produces no deliverable artefact at all" (`09:1082`, `10:737`,
  `25:401`). It becomes real only when a mail transport lands. §6.

### 1. The source key

| Family | Key | Property | Validation |
|---|---|---|---|
| IPv4 (including IPv4-mapped IPv6, which Java returns as `Inet4Address`, §7.1) | the /32 | none; not configurable | — |
| IPv6 | the address masked to `/n` | `app.security.client-ip.ipv6-prefix-length`, default **64** | `@Validated @ConfigurationProperties`, refresh phase, `48 ≤ n ≤ 128`; startup log line states `n` |

**Text form, pinned because it is the HMAC input (§5):** `4:<8 lower-case hex>` or `6:<32 lower-case hex>/<n>`.
The family tag stops a v4 key colliding with a v6 one. Including `/n` means changing the property changes every
IPv6 hash, which is a correlation break like a key rotation and is recorded as such (§5).

**Why /64 and not the alternatives:**
- **/128** keeps today's behaviour. One host rotates freely inside its own /64 (RFC 7934 §8 tells networks not to
  stop it; research §2, §5).
- **/56** would match a RIPE-conformant home subscriber, but in APNIC it may lump up to 256 subscribers, and it
  puts one AWS VPC in one bucket (§6.1 addendum). Both are RFC 9977 §1's overblocking case.
- **RFC 9977 data** is not published in the APNIC region (§11.4).
- **/64** is the smallest unit an end network can freely fill (RFC 4291 §2.5.1 / RFC 7136; research §1). Many hosts
  in one office /64 are ticket 09's IPv4 NAT case, already priced, so nothing new is lost.

**What /64 buys, stated honestly:** it stops one host rotating. It does not stop one subscriber, one VPC or one /48
holder rotating. Under the previous /128 keying, one host supplied unlimited keys; now each key costs a LAN. *Consolidated into the register (ticket 33): R-RL-017. Amend the table by ID, not this list.*

### 2. Every per-source control names its key

| Control | Owner | Key |
|---|---|---|
| All eight source-IP rows of the budget table (`09:309`, `311–313`, `315–318`) | 09 §5 | source key |
| Third axis: distinct accounts driven into tier-1 lockout per source | 09 §R.6 | source key |
| Session-miss budget | 26 | source key |
| Tier-1 keyed audit rows (rows 5, 11, 13), keyed per (source, row, reason, window) | 26 / 13 | source key, carried as `source.ip_hash` |
| Row 46's distinct-source cap `N_src` and `source.distinct_count` | 26 / 13 | distinct `source.ip_hash` values, so distinct source keys |
| `source.ip_hash` on every row that carries it | 13 | HMAC of the source key |
| Per-submitted-username and per-submitted-identifier rows (`09:310`, `09:314`) | 09 | unaffected; not per-source |

**Why one granularity:** logs and limiters agree on what "a source" is. If the limiter keyed on /64 and the log hashed
/128, an attacker rotating inside one /64 would look like thousands of sources, hiding ticket 13's enumeration
signature (`13:374`) and hitting row 46's cap of 20 at once.

**The re-priced third axis, replacing 09 §R.6's honest-ceiling paragraph:**

- **Per bucket:** about 5 concurrent chains, so about **0.36 disables an hour** against the unlimited 36 an hour. That
  is roughly a 100× reduction, not 7×.
- **Where the ladder binds:** with `P = 100` accounts (ticket 29's planning figure), **P ÷ k = 20 source keys** run
  every chain in parallel, and every account is disabled at about 14 h. The user's independent count, treating `k` as
  a lockout rate, gives about 27 (1,900 lockouts ÷ (5 × 14 h)). Both land at "a few dozen".
- **The ceiling had already failed under IPv4.** A few dozen rented proxy addresses was cheap before this ticket. IPv6
  makes that count free for anyone holding more than one /64, and one VPC supplies 256.
- **So the binding limit is the ladder,** which does not depend on source count: about 14 h to a permanent disable,
  the alert at about 4.3 h, and about 9.7 h of warning.

**What a full set refuses (09 §R.6 reading (B), specified here):**

- A source whose set holds `k` members is refused on login attempts for **non-member** usernames. Attempts on existing
  members proceed.
- **An entry's expiry runs from when it was first added.** A re-lock of a member checks membership and **never
  writes**, so Caffeine's `expireAfterWrite` timer is not reset. Implemented as a membership test followed by
  `asMap().putIfAbsent` only on a first lock. Without this pin, an attacker re-locking its 5 members keeps the set full
  for the whole 14 h of each chain, and every non-member on that egress gets 429 throughout.
- **The trade, recorded instead of "never refuses a legitimate user":** under both readings a full set refuses every
  non-member on that egress, which on a shared NAT is almost everyone, so both readings cost a shared NAT the same.
  (B) spares only the members, who are mostly the attacked accounts, and it lets the attacker keep working its own 5
  chains. In exchange, the expiry pin bounds the refusal to about an hour after the fifth first-lock. (A) would stop
  the chains but gains nothing on price, since the ladder binds at about 20 keys under either reading. *Consolidated into the register (ticket 33): R-RL-003. Amend the table by ID, not this list.*

### 3. The ladder is the source-independent bound, and it is enforced at startup

**Corrected arithmetic** (09 §R.5, `09:1217`). Attempts during a lock raise `LockedException` and never advance the
cap (09 §R.4), so with threshold `t` = 5 and cap `C` = 100, failures 5, 10, …, 95 trigger 19 locks and the 100th
disables.

```
locks_to_cap   = floor((C − 1) / t)        = floor(99 / 5) = 19
locks_to_alert = floor((A − 1) / t)        = floor(49 / 5) =  9     (A = alert threshold, 50)
T_disable      = Σ rung(n), n = 1..19      = 5×20 + 5×40 + 9×60 = 840 min  ≈ 14 h
T_alert        = Σ rung(n), n = 1..9       = 5×20 + 4×40        = 260 min  ≈ 4.3 h
W_warning      = T_disable − T_alert       =                      580 min  ≈ 9.7 h
```

Ticket 09's 900 / 300 / "~10 hours" count a 20th lock that would begin after the disable. §R.2's "240 tracks × 6.7 h
and 540 tracks × 15 h" become **228 × 6.3 h and 504 × 14 h**. The throughput identity, 36 disables an hour from one *Consolidated into the register (ticket 33): R-LCK-005. Amend the table by ID, not this list.*
unlimited source, is unchanged. The flat-duration comparison of 3.3 h also stands, because both of its ends move by
20 min (380 − 180 = 200 min).

**Startup check, following ticket 11's refresh-validation pattern.** A `@Validated @ConfigurationProperties` check
derives `locks_to_cap` and `locks_to_alert` from **every input** (the lock threshold, the cap and the alert threshold)
and sums the configured rungs over them. It fails the context refresh if `T_disable < 840` or `W_warning < 580`.
Deployers may make the ladder slower but never faster. Checking the rungs alone would not be enough: raising the
threshold to 10 halves the lock count to 9 while every rung still passes. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-LCK-011. Amend the table by ID, not this list.* *Consolidated into the register (ticket 33): R-LCK-014. Amend the table by ID, not this list.*

**The inputs are ticket 09's constants.** Their property keys are still owed by 09 (`09:1219-1220`, and 16 test 8).
Per the mechanism/constants seam rule this ticket owns the check and names those keys as a **named owed input** in the
deferral register, never as a missing value.

**`k` is not an input to this bound**, but the 20-key figure in §2 is `P ÷ k`, so a change to `k` is a reopening
trigger (§10). *Consolidated into the register (ticket 33): R-RL-022. Amend the table by ID, not this list.*

### 4. Where the key is derived, and closing every raw path

- **`SourceKeyResolver`**, one bean, owns the IPv6 prefix property and the parser (§4.1). It has exactly two callers:
  1. **The early per-IP filter** resolves once and stores the key as a request attribute.
  2. **The Route C converter's details source** builds `SourceKeyAuthenticationDetails extends
     WebAuthenticationDetails`. It reads the request attribute the filter already set and calls the resolver only as a
     fallback, so there is one derivation per request. R.6's limiter and the audit field read the key from the
     details. This replaces `09:1295`'s "R.6 keys on the resolved raw address".
- **Raw-path ban, as an architecture test:** production code may not call `HttpServletRequest.getRemoteAddr()` or
  `WebAuthenticationDetails.getRemoteAddress()` anywhere except inside `SourceKeyResolver`. The framework's own
  details constructor still calls `super(request)`, so the raw string remains inside the object, which is why the
  accessor is banned too.
- **`toString()` is overridden** to omit the raw address, with a test asserting the rendered form contains no address.
  An `Authentication` rendered into a log line otherwise carries its details. Whether 7.1.1's base `toString()`
  includes the address was **not verified at source**. The test settles it either way, so it is recorded as owed, not
  as fact. *Consolidated into the register (ticket 33): R-AUD-036. Amend the table by ID, not this list.*
- **The rule covers our code only.** Tomcat's access log, any `hasIpAddress` matcher, and the edge still see raw
  addresses. The edge's copy is the one the runbook needs (§6).
- **Serialisation:** the subclass is stored inside the `Authentication` in `SPRING_SESSION`, so it declares a
  `serialVersionUID`. It adds one short string, about 40 characters plus Java serialisation framing, to each
  authenticated session row, which is negligible against ticket 29's `b = 17 KB` (unmeasured, and anonymous rows carry
  no `Authentication` at all). *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-040. Amend the table by ID, not this list.*

#### 4.1 Parsing

- **Shape check, then bytes, never DNS, and no new dependency.** A strict dotted-quad match or the presence of `:`
  must hold, or the token is unparseable. Only then is `InetAddress.getByName` called, followed by byte masking
  (research §10.3). `InetAddress.ofLiteral` would be cleaner but is Java 22+ and the map is on 21 (§10.2). **The
  research snippet was never compiled.**
- **The shape check leans on unpromised JDK behaviour**: a `:`-containing non-literal throws rather than resolving
  (jdk-21+35 source, not the Javadoc). So a test installs a failing `InetAddressResolverProvider` (Java 18+) and runs
  the resolver over hostnames, `bad.cafe`, `untrusted-proxy`, bracketed forms and zone-suffixed forms. A JDK upgrade
  that brings DNS back then fails the build rather than production.
- **Normalisation is a consequence of keying on bytes:** IPv4-mapped addresses collapse to IPv4 (§7.1), zone IDs are
  dropped by `getAddress()`, and case and compression cannot split a key. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RL-025. Amend the table by ID, not this list.*

#### 4.2 An unparseable token

- **It goes to one shared bucket, keyed `unparseable`.**
  - A 400 (a) is rejected because the client did nothing wrong. It would blame them for our proxy's misconfiguration
    and misuse `VALIDATION_FAILED`, which ticket 06 reserves for payload validation.
  - Falling back to the socket peer (c) is rejected because it silently puts everyone into the proxy's single bucket.
  - **The shared bucket (b) is not a cure for total misconfiguration.** If every token is bad, the shared bucket *is*
    the whole-internet bucket of `09:433`. It only helps when some tokens are bad, so detection matters.
- **Detection:**
  - The `app.client_ip.unparseable` counter is incremented every time.
  - In `proxy` mode, a WARN is written once per window.
  - In `socket` mode an unparseable token is impossible (§7.2), so it is logged at **ERROR**.
  - The raw token is attacker-influenced text, so it is **escaped** (non-printables as `\uXXXX`) and **truncated to 64
    characters** before logging. It is never hashed into `source.ip_hash`, which carries the constant key `unparseable`. *Consolidated into the register (ticket 33): R-RL-020. Amend the table by ID, not this list.*

### 5. `source.ip_hash` hashes the key

- **Input, pinned by a test:** `HMAC-SHA-256(app.security.hmac.log.key, "ip:" || source_key)`, where `source_key` is
  §1's text form. This amends ticket 13's input and ticket 24's inventory row (`24:715`).
- **IPv4 hashes change** from the dotted-quad input to `ip:4:<hex>`, so no earlier hash matches. That costs nothing
  before first deploy, and is recorded so nobody mistakes it for a rotation.
- **Changing `ipv6-prefix-length` breaks IPv6 correlation across the change**, exactly like a log-key rotation, and
  the only marker is the startup log line (compare `25:236`).
- **Forensic cost, recorded as a residual:** raw addresses are never logged (ticket 13), so after this change no log
  of ours can distinguish hosts inside one /64. IPv4 NAT never allowed it either. *Consolidated into the register (ticket 33): R-AUD-035. Amend the table by ID, not this list.*
- **Brute-forcing a leaked key:** no credit is claimed. The allocated /64 space is far smaller than 2⁶⁴.
- **The exact join this enables is §6's operator route (a).** An operator who holds the log key can mask edge-log
  addresses to the same source key, hash them in the same input format, and match `source.ip_hash` exactly. Joining by
  timestamp alone is guesswork. **`trace.id` is not a join key**, because ticket 27 regenerates it at our boundary and
  the edge never sees ours. *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-AUD-041. Amend the table by ID, not this list.*

### 6. What the operator does with the 9.7 hours

The alert at the 50th consecutive failure names an account. Three routes, in order:

- **(a) Edge block on the attacking prefixes.** Take the alert's `source.ip_hash` values, find the matching raw
  addresses in the edge's access log by the exact hash join (§5), and block them at the edge at a prefix the operator
  chooses.
  - **Who holds the log key:** ticket 24 makes it a deployer-supplied mounted secret, so the deployer holds it. Using
    it outside the running application widens its exposure, so the join is a controlled one-off, not a standing
    script.
  - If the operator does not hold the key, the fallback is a timestamp join against the edge log. That fallback is
    recorded as the reason the key's holder is named here.
- **(b) The account owner signs in successfully between locks.** Reset-on-success sets the cap counter to zero and
  restarts the 14 h clock. This is **unreliable**: the attacker re-locks within seconds of each lift. It doesn't end
  the attack either.
- **(c) The owner's own reset after the disable.** 09 §R.3 clears a disable on reset-token redemption, but outside
  `dev` the reset request produces no deliverable artefact (`09:1082`). So **(c) exists only once a mail transport
  lands**, and even then it restarts the attacker's clock rather than ending the attack.

**So in this build, outside `dev`, every disable needs deploy-level access** (the `--rebind` runner, `09:1143`). The
warning window is the only time to act without it. The user's round-4 narrowing, "deploy access applies only when the
reset channel is unavailable", is correct in principle, and in this build that condition always holds. It becomes
false when the transport fog item resolves, which is a reopening trigger (§10). *Consolidated into the register (ticket 33): R-CRED-026, R-LCK-012. Amend the table by ID, not this list.*

**Declined:** logging a raw or masked prefix in our own alert row. It would bring back the address ticket 13 withdrew
(`13:132`), and the edge log already has it.

### 7. Shared egress, re-priced

- **IPv6 /64** is one LAN: an office floor, a campus VLAN, or a tethering phone with about 8 clients (RFC 7934 §7).
  That is ticket 09's NAT case, and all of 09's budget sizing, 26's 50-staff × 3-tab miss budget, and 29's 480/510
  per-source figures carry over **per source key, unchanged**.
- **IPv4** is unchanged, including CGNAT (RFC 6269 §13.1, the primary-source statement of the penalty-box failure
  09 priced at login).
- **The origin-reachability case is decided by the public endpoint, not the origin:**
  - If the public hostname publishes **AAAA**, IPv6 clients arrive natively. In socket mode they reach us directly; in
    proxy mode a dual-stack edge forwards native IPv6 addresses in `X-Forwarded-For` even to an IPv4-only origin.
    Either way the /64 property applies in full.
  - If the public hostname publishes **no AAAA**, IPv6-only clients arrive through NAT64 as shared IPv4 (research
    §6.2). That is the CGNAT case, and the IPv6 property has no effect.
  - Ticket 20 never mentions IPv6, and no IM8 or IMDA requirement for IPv6 reachability was found (§11.5). So this
    ticket mandates neither case; the deployer declares which applies (handover item 1). *Consolidated into the register (ticket 33): R-OPS-008. Amend the table by ID, not this list.*

### 8. Residuals, re-priced or confirmed

| Residual | Where recorded | Now |
|---|---|---|
| Per-IP rows: "IP rotation restores throughput" | 09 §5 | **Confirmed, re-worded**: free for anyone holding more than one /64. No free rotation *within* the key, but mobile reconnects rotate it (§4). *Consolidated into the register (ticket 33): R-RL-005. Amend the table by ID, not this list.* |
| Third axis: "7× reduction, defeated by IP rotation" | 09 §R.6, `09:1406`, `15:73`, `25:422`, `map.md:346`, `threat-model/report.md:92` | **Re-priced**: about 100× per bucket; the ladder binds from about 20 source keys; IPv6 makes that count free for anyone holding more than one /64. The ceiling had already failed under IPv4. |
| Mass primitive's residual | 09 register, `09:1406` | **Re-priced**: 36/hour from one unlimited source; about 0.36/hour per limited bucket; bounded below by 14 h per account regardless of sources. |
| Miss budget: "IP rotation defeats the miss budget" | 26 register entry 4 | **Confirmed, re-unitised** to per source key. The source-independent emitter bound remains the compensating control. |
| `N_max` login denial at about 196 sources | 29 §6, register entry 2 | **Improved, not worsened**: under /128 keying one host supplied 196 keys for free; at /64 it takes 196 LANs. The cheapest supply is **one AWS VPC** (256 /64s for creating subnets, research §6.1 addendum), in any region including `ap-southeast-1`; a RIPE-conformant home /56 is the other. *Consolidated into the register (ticket 33): R-RL-011. Amend the table by ID, not this list.* |
| 480 / 510 anonymous rows | 29 §7 | **Confirmed**, per source key. *Consolidated into the register (ticket 33): R-LCK-005. Amend the table by ID, not this list.* |

**New residuals, each small, recorded so they are not rediscovered:**
- A /64 stops one host rotating, not one subscriber, VPC or /48 holder. *Consolidated into the register (ticket 33): R-RL-017. Amend the table by ID, not this list.*
- **Teredo (`2001::/32`)** puts every client of one Teredo server in one key, and **6to4 (`2002::/16`)** is finer
  than the site. Both are deprecated transition mechanisms. The Teredo layout (RFC 4380) was **not re-read**, so it is
  UNVERIFIED (research §10.3). *Consolidated into the register (ticket 33): R-RL-018. Amend the table by ID, not this list.*
- **A dual-stack host gets two budgets**, one per address family. *Consolidated into the register (ticket 33): R-RL-019. Amend the table by ID, not this list.*
- No host-level forensics inside one /64 (§5). *Consolidated into the register (ticket 33): R-AUD-035. Amend the table by ID, not this list.*
- The `unparseable` shared bucket becomes the whole-internet bucket under total proxy misconfiguration (§4.2). *Consolidated into the register (ticket 33): R-RL-020. Amend the table by ID, not this list.*
- Singapore ISP prefix sizes are UNVERIFIED (§11.2). *Consolidated into the register (ticket 33): R-RL-021. Amend the table by ID, not this list.*

### 9. Tests owed to ticket 16

1. **Replaces 16 test 3's second assertion (`16:151`, "two addresses, two keys").** Two different /64s give two keys;
   two addresses in the same /64 give one key; `::ffff:a.b.c.d` gives the same key as `a.b.c.d`; compressed,
   uncompressed and upper-case spellings give one key.
2. `ipv6-prefix-length` binding: 64 is the default; 47 and 129 fail the refresh; 48 and 128 start.
3. **HMAC input format pinned**: a fixed key and fixed addresses produce fixed `source.ip_hash` values for `ip:4:…`
   and `ip:6:…/64`. The test comment records that IPv4 hashes changed from the dotted-quad input.
4. **No DNS, ever**: with a failing `InetAddressResolverProvider` installed, the resolver maps hostnames, `bad.cafe`,
   `untrusted-proxy`, bracketed and zone-suffixed inputs to `unparseable` (or strips the zone) and never calls the
   provider.
5. **Raw-path ban**: an architecture test fails on any production call to `getRemoteAddr()` or
   `WebAuthenticationDetails.getRemoteAddress()` outside `SourceKeyResolver`.
6. `SourceKeyAuthenticationDetails` declares a `serialVersionUID`, round-trips through JDBC session serialisation, and
   its `toString()` contains no address.
7. **One derivation per request**: with the filter's request attribute set, the details source never calls the
   resolver.
8. **Unparseable handling**: the counter increments; there is one WARN per window in proxy mode and an ERROR in socket
   mode; the logged token is escaped and at most 64 characters; the bucket key is `unparseable`.
9. **R.6 reading (B)**: re-locking a member does not count toward `k`; a non-member is refused when the set is full.
10. **R.6 expiry pin**: re-lock a member at t = 50 min, and the entry still expires at t = 60 min.
11. **Ladder floor**: defaults pass (840 / 580); one rung lowered by a minute fails; rungs raised pass; lock threshold
    raised to 6 with rungs unchanged fails (16 locks, 660 min); alert moved to 60 fails (warning 480 min).
12. The startup log line states the IPv6 prefix length (the evidence handover item 1 relies on). *Superseded by the [test-plan table](../../../docs/test-plan/test-plan.md) (ticket 32): T-RL-026, T-RL-027, T-AUD-041, T-RL-025, T-RL-028, T-AUD-040, T-RL-029, T-RL-020, T-RL-030, T-RL-031, T-LCK-011, T-CFG-037. Amend the table by ID, not this list.*

### Handover items (ticket 25)

- **Declare whether the public hostname publishes AAAA.**
  - **Obligation:** state whether IPv6 clients reach the public endpoint natively, since that decides whether the
    /64 source key does anything (§7). Re-declare when the edge or DNS changes.
  - **Discharges:** ASVS **15.3.4 (L2)** (the real client IP is what the limiters use), adopted where cheap against
    the L1 target.
  - **Application enforcement:** none over DNS. The application logs its prefix length at startup.
  - **Fields:** `responsibility: deployer` · `status: documented` · `priority: required`.
  - **Acceptance check:** an `AAAA` lookup on the public name, recorded beside the startup log line showing
    `ipv6-prefix-length`. *Consolidated into the register (ticket 33): R-OPS-008. Amend the table by ID, not this list.*
- **Aggregate IPv6 at a chosen prefix in the edge per-source limit.** This tightens tickets 26 and 29's
  infrastructure-layer limit item (`26:546`, `29:372`); it does not add a second limit. *Consolidated into the register (ticket 33): R-RL-010. Amend the table by ID, not this list.*
  - **Obligation:** the edge limit must key IPv6 on a prefix the deployer chooses for their user population, not on
    /128. The application's /64 is fixed for correctness; the edge is where knowledge of the population sits, which is
    RFC 9977's point. Envoy's `MaskedRemoteAddress` defaults to /128, so the default is the wrong answer (research §8).
  - **Discharges:** ASVS **2.4.1 (L2)** (anti-automation) and **15.3.4 (L2)**.
  - **Application enforcement:** none; it is the edge's configuration.
  - **Fields:** `responsibility: deployer` · `status: documented` · `priority: required`.
  - **Acceptance check:** the edge configuration shows an explicit IPv6 mask, and a test from two addresses in one
    prefix hits one bucket. *Consolidated into the register (ticket 33): R-OPS-006. Amend the table by ID, not this list.*
- **Keep edge access logs with raw client addresses: at least 9.7 h, at most a bounded retention.**
  - **Obligation:** retain raw client addresses at the edge for at least `W_warning` (580 min), or §6 route (a) cannot
    be done. Retain them for **no longer than the deployer's retention policy**, with **30 days as a planning value**.
    Ticket 13 withdrew raw IPs from our logs, and this item moves that personal data to the deployer, so its retention
    must be someone's job.
  - **Discharges:** IM8 **lm-16** Key Signals Monitoring (MUST, LR 2 | MR 2; `01:193`), for the response half; and
    the PDPA retention limitation obligation (statutory, no level scheme).
  - **Application enforcement:** none.
  - **Fields:** `responsibility: deployer` · `status: documented` · `priority: required`.
  - **Acceptance check:** the edge's log retention setting, recorded with both bounds and the retention policy that
    set the maximum. *Consolidated into the register (ticket 33): R-OPS-009. Amend the table by ID, not this list.*
- **Runbook for the lockout-cap alert's warning window.**
  - **Obligation:** on the alert at 50 consecutive failures, act within about 9.7 h using §6's routes in order: (a)
    edge block, found by the exact hash join where the operator holds `app.security.hmac.log.key` and by timestamp
    otherwise; (b) owner sign-in between locks, marked unreliable; (c) owner reset, **not available outside `dev` until
    a mail transport exists**. Name who holds the log key. State that in this build every disable needs the `--rebind`
    runner.
  - **Discharges:** ASVS **6.1.1 (L1)** (documented anti-automation and lockout behaviour), and IM8 **lm-16**'s
    alerting half.
  - **Application enforcement:** partial. The alert, the ladder floor (§3) and the pinned HMAC input are enforced; the
    response is not.
  - **Fields:** `responsibility: shared` · `status: asserted-by-test` (the ladder floor and HMAC format, §9 tests 3
    and 11) · `priority: required`.
  - **Acceptance check:** the runbook names the three routes, the key holder, and the `--rebind` runner. A dry run
    matches one known edge-log address to its `source.ip_hash`. *Consolidated into the register (ticket 33): R-LCK-012. Amend the table by ID, not this list.*
- **Keep the trusted proxy writing IP literals in `X-Forwarded-For`.**
  - **Obligation:** in `proxy` mode, the trusted proxy must write IP literals, not hostnames or obfuscated tokens.
    Alert on `app.client_ip.unparseable` above zero.
  - **Discharges:** ASVS **15.3.4 (L2)**.
  - **Application enforcement:** detection only (§4.2); the shared bucket fails closed.
  - **Fields:** `responsibility: deployer` · `status: asserted-by-test` (§9 test 8) · `priority: required`.
  - **Acceptance check:** the counter reads zero after a representative traffic sample through the edge. *Consolidated into the register (ticket 33): R-OPS-010. Amend the table by ID, not this list.*

### 10. Graduated: none

Nothing new is specifiable. The mail-transport fog item already on the map carries the §6 route (c) consequence.

### 11. ADRs, glossary, register

**Two new ADRs:**
1. **The /64 source key via one property.** Covers the RIPE / APNIC / VPC trade, why not /128, /56 or RFC 9977 data,
   bytes-not-strings, and the one-resolver rule with its raw-path ban. *Consolidated into the ADR routing (ticket 34): ADR-020. Amend by ID, not this list.*
2. **`source.ip_hash` hashes the source key.** Covers the pinned input format, the IPv4 hash change, the correlation
   break on a prefix change, and the exact-join use. *Consolidated into the ADR routing (ticket 34): ADR-054. Amend by ID, not this list.*

**Three amended ADRs:**
- Ticket 09 §R.6: the honest ceiling is re-priced (units error, about 100× per bucket, the ladder binds from about 20
  keys), and reading (B) is specified with the expiry pin and its trade. *Consolidated into the ADR routing (ticket 34): ADR-015 (attached amendment). Amend by ID, not this list.*
- Ticket 09 §R.5: the ladder, with the fencepost corrected (840 / 260 / 580) and the startup floor. *Consolidated into the ADR routing (ticket 34): ADR-011 (attached amendment). Amend by ID, not this list.*
- Ticket 13: the `source.ip_hash` input. *Consolidated into the ADR routing (ticket 34): ADR-054 (attached amendment). Amend by ID, not this list.*

**One glossary term:**
- **Source key:** the family-tagged, prefix-masked value every per-source control treats as "a source". It is an IPv4
  /32 or an IPv6 address masked to the configured prefix. It is not an address, and it identifies a network, not a
  host.

**Register entries:**
1. **Second /56 "network key" for the third axis: declined.** It would buy 400 h instead of 14 h against a
   single-/56 attacker, but only where /56 is the subscriber, and it puts one VPC in one bucket. It does nothing
   against a /48 or several VPCs. It would cost two properties, a second data structure, a new targeted DoS (an
   attacker locking 5 self-registered accounts 429s up to 256 unrelated neighbours in its /56), and an audit gap
   between the key and the hash. *Consolidated into the register (ticket 33): R-RL-016. Amend the table by ID, not this list.*
2. **Global lockout-rate cap (`N_max`-style): declined.** It would let an attacker switch off a NIST `SHALL` or deny
   login for everyone, to defend against what the ladder already bounds. *Consolidated into the register (ticket 33): R-LCK-013. Amend the table by ID, not this list.*
3. **Ladder constants as a named owed input** from ticket 09 (property keys at `09:1219-1220`), consumed by §3's
   floor check.
4. A /64 stops a host, not a subscriber, VPC or /48 holder. *Consolidated into the register (ticket 33): R-RL-017. Amend the table by ID, not this list.*
5. Teredo and 6to4 granularity (Teredo layout UNVERIFIED). *Consolidated into the register (ticket 33): R-RL-018. Amend the table by ID, not this list.*
6. Dual-stack hosts get two budgets. *Consolidated into the register (ticket 33): R-RL-019. Amend the table by ID, not this list.*
7. No host-level forensics inside one /64. *Consolidated into the register (ticket 33): R-AUD-035. Amend the table by ID, not this list.*
8. The `unparseable` bucket under total proxy misconfiguration. *Consolidated into the register (ticket 33): R-RL-020. Amend the table by ID, not this list.*
9. Singapore ISP delegation sizes UNVERIFIED. *Consolidated into the register (ticket 33): R-RL-021. Amend the table by ID, not this list.*
10. R.6 reading (B): a full set refuses non-members on that egress for about an hour after the fifth first-lock. *Consolidated into the register (ticket 33): R-RL-003. Amend the table by ID, not this list.*
11. `SourceKeyAuthenticationDetails.toString()` behaviour in 7.1.1 is unverified at source and settled by test. *Consolidated into the register (ticket 33): R-AUD-036. Amend the table by ID, not this list.*

**Reopening triggers for this ticket:**
- Any change to ticket 09's ladder constants, lock threshold, cap or alert threshold that the startup floor would
  reject. The floor refuses it, so the change can only happen by reopening this ticket. *Consolidated into the register (ticket 33): R-LCK-014. Amend the table by ID, not this list.*
- Any change to `k`, or `P` growing well past 100, since the 20-key figure is `P ÷ k`. *Consolidated into the register (ticket 33): R-RL-022. Amend the table by ID, not this list.*
- A user population known to sit on /56s (a coarser key becomes worth its cost). *Consolidated into the register (ticket 33): R-RL-023. Amend the table by ID, not this list.*
- APNIC adopting `prefixlen:`, or an RFC 9977 feed becoming available for the deployment's ranges. *Consolidated into the register (ticket 33): R-RL-024. Amend the table by ID, not this list.*
- A mail transport landing (§6 route (c) becomes real, and deploy access stops being required for every disable). *Consolidated into the register (ticket 33): R-CRED-026. Amend the table by ID, not this list.*

### 12. Amendments made

Written into tickets 09, 13, 15, 16, 17, 20, 24, 25, 26 and 29 below each ticket's existing content. The map's
Decisions-so-far gains this ticket's pointer. Two stale copies of the 7× figure, in `map.md:346` and
`threat-model/report.md:92`, are left as the historical record of the route walked, with the correction owned by 09
and 15.

Status: resolved.
