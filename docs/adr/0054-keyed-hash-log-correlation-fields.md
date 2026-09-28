---
status: accepted
---

# ADR-054: Log correlation fields are keyed hashes: `source.ip_hash` over the source key and `session.hash`, never the address

No log line carries a client address. Where a row needs to say "the same source", it carries `source.ip_hash`, an
HMAC under the log key over the **source key**, not over the address. `session.hash` is an HMAC under the same key.
A maintainer following the corpus would undo both: the AuthN logging recipe logs cleartext `source.ip` on every
security event, and `Log_Schema.md` defines `session.hash` as a SHA-256 hash with no key.

## Context

- The logging standard's §3.3 lists "Client IP addresses" and raw session IDs under what must not be logged, and ends
  with "If any sensitive value must be logged for correlation, mask or hash it". Its §5 makes the address ban a
  release test for request logs.
- The AuthN recipe (§4, §10, §11) and the typed-module recipe (§7) say the opposite for security events: include
  `source.ip` on every event, for investigation. `Log_Schema.md` defines `source.ip` as a plain `string` field. Recipes
  lose to the standards they illustrate, so the recipe is the thing this decision departs from, not §3.3.
- `Log_Schema.md` defines `session.hash` as "a one-way hash (SHA-256) of the raw session identifier" and names no key.
  The governing standard's §3.4 asks for "a hashed session identifier for correlation" and names no algorithm.
- ASVS 5.0 16.2.5 (L2) says logging of sensitive data follows its protection level, and gives session tokens, logged
  only hashed or masked, as its example. It does not name IP addresses. The citation for hashing the address is that
  general rule plus V16's control objective.
- An unkeyed hash of an address is a lookup table. The whole IPv4 space is 2³² values, and the allocated IPv6 /64
  space is far smaller than 2⁶⁴. A key is what makes the hash a pseudonym rather than an encoding.
- Every per-source control keys on the source key, not the address (ADR-020). A log field over the address would count
  something different from what the limiters count.
- ECS types `source.ip` as `ip`, core level. A sub-field such as `source.ip.hash` would turn it into an object where
  every other producer on the platform emits a scalar.

## Considered options

- **Cleartext `source.ip` on security events only (the recipes).** Breaks §3.3 outright, and moves raw personal data
  into the application's own logs for a benefit a keyed hash also delivers.
- **Plain SHA-256, as `Log_Schema.md` words it.** For `session.hash` the input is high-entropy, so this is not
  reversible, but anyone holding a cookie can confirm which rows are theirs. For the address it is reversible by
  enumeration.
- **HMAC over the full address.** One host can rotate freely inside its own /64, so the logs would show thousands of
  sources where the limiters see one, and an enumeration signature would vanish.
- **`source.ip.hash` or `labels.source_ip_hash`.** The first conflicts with the `source.ip` mapping. The second is
  safe but splits client facts across two prefixes.
- **HMAC over the source key, as `source.ip_hash`, and an HMAC `session.hash` (chosen).**

## Decision

- **`source.ip_hash`** is `HMAC-SHA-256(app.security.hmac.log.key, "ip:" || source_key)`, lowercase hex. The source
  key's text form is `4:<8 lower-case hex>` or `6:<32 lower-case hex>/<n>`, where `n` is
  `app.security.client-ip.ipv6-prefix-length`. The family tag stops a v4 key colliding with a v6 key. An unparseable
  client-IP token hashes the constant key `unparseable`; the raw token appears only escaped and truncated in its own
  WARN or ERROR line.
- **`session.hash`** is an HMAC-SHA-256 under the same log key, with its own domain prefix, emitted as a 64-character
  lowercase hex digest.
- `source.ip` and `source.ip.hash` are never emitted, on any row or appender. `user.hash` is never emitted either.
- The log key rotates no faster than the investigation window, or the previous key is kept for verification.
  Rotation breaks correlation across the boundary, and nothing looks wrong afterwards: every row still has a hash.

## Consequences

- **Logs count what the limiters count.** Distinct `source.ip_hash` values are distinct source keys, so the
  distinct-source cap (ADR-019) and the enumeration signals read the same unit as the limiters.
- **IPv4 hashes differ from any hash over the dotted quad.** No hash computed the older way matches.
- **Changing the prefix length breaks IPv6 correlation** across the change, as a key rotation does. The startup line
  stating the prefix length (T-CFG-037) is the only marker. R-AUD-021 records this.
- **No log of ours distinguishes hosts inside one IPv6 /64** (R-AUD-035). Host-level attribution needs the edge's raw
  access log, which the deployer keeps for a bounded window (R-OPS-009).
- **The exact join belongs to the key holder.** An operator holding the log key can mask edge-log addresses to the
  source key, hash them the same way and match rows exactly (R-LCK-012). `trace.id` is no join key, because inbound
  trace context is restarted at the boundary (ADR-063).
- `source.ip_hash` is a custom field. A future ECS field of that name would collide (R-AUD-006), and the field is part
  of the schema amendment request (R-AUD-002). `Log_Schema.md`'s `session.hash` example is MD5-length (R-STD-006).
- Tests: T-AUD-006 (no address in any textual form, only `source.ip_hash`), T-AUD-041 (HMAC input pinned for
  `ip:4:…` and `ip:6:…/64`), T-AUD-020 (`session.hash` present, raw session id absent), T-AUD-008 (no `user.hash` or
  other cleartext identity key), T-CFG-037.
- Nothing yet fails if `session.hash` reverts to plain SHA-256. It needs a test of its own: for a known session id,
  the emitted value must not equal its unkeyed SHA-256.

## Sources

- Structured Logging Application Standard §3.3 Logging Contract (what must not be logged; hash for correlation),
  §3.5 Security Contract (hashed PII only with a system-wide salt or HMAC), §5 Test & Validation Standard.
- `Log_Schema.md`, Source table (`source.ip`) and User table (`session.hash`).
- Standalone User Access Control Application Standard §3.4 Logging Contract.
- Recipes: Logging Authentication and Authorisation Events §4, §10, §11; Centralising Audit Logging with a Typed
  Module §7.
- OWASP ASVS 5.0: 16.2.5 (L2); V16 Control Objective.
- Elastic Common Schema reference, Source fields (`source.ip`: type `ip`, level core).
