---
status: accepted
---

# ADR-020: Source key: IPv6 aggregated to /64 by one property, from one resolver, with the raw-address path banned

Every per-source control keys on one value, the **source key**. For IPv4 it is the /32. For IPv6 it is the address
masked to `app.security.client-ip.ipv6-prefix-length`, which defaults to 64. It is computed from parsed bytes,
never from the address string, by one `SourceKeyResolver`. Keying on the full address is the default in most
libraries and looks more precise. Under IPv6 it gives one host an unlimited supply of fresh keys.

## Context

- **A /64 is the smallest unit an end network can fill freely.** Interface identifiers are 64 bits (RFC 4291 §2.5.1,
  RFC 7136). RFC 7934 recommends that hosts get many addresses and advises networks not to limit them to one per
  prefix. So under /128 keying, one host rotates inside its own /64 at no cost.
- **There is no prefix that is right everywhere.** RIPE-690 recommends /56 for residential and /48 for business end
  sites. But APNIC-114 §10.1 permits anything from /64 to /48 per end site, so in this region a /64 may be the whole
  subscriber, and a /56 may hold 256 of them. A /56 would also put one AWS VPC in one bucket. RFC 9977 (Standards
  Track, May 2026) says any fixed prefix overblocks or underblocks someone, and it publishes per-range prefix data
  instead. That data is not available for the APNIC region, because APNIC Whois has no `prefixlen:` attribute.
- **/64 is operational practice, not an RFC rule.** Cloudflare's earlier rate limiting and Spamhaus CSS both use it.
  NIST SP 800-63B-4 §3.2.2 mandates only the per-account limit. IP address appears there only as an optional signal.
- **Strings split keys.** `RemoteIpValve` passes the chosen `X-Forwarded-For` token through verbatim, so case,
  compression, IPv4-mapped forms and zone suffixes can spell one client several ways. `InetAddress.getByName` on a
  non-literal performs a DNS lookup. `InetAddress.ofLiteral` would avoid that, but it needs Java 22, and the build is
  on Java 21.
- **Logs and limiters must agree on what "a source" is.** If the limiter keyed on /64 and the log hashed /128, an
  attacker rotating inside one /64 would look like thousands of sources. That would hide the enumeration signature and
  fill the audit emitter's distinct-source cap (ADR-019) at once.

## Decision

- **One key, one granularity.** It is used by every source row in the budget table, the lockout-cardinality axis
  (ADR-015), the session-miss budget (ADR-017), the tier-1 audit keys, and `source.ip_hash`, which hashes the key and
  not the address (ADR-054). A second, coarser /56 key for the cardinality axis was declined (R-RL-016).
- **Text form, pinned because it is an HMAC input:** `4:<8 lower-case hex>` or `6:<32 lower-case hex>/<n>`. The
  family tag keeps IPv4 and IPv6 keys apart. Including `/n` means a change to the prefix changes every IPv6 hash, and
  that is a correlation break just like a key rotation.
- **The property is validated at startup.** It binds through `@Validated @ConfigurationProperties` and must satisfy
  48 ≤ n ≤ 128. The startup log line states the effective n, so the deployer has evidence of it.
- **Parsing is bytes-first and never uses DNS.** A strict shape check (dotted quad, or the presence of `:`) runs
  before `getByName`. Masking is done on the bytes. IPv4-mapped addresses collapse to IPv4, and zone ids are dropped.
  A token that fails the shape check goes to one shared bucket keyed `unparseable`. That increments a counter and logs
  the token escaped and truncated to 64 characters (R-RL-020).
- **One resolver, two callers, one derivation per request.** The early filter resolves the key and stores it as a
  request attribute. The login converter's details source builds `SourceKeyAuthenticationDetails` from that
  attribute, and calls the resolver only as a fallback.
- **Raw-path ban, as an architecture test.** Production code may not call `HttpServletRequest.getRemoteAddr()` or
  `WebAuthenticationDetails.getRemoteAddress()` anywhere except inside `SourceKeyResolver`.
  `SourceKeyAuthenticationDetails` overrides `toString()` so that no raw address reaches a log line.

## Consequences

- A /64 stops one host from rotating. It does not stop a subscriber, a VPC or a /48 holder (R-RL-017). What bounds
  those attackers is the lockout ladder, which does not depend on the source (ADR-011).
- A shared office /64 behaves like shared IPv4 NAT, which is already priced. Teredo and 6to4 key at the wrong
  granularity (R-RL-018). A dual-stack host gets two budgets (R-RL-019). No host-level forensics are possible inside
  one /64 (R-AUD-035). Singapore ISP prefix sizes are unverified (R-RL-021).
- The rule covers this application's code only. Tomcat's access log and the edge still see raw addresses, and the
  edge's copy is the one an incident runbook needs.
- Reopen when a user population is known to sit on /56s (R-RL-023), or when APNIC prefix data or an RFC 9977 feed
  becomes available (R-RL-024).
- Tests: T-RL-025 (no DNS, with a failing resolver provider installed), T-RL-026 (key equivalence classes), T-RL-027
  (property default and bounds), T-RL-028 (raw-path ban), T-RL-029 (one derivation per request), T-CFG-037 (startup
  log line), T-AUD-040 (details `toString()` carries no address).

## Sources

- RFC 4291 §2.5.1 and RFC 7136 (64-bit interface identifiers); RFC 7934 (multiple addresses per host); RFC 9977
  *Publishing End-Site Prefix Lengths* (Standards Track, May 2026) §1, §8.
- RIPE-690 (end-site prefix recommendations); APNIC-114 §10.1 (/64 to /48 per end site).
- NIST SP 800-63B-4 (July 2025) §3.2.2 (IP address as an optional risk-based signal only).
- OWASP ASVS 5.0, 15.3.4 (L2) (real client IP for security decisions).
- Java SE 21 `InetAddress` (`getByName` resolves non-literals; `ofLiteral` since Java 22); Apache Tomcat
  `RemoteIpValve`.
