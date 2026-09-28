# 07: Source key resolver and trusted proxies

**What to build:** One `SourceKeyResolver` derives the *source key* for every per-source control and log field (ADR-020): IPv4 as /32, and IPv6 masked to `app.security.client-ip.ipv6-prefix-length` (default 64, allowed 48–128), tagged with its family. It never does DNS. The *client address* is the socket peer, unless a named trusted proxy is configured (REJ-015; R-RL-007). Using the raw address anywhere is banned. The decision logic is a pure function, so it can be unit- and mutation-tested.

**Blocked by:** 01

**Status:** ready-for-agent

- [ ] Unit tests cover IPv4, IPv6 at several prefixes, IPv4-mapped IPv6, and prefix bounds. A prefix outside 48–128 stops startup.
- [ ] On a real port, `X-Forwarded-For` from an untrusted peer is ignored. From a named trusted proxy it is honoured.
- [ ] An ArchUnit rule forbids reading `getRemoteAddr()` outside the resolver.
- [ ] The resolver is in the PIT scope and meets 85%.
