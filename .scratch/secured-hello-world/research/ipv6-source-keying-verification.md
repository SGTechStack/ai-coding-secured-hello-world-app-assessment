# Verification asset — ticket 31 (IPv6 source keying)

Produced under the map's **verification rule** to discharge ticket 31's research obligation: "State which RFC or
operational source justifies the prefix, and verify it under the map's verification rule." Primary sources only:
RFC Editor text, RIPE-690, NIST SP 800-63B-4 (online edition), AWS and Google Cloud first-party docs, Cloudflare /
nginx / HAProxy / Envoy / Spamhaus first-party docs, and source at the versions Boot `v4.1.1` pins:
**Tomcat 11.0.24** (`gradle.properties` `tomcatVersion`; source read on the `11.0.x` branch), **Spring Security
7.1.1** (`platform/spring-boot-dependencies/build.gradle`), and OpenJDK (`jdk-21+35` where behaviour differs by
release, `master` otherwise). The map's runtime is **Java 21** (map.md stack baseline), which matters for §10.

Each fact carries **CONFIRMED**, **WRONG** (the premise as stated is false) or **NUANCED** (true with a qualifier
that changes how it can be used). Items that could not be read at a primary source are marked **UNVERIFIED** and
must not carry an argument. Paraphrases are mine and ≤30 words.

Source prefixes:

- `RFC n` = `https://www.rfc-editor.org/rfc/rfcn.txt`
- `TC` = `https://github.com/apache/tomcat/blob/11.0.x/`
- `SS` = `https://github.com/spring-projects/spring-security/blob/7.1.1/`
- `JDK` = `https://github.com/openjdk/jdk/blob/master/src/java.base/share/`

Bias check: I expected "/64 is the natural key, and it is roughly one household". The first half survives as
*operational practice*; the second is **wrong** for fixed broadband (a household holds a /56 or /48, §3) and only
approximately right for mobile (§4). The Tomcat premise "getRemoteAddr is always a literal" is **wrong** once a
proxy strategy is on (§7.3).

---

## §1 — The /64 interface-identifier boundary

**NUANCED.** The /64 is normative, but for *interface identifiers on a link*, not for "who owns an address".

| Claim | Verdict | Citation | Paraphrase |
|---|---|---|---|
| IIDs are 64 bits for all unicast outside `000` binary prefixes | CONFIRMED, normative | RFC 4291 §2.5.1, as amended by RFC 7136 (quoted in RFC 7421 §1) | Interface IDs for unicast addresses not starting with binary 000 are required to be 64 bits; Modified EUI-64 applies only when derived from a MAC. |
| The routing architecture itself has no fixed /64 | CONFIRMED | RFC 7421 §1 | Routing is variable-length prefix based; all routing protocols support any length up to /128; there is no architectural fixed n. |
| Only documented exception | CONFIRMED | RFC 7421 §1 (citing RFC 6164) | /127 on router point-to-point links is the one standardised exception; longer-than-/64 subnets are outside current specs. |
| IETF consensus is to keep 64 | CONFIRMED | RFC 7421 §2 (last paragraph) | IETF consensus: benefits of a fixed 64-bit IID and cost of change outweigh arguments for varying it. |
| RFC 7421 is itself normative | **WRONG** | RFC 7421 header | RFC 7421 is **Informational**; the normative text is RFC 4291 / RFC 7136. |

**Consequence.** /64 is the smallest unit a SLAAC network can hand a LAN, so it is the smallest prefix an
end user can *freely* fill. It says nothing about how many /64s that user controls (§3).

## §2 — Address rotation inside a /64 (RFC 8981, RFC 7217)

**CONFIRMED, with a flag.**

| Claim | Verdict | Citation | Paraphrase |
|---|---|---|---|
| `TEMP_PREFERRED_LIFETIME` default | CONFIRMED: **1 day** | RFC 8981 §3.6 | Default regeneration interval is one day; implementations should let users change it. |
| `TEMP_VALID_LIFETIME` default | CONFIRMED: **2 days** | RFC 8981 §3.6 | Suggested default validity is two days, overridable. |
| Regeneration is jittered | CONFIRMED | RFC 8981 §3.4 step 4, §3.6 | `DESYNC_FACTOR` randomises lifetimes so hosts don't rotate at fixed or synchronised times. |
| At most one preferred temporary address per prefix | CONFIRMED (SHOULD-level) | RFC 8981 §3.5 | In normal operation only one non-deprecated temporary address per prefix per interface, except during regeneration. |
| New link → new temporary addresses | CONFIRMED (MUST) | RFC 8981 §3.6 | On attaching to a different link, existing temporary addresses MUST be removed and new ones generated. |
| RFC 7217 addresses rotate | **WRONG** | RFC 7217 Abstract | RFC 7217 stable addresses are **stable within a subnet**; the IID changes only when the host moves network. |

**Flag — common belief is wrong.** "Hosts rotate addresses every day, so a daily /128 key is fine for honest
users" is true, but it is the **wrong threat model**. RFC 8981 governs *well-behaved* hosts. Nothing stops a
host from configuring arbitrary IIDs in its own /64 (RFC 7934 §2: hosts on SLAAC and all 3GPP networks can form
addresses without asking the network). An attacker's rotation rate is bounded by nothing in these RFCs. RFC 7934 §7
also observes privacy addresses alone can leave ~7 addresses live per host (one per day for a week, citing
RFC 4941 §3.5), so even an honest host is several `/128` keys.

## §3 — End-site prefix sizes (RFC 6177 / BCP 157, RIPE-690)

**CONFIRMED; flag on "one /64 per household".**

| Claim | Verdict | Citation | Paraphrase |
|---|---|---|---|
| /128 per site no longer recommended | CONFIRMED | RFC 6177 §1 item 1 | A site implies multiple subnets and devices, so /128 assignments are no longer recommended. |
| Sites get at least one /64, usually more | CONFIRMED | RFC 6177 §1 (reaffirmed principle) | End sites should obtain at least one /64 and in most cases significantly more. |
| Home default | CONFIRMED: **/56 suggested, not mandated** | RFC 6177 §2 | Home sites should get significantly more than one /64, not necessarily a /48; a /56 meets the goals. |
| No hard-coded boundaries | CONFIRMED | RFC 6177 §1 item 2 | Fixed boundaries risk "classful" hard-coding; CIDR applies to all prefix bits. |
| RIPE-690 options | CONFIRMED | RIPE-690 §1, §4.2.2 | Either /48 for everyone, or /48 business and /56 residential, or reserve /48 and assign first /56. |
| Longer than /56 | CONFIRMED: **strongly discouraged** | RIPE-690 §1, §4.2.3 | Prefixes longer than /56 are strongly discouraged; a /64 per customer doesn't conform and breaks LAN functionality. |
| Cellular exception | CONFIRMED | RIPE-690 §4.2.4 | Phones get one /64 per PDP context; LTE modems/routers used as broadband still get /48 or /56. |
| Persistent prefixes recommended | CONFIRMED | RIPE-690 §5.3 | Persistent (stable) end-user prefixes are strongly recommended; non-persistent ones are considered harmful. |
| Broadband Forum floor | CONFIRMED (as quoted) | RIPE-690 §4.2.3 quoting BBF TR-177 | TR-177: at least /60 for home/SOHO, /56 recommended, /48 for larger organisations. |

**Flag — common belief is wrong.** "A /64 is one customer, like one IPv4 address behind a home NAT" does not hold
for fixed broadband done per BCP. A residential customer on a /56 controls **256** /64s, a business on a /48
controls **65 536**. A /64 key is one *LAN*, so one BCP-conformant subscriber can present 2⁸–2¹⁶ distinct /64 keys.
A /56 key matches the RIPE residential recommendation but would, per RFC 9977 §1 (§8.4 below), lump together
neighbours at ISPs that hand out /64s or /60s.

**Persistence** (RIPE-690 §5) cuts the other way: an honest residential prefix is stable for months, so a
prefix key does not reset for honest users the way temporary /128s do.

## §4 — Mobile: one /64 per UE bearer; tethering

**NUANCED.** "/64 ≈ one device" is closer to "/64 ≈ one subscription's PDN connection, which may be several devices".

| Claim | Verdict | Citation | Paraphrase |
|---|---|---|---|
| SLAAC only, no stateful DHCPv6 | CONFIRMED | RFC 6459 §5.2 | SLAAC is the only supported address configuration in 3GPP; stateful DHCPv6 isn't supported; stateless DHCPv6 is. |
| Each default bearer gets a unique /64 | CONFIRMED | RFC 6459 §5.2 | The network allocates each default bearer a unique /64 and guarantees it is unique for the UE. |
| UE may use any IID in it | CONFIRMED | RFC 6459 §5.2 | The UE may use any interface identifier for non-link-local addresses, including privacy extensions. |
| /64 lifetime tied to the connection | CONFIRMED | RFC 6459 §5.2 (last paragraph) | The /64's lifetime is bound to the layer-2 connection; renumbering requires closing it. |
| Tethering shares the same /64 | CONFIRMED | RFC 7278 §1, §3 R-1 | The UE advertises its 3GPP /64 onto the LAN so tethered hosts SLAAC from the same prefix. |
| Typical tether size | CONFIRMED (informative) | RFC 7934 §7 | Current mobile devices sharing an uplink typically support about 8 downstream clients. |
| DHCPv6-PD possible from Rel-10 | CONFIRMED | RFC 6459 §5.3; RFC 7278 §1, §2 | Prefix delegation exists from 3GPP Release 10, optional; PD'd routers get more than one /64. |
| 3GPP TS 23.401 / 29.061 read directly | UNVERIFIED | — | Carried here via RFC 6459's citations of TS 29.061 §11.2.1.3.2a; the 3GPP text was not read. |

**Flag.** Because the /64 is bound to the bearer, a phone that reconnects (airplane-mode toggle) will
typically receive a **different /64** from the carrier pool. RFC 6459 establishes the binding; how often a carrier
reuses the same /64 on reconnect was not established at a primary source. So the mobile rotation cost of a /64
key is "one reconnect", not "one new SIM". Compare RIPE-690 §4.2.4: an LTE *router* gets a /48 or /56.

## §5 — Multiple addresses per host (RFC 7934, BCP 204)

**CONFIRMED.**

| Claim | Verdict | Citation | Paraphrase |
|---|---|---|---|
| Networks should give hosts multiple addresses | CONFIRMED (RECOMMENDED) | RFC 7934 §8 | Deployments are RECOMMENDED to provide general-purpose hosts with multiple IPv6 addresses from each prefix. |
| No hard limit | CONFIRMED (NOT RECOMMENDED to limit) | RFC 7934 §8 | Imposing a hard limit on a host's address pool, especially one address per prefix, is NOT RECOMMENDED. |
| A /64 per host is an endorsed option | CONFIRMED | RFC 7934 §8 | Networks can let hosts form addresses via SLAAC or provide a dedicated /64, e.g. via DHCPv6-PD. |
| ~20 simultaneous addresses plausible | CONFIRMED (informative) | RFC 7934 §7 | A host doing a few common functions might need on the order of 20 addresses at once. |

**Consequence.** Per-/128 keying is contrary to the architecture's intent: an honest host legitimately spreads
across many /128s, and a network is *told not to* stop it. RFC 7934 §8's "dedicated /64 per host" option
means some networks give a single host a whole /64 — so the /64 is also the natural *host* unit
on those networks.

## §6 — Shared prefixes, clouds, NAT64 and CGNAT

**NUANCED.**

### §6.1 Clouds

| Claim | Verdict | Citation | Paraphrase |
|---|---|---|---|
| AWS: subnet is carved from the VPC block | CONFIRMED | AWS VPC User Guide, "Add IPv6 support for your VPC"; "Add or remove an IPv6 CIDR block from your subnet" | A VPC IPv6 block is associated, then a subnet CIDR from it; subnet netmask can range from the VPC's length down to /64. |
| AWS: ENI addresses are individual /128s from the subnet | CONFIRMED | EC2 API `AssignIpv6Addresses` | Specific IPv6 addresses, or a count, are assigned to a network interface from the subnet's IPv6 CIDR. |
| AWS: ENI prefix delegation is **/80** | CONFIRMED | EC2 User Guide, "Prefix delegation for Amazon EC2 network interfaces" › Basics | The IPv6 prefix you can assign to a network interface is /80. |
| GCP: each interface gets a **/96** from a /64 subnet range | CONFIRMED | Compute Engine, "Configure IPv6 addresses for instances"; "IP addresses" overview | Dual-stack/IPv6-only interfaces get a single /96; first /128 is configured; ranges come from the subnet's /64. |
| Unrelated tenants share one /64 at AWS/GCP | **WRONG** as to the /64 (source-inferred) | as above | Subnets belong to one VPC/project, so a /64 there spans one customer's VMs, not unrelated tenants. |
| Unrelated tenants share a /48 or /56 at clouds | CONFIRMED (IETF statement) | RFC 9977 §1 "Blocklisting/throttling" | Blocking at /48 or /56 can overblock by putting multiple VMs from different users in one bucket. |
| VPS/hosting providers sharing a /64 across customers | UNVERIFIED | — | No first-party provider doc was read for this; do not rely on it either way. |

**Addendum (ticket 31 review, round 3): the VPC block size.**

| Claim | Verdict | Citation | Paraphrase |
|---|---|---|---|
| An Amazon-provided VPC IPv6 block is a /56 | CONFIRMED (worked example) | AWS VPC User Guide, "VPC CIDR blocks" › IPv6 VPC CIDR blocks | Example: Amazon assigns `2001:db8:1234:1a00::/56` to the VPC; you cannot choose the range; subnets take /64s from it. |
| VPC IPv6 block sizes are fixed at /56 | **NUANCED** | same page | One block at creation, or up to five blocks from /44 to /60 in /4 increments. |
| A re-associated block is the same block | **WRONG** | same page | After disassociating, you cannot expect the same CIDR if you associate again later. |

**Consequence.** One AWS account holds 256 /64 keys per default VPC block for the price of creating subnets, up
to five blocks per VPC, and can swap the whole /56 by disassociating it. A /56 key would put one VPC in one bucket;
a /64 key gives that VPC 256 buckets. This is the cheapest source of many /64s available to any attacker, in any
RIR region, including Singapore (`ap-southeast-1`).

**Consequence.** A /64 key is *coarse* for cloud sources: one AWS VM can source 2⁴⁸ addresses from its own /80,
one GCP VM 2³² from its /96, and still all fall into its subnet's /64. That is good for the limiter (cloud is
a credential-stuffing origin the OWASP cheat sheet singles out, §8) and cheap for honest users (few end users log in from a VPC).

### §6.2 NAT64 / DNS64

**CONFIRMED (source-inferred for our topology).** RFC 6269 §2 lists NAT64 (RFC 6146) among large-scale IPv4
address-sharing mechanisms: the *view from outside* is a shared public IPv4 address. So an IPv6-only client
reaching an **IPv4-only** origin is seen with the NAT64's pool IPv4, i.e. behaves like CGNAT (§6.3). If our origin
publishes AAAA and is reachable on IPv6, DNS64 does not synthesise, and the client arrives natively on IPv6 with
its own /64. NAT64 is therefore relevant to our keying only if the deployment is IPv4-only (ticket 20's topology).

### §6.3 IPv4 CGNAT sharing ratios

| Claim | Verdict | Citation | Paraphrase |
|---|---|---|---|
| RFC 6888 specifies a sharing ratio | **WRONG** | RFC 6888 §3 (REQ-1…REQ-10), §5 | RFC 6888 sets CGN behaviour (paired pooling, per-subscriber port limits, logging); it states **no** subscribers-per-address ratio. |
| Per-subscriber port quota MUST be supported | CONFIRMED | RFC 6888 REQ-4 | A CGN MUST support configurable limits on external ports assigned per subscriber. |
| Paired pooling by default | CONFIRMED | RFC 6888 REQ-2 | A subscriber keeps one external IPv4 for all its sessions by default. |
| Ratios in practice | CONFIRMED (informative, illustrative) | RFC 6269 Appendix B | Multiplicative factor varies: under 10 for incumbents re-purposing pools, around 1000 for new entrants. |
| Penalty boxes break under sharing | CONFIRMED | RFC 6269 §13.1 | With shared IPv4, one user's failed logins can block others on their first attempt; penalty boxes "simply will not work". |

**Consequence for ticket 09's NAT reasoning.** RFC 6269 §13.1 is the primary-source statement of exactly the
residual ticket 09 priced at login. Under IPv6 the same *shared-egress* case reappears only when many users sit
in one key; with a /64 key that is one LAN (a tethering phone, an office floor, a campus VLAN), not an ISP-wide
CGNAT. Paired pooling (REQ-2) means a single IPv4 CGN subscriber is at least *stable* on one address.

## §7 — IPv4-mapped addresses, zone IDs and textual form in Java / Tomcat

### §7.1 Java never exposes an IPv4-mapped address

**CONFIRMED.**

- `JDK` `classes/java/net/Inet6Address.java`, class Javadoc "Special IPv6 address": Java uses `::ffff:w.x.y.z`
  internally only; **"Java will never return an IPv4-mapped address"**; text or byte input in that form is
  converted to an IPv4 address.
- Parsing: `InetAddress.getByName("::ffff:1.2.3.4")` → `Inet4Address` 1.2.3.4. In `getAllByName` (jdk-21+35),
  a 4-byte result from `textToNumericFormatV6` yields `new Inet4Address(null, addr)`; a zone ID on a mapped literal
  throws `UnknownHostException` ("invalid IPv4-mapped address").
- Sockets: `JDK` `native/libnet/net_util.c` `NET_SockaddrToInetAddress` builds an `Inet4Address` when
  `NET_IsIPv4Mapped`; NIO's `sun/nio/ch/NativeSocketAddress.address()` goes through
  `InetAddress.getByAddress(bytes)`, which calls `IPAddressUtil.convertFromIPv4MappedAddress` and returns
  `Inet4Address` (`InetAddress.java`, `getByAddress(String, byte[])`).

### §7.2 Tomcat `getRemoteAddr()` in socket mode

**CONFIRMED.** `TC` `java/org/apache/tomcat/util/net/NioEndpoint.java` `populateRemoteAddr()`:
`remoteAddr = sc.socket().getInetAddress().getHostAddress()`. Therefore, on a dual-stack listener:

- an IPv4 peer arriving on the IPv6 socket is returned as **`1.2.3.4`**, not `::ffff:1.2.3.4` (§7.1);
- an IPv6 peer is returned in Java's **full, uncompressed, lower-case** form, e.g. `2001:db8:0:0:0:0:0:1`
  (`Inet6Address.numericToTextFormat` — eight groups, `Integer.toHexString`, no `::`). This is **not** RFC 5952
  canonical text, but it is a deterministic function of the 16 bytes;
- a link-local peer carries a zone suffix, `%<n>`: `NativeSocketAddress` passes `sin6_scope_id` to
  `Inet6Address.getByAddress(null, bytes, scope_id)`, and `getHostAddress()` appends `%` plus the scope
  (`Inet6Address.getHostAddress` Javadoc). Only an on-link peer can have one.

### §7.3 Tomcat `getRemoteAddr()` behind `RemoteIpValve` — not a literal

**WRONG premise.** `TC` `java/org/apache/catalina/valves/RemoteIpValve.java` `invoke()`: the header value is
comma-split and the chosen token is passed **verbatim** to `request.setRemoteAddr(remoteIp)`. No literal
validation, no normalisation. The valve's own Javadoc example ("Sample with an untrusted proxy") shows
`request.remoteAddr` becoming the string **`untrusted-proxy`**. So in `proxy` mode `getRemoteAddr()` may be a
hostname, an uppercase or compressed IPv6 (`2001:DB8::1`), a bracketed form, a zone-suffixed form, or
garbage — whatever the last untrusted hop wrote. The default `internalProxies` list includes
`fe80::/10` and `fc00::/7` as well as `::1/128` (Javadoc table and field initialiser).

Only the Tomcat path was read. Spring's `ForwardedHeaderFilter` (`server.forward-headers-strategy=framework`)
was not re-verified here.

**Consequence.** Derive the key from **parsed bytes**, never from the `getRemoteAddr()` string. String keys would
split `2001:db8::1` and `2001:db8:0:0:0:0:0:1` into two buckets in proxy mode, and would admit
non-address strings.

**Correction (ticket 31 review, round 2): the splitting claim above is overstated.** The valve selects the
rightmost entry that is not an internal proxy, and that entry is written by *our* trusted proxy, not by the
client. A given proxy spells a given client the same way every time, and the same string reaches both the early
filter and `WebAuthenticationDetails`. So today's string keys do not split one client across buckets; they merely
cannot be prefix-masked. DNS exposure arises only once something calls `getByName`, which nothing does today. The
finding is a **constraint on the new parser** (it must not trust the token's shape), not a live defect.

## §8 — Operational practice at scale

| System | Verdict | Citation | Paraphrase |
|---|---|---|---|
| Cloudflare Rate Limiting (previous version) keys IPv6 by /64 | CONFIRMED | Cloudflare WAF docs, "Rate Limiting (previous version)" | Once an individual IPv4 address or IPv6 /64 range exceeds a threshold, further requests are blocked with 429. |
| Cloudflare current Rate limiting rules: IPv6 granularity | UNVERIFIED | WAF "Rate limiting parameters", "How Cloudflare determines the request rate" | Neither current page states an IPv6 prefix for the IP characteristic; do not assume /64 carries over. |
| nginx `limit_req` has a prefix option | **WRONG** | nginx `ngx_http_limit_req_module`, `limit_req_zone` | Key is text/variables; `$binary_remote_addr` is 4 or 16 bytes. No built-in prefix masking. |
| HAProxy can mask IPv6 before tracking | CONFIRMED | HAProxy 3.2 configuration, converter `ipmask(<mask4>[,<mask6>])`; `http-request set-src` example | `ipmask` takes a separate IPv6 mask (e.g. 64); without mask6, IPv6 addresses fail to convert. |
| Envoy has a masked remote-address descriptor | CONFIRMED, **default /128** | Envoy `config.route.v3.RateLimit.Action.MaskedRemoteAddress` | `v6_prefix_mask_len` defaults to 128 when unset; setting 64 yields e.g. `2001:abcd:ef01:2345::/64`. |
| Spamhaus lists IPv6 at /64 | CONFIRMED (CSS) | Spamhaus "Combined Spam Sources" page | CSS lists both IPv4 /32 and IPv6 /64. Other Spamhaus lists not checked. |
| Fail2ban aggregates IPv6 by prefix | UNVERIFIED | — | Not read at a primary source. |
| M3AAWG IPv6 receiving-policy guidance | UNVERIFIED | M3AAWG "Policy Issues for Receiving Email in a World with IPv6 Hosts" | PDF not retrievable by the fetch tool; content not verified. |
| OWASP recommends /64 aggregation | **WRONG** (silent) | OWASP Credential Stuffing Prevention Cheat Sheet, "IP Mitigation and Intelligence" | Warns IP blocking is easily circumvented; mentions IP/subnet volume and hosting-range classification; no IPv6 prefix guidance. |
| Spring Security `IpAddressMatcher` does prefix *matching* | CONFIRMED | `SS` `core/.../util/matcher/IpInetAddressMatcher.java` `matches` | Byte-wise CIDR comparison; IPv4 patterns never match IPv6 inputs and vice-versa. A matcher, not a key deriver. |
| RFC 6583 recommends /64 rate limiting | **WRONG** | RFC 6583 (cited in RFC 7421 §3.4) | RFC 6583 is about Neighbor Discovery cache exhaustion on routers, not application rate limiting. |

### §8.4 The IETF document that does address this: RFC 9977

**CONFIRMED — new primary source for ticket 31.** RFC 9977, *Publishing End-Site Prefix Lengths* (Standards
Track, 2026; formerly `draft-ietf-opsawg-prefix-lengths`), §1:

- *Blocklisting/throttling:* /48 or /56 overblocks (different users' VMs in one bucket); /64, /96 or /128 fill
  blocklists quickly; RFC 8981 temporary addresses cause unwanted unblocking at /128. "All these issues apply
  to throttling as well."
- *Rate limiting/CAPTCHAs:* over-aggregation (e.g. /48 or /56 when the ISP hands out /64s) makes neighbours
  "jointly" hit limits.
- It defines `prefixlen` CSV files referenced from RPSL `inetnum:`/`inet6num:` so operators can publish the
  real end-site size per range, including a CGN/proxy marker (§3.2). §8: the RIPE NCC database implements the
  `prefixlen:` attribute as of November 2025.

**Flag — common belief is wrong.** No IETF document recommends a single fixed prefix for throttling. The only
Standards-Track text on the question says **any fixed prefix is wrong for some networks**, and fixes it with
published per-range data rather than a constant. A fixed /64 is defensible as *operational practice*
(Cloudflare legacy, Spamhaus CSS), not as an IETF recommendation.

## §9 — NIST SP 800-63B-4 §3.2.2 Rate Limiting (Throttling)

**NUANCED: IP address is mentioned only as an optional risk signal; the mandatory limit is per account.**

- Mandatory: the verifier SHALL limit consecutive failed attempts **using a specific authenticator on a single
  subscriber account** to no more than 100, by disabling that authenticator (§3.2.2 ¶1). Nothing mandatory is
  keyed by network source.
- The IP allowance (§3.2.2, list of additional techniques): additional techniques **MAY** be used to reduce the
  likelihood of an attacker locking out the legitimate claimant, including risk-based or adaptive techniques
  using "the claimant's IP address, geolocation, timing of request patterns, or browser metadata".
- Elsewhere: §2 intro notes authentication from an unexpected "IP address block (e.g., a cloud service)" MAY
  prompt risk-based controls, and SHALL be assessed for efficacy and user impact; §5.3 Session Monitoring lists
  "IP address characteristics (e.g., whether the IP address is in a block known for abuse)".
- No prefix length, IPv6, or aggregation guidance anywhere in the sections read.

**Consequence.** NIST treats IP (including "blocks") as an adaptive signal whose population impact must be
assessed, which is the framing ticket 31's "shared egress" pricing needs. It gives no support to any particular
prefix. The per-account lockout (ticket 09) is the only NIST-mandated limiter; every per-source limiter is a MAY.

## §10 — Java: masking to /N, and not triggering DNS

### §10.1 `getByName` on an untrusted string can trigger DNS

**CONFIRMED.** `InetAddress.getByName` Javadoc: if a *literal* is supplied only the format is checked; otherwise
the name is resolved. Source (`jdk-21+35` `InetAddress.getAllByName`):

- a string starting with a hex digit or `:` is tried as IPv4 then IPv6; if IPv6 parsing fails **and** the string
  contains `:` (or was bracketed), `UnknownHostException` is thrown — no DNS;
- otherwise it falls through to `getAllByName0` — **a name-service lookup**. Strings such as `example.com`,
  `bad.cafe`, `unknown` or `untrusted-proxy` (§7.3) reach DNS. The leading-hex-digit test does not protect:
  `e`, `b`, `d`… are hex digits.

### §10.2 Non-resolving parsers, on Java 21

| Parser | Verdict | Citation | Notes |
|---|---|---|---|
| `InetAddress.ofLiteral` / `Inet6Address.ofLiteral` / `Inet4Address.ofLiteral` | CONFIRMED non-blocking, **but `@since 22`** | `JDK` `InetAddress.java`, `Inet6Address.java` | "This method doesn't block". **Not available on the map's Java 21.** Mapped literal → `Inet4Address`. |
| Guava `InetAddresses.forString` | CONFIRMED never uses DNS | Guava `com/google/common/net/InetAddresses.java` class and method Javadoc | Deliberately avoids name-service lookups; mapped → `Inet4Address`; accepts non-ASCII digits; scope ID checked against local interfaces. Adds a dependency if not already present. |
| Spring Security `InetAddressParser` | CONFIRMED heuristic guard, **package-private** | `SS` `core/.../util/matcher/InetAddressParser.java` | `assertNotHostName` then `getByName`. Not public API, so not usable by application code; and the guard is a heuristic. |
| `sun.net.util.IPAddressUtil` | Not recommended | `JDK` (module `java.base`, package not exported) | Internal JDK API; needs `--add-exports`. |

**Tomcat literal claim.** In **socket** mode `getRemoteAddr()` is always a literal (§7.2): it is
`getHostAddress()` of a socket peer. In **proxy** mode it is **not** (§7.3). A key-derivation site that sits in
ticket 09's client-IP customizer must therefore parse *without* DNS in proxy mode.

### §10.3 Minimal correct masking on Java 21 (no new dependency)

Gate on shape first so `getByName` can only ever see a literal, then mask bytes. The `:` rule relies on the
§10.1 behaviour (a `:`-containing non-literal throws rather than resolving); the IPv4 regex is strict
dotted-quad. Not compiled or run in this session.

```java
private static final Pattern DOTTED_QUAD =
        Pattern.compile("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

/** Returns a family-tagged, prefix-masked key, or empty if {@code raw} is not an IP literal. Never resolves DNS. */
static Optional<String> sourceKey(String raw, int v6PrefixBits) {
    if (raw == null || raw.isEmpty() || raw.length() > 64) return Optional.empty();
    if (!DOTTED_QUAD.matcher(raw).matches() && raw.indexOf(':') < 0) return Optional.empty(); // would hit DNS
    final InetAddress addr;
    try {
        addr = InetAddress.getByName(raw); // literal-only by construction; mapped -> Inet4Address
    } catch (UnknownHostException e) {
        return Optional.empty();
    }
    byte[] b = addr.getAddress(); // 4 or 16 bytes; zone ID is not part of the bytes
    if (b.length == 4) return Optional.of("4:" + HexFormat.of().formatHex(b));
    int full = v6PrefixBits / 8, rem = v6PrefixBits % 8;
    if (rem != 0) b[full] &= (byte) (0xFF << (8 - rem));
    Arrays.fill(b, full + (rem != 0 ? 1 : 0), 16, (byte) 0);
    return Optional.of("6:" + HexFormat.of().formatHex(b) + "/" + v6PrefixBits);
}
```

Pitfalls this handles: DNS on non-literals (§10.1); `::ffff:a.b.c.d` collapsing to the IPv4 key (§7.1);
textual variants and case (keyed on bytes, §7.2–7.3); zone IDs (dropped by `getAddress()`); family collision
(`4:`/`6:` tag). A zone ID naming a non-existent interface throws and yields empty. Callers must decide what an
empty key means (fail closed to a shared "unparseable" bucket is the safe default).

Pitfalls it does **not** handle, recorded for ticket 31:

- **Teredo (`2001::/32`)** puts the Teredo *server's* IPv4 in bits 32–63 and the client in bits 96–127
  (RFC 4380 §4, not re-read in this session: UNVERIFIED). A /64 key would lump all clients of one Teredo server.
- **6to4 (`2002::/16`)** embeds the site's IPv4 in bits 16–47; a /64 key there is finer than the site (/48).
- NAT64 well-known prefix `64:ff9b::/96` would only appear if *our* side translated; not expected (§6.2).

## §11 — APNIC region and Singapore ISPs (added in round 2)

**NUANCED.** APNIC is looser than RIPE-690: it permits anything from /64 to /48 per end site. No Singapore ISP
publishes its delegation size at a first-party source that I could read, so the Singapore-specific premise is
**UNVERIFIED**.

Sources: `APNIC-POL` = APNIC Internet Number Resource Policies, `https://www.apnic.net/community/policy/resources/`
(current web edition; the page carries no document number). `APNIC-114` = *APNIC guidelines for IPv6 allocation
and assignment requests*, `https://www.apnic.net/ipv6-guidelines` (APNIC-114 v012, 7 May 2024, Active, implements
prop-155). Whois templates were read live on port 43 (`whois.apnic.net -t inet6num` / `-t inetnum`, service
1.88.48; `whois.ripe.net -t inet6num`, service 1.124.1).

### §11.1 APNIC end-site assignment policy

| Claim | Verdict | Citation | Paraphrase |
|---|---|---|---|
| APNIC mandates a specific end-site size | **WRONG** | APNIC-POL §5.2.3.2 | The LIR/ISP decides the end-site assignment size locally, as "n × /64"; RIRs/NIRs don't care which size is actually assigned. |
| Over /48 per site needs justification | CONFIRMED | APNIC-POL §5.2.3.3; APNIC-114 §10.1, §10.1.1 | Assigning more than a /48 to one end site needs documented justification; the LIR must file a second-opinion request with APNIC. |
| APNIC guideline sizes | CONFIRMED: **/64, /56 or /48** | APNIC-114 §10.1 | /64 if only one subnet is known to be needed; /56 for small sites and SOHO; /48 for larger or growing sites. |
| /64 per end site is within APNIC guidance | CONFIRMED | APNIC-114 §10.1 | An LIR can assign anything from /64 to /48 to an end-site customer. This contrasts with RIPE-690, which strongly discourages longer than /56 (§3). |
| Utilisation is measured in /56 units | CONFIRMED | APNIC-POL §2.5, §8.3.2, Appendix A | Utilisation and the HD-ratio count /56s delegated to end sites, not addresses inside them. |
| Single static IPv4 customer may get up to /48 | CONFIRMED | APNIC-114 §8.1.1 | A customer given one static IPv4 address can be given up to a /48 in IPv6, or smaller per RFC 6177. |
| Sub-/48 assignments are public in Whois | **WRONG** | APNIC-114 §12 and note; APNIC-POL §5.3.1 | Registering assignments smaller than /48 is optional. Customer registrations must be recorded but are hidden by default. |

**Consequence.** In the APNIC region a /64 per residential customer is **policy-conformant**. RIPE-690's "a /64
per customer does not conform" (§3) is a RIPE-region recommendation and does not carry over. A fixed /56 key
would therefore lump together up to 256 separate APNIC-region subscribers at any ISP that hands out /64s. That is
exactly the RFC 9977 §1 over-aggregation case.

### §11.2 Singapore fixed broadband (residential fibre)

| ISP | Verdict | First-party sources read | Notes |
|---|---|---|---|
| Singtel | **UNVERIFIED** (size) | `singtel.com/business/support/faq-ipv6`; `…/internet/ipv6-readiness`; `…/fibre-broadband-static-ip` (search snippet); 5 Gbps FAQ PDF (text extracted, no IPv6 content) | First-party pages confirm business dual-stack IPv6. None states a delegated prefix length, residential or business. |
| StarHub | **UNVERIFIED** | None found. Site searches returned no IPv6 support page | Nothing first-party read. |
| M1 | **UNVERIFIED** | `m1.com.sg/support/faq/tech-support-faq` (no "IPv6" match) | Nothing first-party read on IPv6. |
| MyRepublic | **UNVERIFIED** | `support.myrepublic.net` article 8830009 (page body not rendered; search snippet only) | The snippet describes IPv6 in generic terms as a future transition. It states no service offering or prefix. Not enough to carry an argument either way. |
| ViewQwest | **UNVERIFIED** | None found | Nothing first-party read. |
| IMDA | CONFIRMED silent on prefix sizes | IMDA RS IPv6 (October 2016), *Singapore IPv6 Profile* (PDF, text extracted) | This is a capability checklist for procurement, adapted from USGv6. It lists RFC 3633 DHCPv6-PD per device class and states no delegation size. |

**Third-party only, not usable:** HardwareZone forum threads and personal blogs report a /56 by DHCPv6-PD on
Singtel (and a static /48 on request for business), a /56 on ViewQwest, and Singtel ONR setups where clients
effectively see one /64. These are user reports. They are recorded only to show that the question is open, and
must not carry an argument.

**Consequence.** Ticket 31 cannot claim "Singapore residential = /56" (or any other size) from a primary source.
The APNIC policy range (/64–/48, §11.1) is the only defensible statement.

### §11.3 Singapore mobile carriers

| Carrier | Verdict | Citation | Paraphrase |
|---|---|---|---|
| SIMBA offers dual-stack mobile data | CONFIRMED (by instruction) | SIMBA support, "What is the APN Setting for SIMBA?" | If the phone exposes an APN IP-protocol setting, customers should choose IPv4/IPv6 rather than IPv4 only. |
| SIMBA: /64 per bearer | UNVERIFIED (carrier-specific) | — | Not stated by SIMBA. The architectural claim of one /64 per default bearer holds for all 3GPP networks (RFC 6459 §5.2, see §4 above). |
| Singtel / StarHub / M1 mobile IPv6 | UNVERIFIED | — | No first-party mobile IPv6 page found. A StarHub VoLTE device-list PDF appeared in search results but is irrelevant to IPv6. |

### §11.4 APNIC Whois support for RFC 9977 `prefixlen:`

| Claim | Verdict | Citation | Paraphrase |
|---|---|---|---|
| APNIC `inet6num`/`inetnum` has a `prefixlen:` attribute | **WRONG** (as of the query) | Live `whois.apnic.net -t inet6num` and `-t inetnum` templates | The templates list `geofeed:`, `geoloc:`, `remarks:` and the others, but **no `prefixlen:`**. |
| APNIC registrants can still publish via `remarks:` | CONFIRMED (by construction) | RFC 9977 §4, §8; APNIC template has `remarks:` [optional][multiple] | Where an RIR lacks `prefixlen:`, RFC 9977 defines a `remarks: Prefixlen <URL>` form that consumers MUST also accept. |
| RIPE `inet6num` has `prefixlen:` | CONFIRMED | Live `whois.ripe.net -t inet6num` template | `prefixlen: [optional] [single]` is present, alongside `assignment-size:`. |
| APNIC supports `geofeed:` (the RFC 9632 analogue) | CONFIRMED | Live APNIC templates | `geofeed:` is optional and single-valued in both `inetnum` and `inet6num`. |

**Consequence.** Consuming RFC 9977 data for APNIC-region sources means parsing `remarks:` lines. APNIC hides
customer assignments by default (§11.1), so expect sparse coverage for Singapore residential ranges. RFC 9977 is
**not** a practical key source for this app today. It supports the argument that no fixed prefix is universally
right; it does not supply a value.

### §11.5 IM8 / IMDA / GovTech: IPv6 reachability for government web apps

| Claim | Verdict | Citation | Paraphrase |
|---|---|---|---|
| Public IM8 Reform controls require IPv6 reachability | **WRONG** (not present) | `github.com/GovTechSG/tech-standards` @ `f636bc47` (2025-09-02), `catalogs/im8-reform.json` (OSCAL, ~304 KB) | Zero matches for IPv6, "IP version 6", dual-stack or AAAA in the public IM8 Reform catalogue. |
| Full (non-public) IM8 IPv6 requirement | UNVERIFIED | — | The complete agency IM8 is not public. Nothing was read, so nothing can be concluded either way. |
| IMDA IPv6 Profile mandates gov-service IPv6 | **WRONG** | IMDA RS IPv6 (Oct 2016) §1, §5 | It helps agencies write procurement specifications and audit infrastructure. It is explicitly not a transition guide or policy, and sets no reachability mandate. |
| CSA Internet Hygiene Portal covers IPv6 | NUANCED | CSA IHP, "Internet Protocol Version 6 (IPv6)" information page | CSA lists IPv6 as an IHP topic with a security focus. The page states no requirement that .gov.sg services be IPv6-reachable. |

**Consequence.** No primary source reachable here obliges this app to be IPv6-reachable. Ticket 20's topology
decides whether IPv6 clients arrive natively. Ticket 31's keying must still be correct if they do.

## §12 — RFC 9977 independent re-check

**CONFIRMED.** The RFC exists, the number is right, and the §1 and §8 statements used in §8.4 are accurate. One
paraphrase in §8.4 overstates the text (see the last row).

Sources: `https://www.rfc-editor.org/rfc/rfc9977.txt` (full text read), `https://www.rfc-editor.org/info/rfc9977`,
`https://datatracker.ietf.org/doc/rfc9977/`, `https://datatracker.ietf.org/doc/draft-ietf-opsawg-prefix-lengths/`.

| Claim | Verdict | Citation | Paraphrase |
|---|---|---|---|
| RFC 9977 exists | CONFIRMED | RFC Editor text, info page, datatracker | All three resolve to the same document. |
| Title | CONFIRMED: *Publishing End-Site Prefix Lengths* | RFC 9977 header | — |
| Status | CONFIRMED: **Standards Track** (maturity level not stated in the text read) | RFC 9977 header, "Status of This Memo" | An Internet Standards Track document, IETF consensus, approved by the IESG. |
| Date | CONFIRMED: **May 2026** | RFC 9977 header | — |
| Authors | CONFIRMED | RFC 9977 header | O. Gasser (IPinfo), R. Bush (IIJ Research & Arrcus), M. Candela (NTT), R. Housley (Vigil Security). |
| Draft lineage `draft-ietf-opsawg-prefix-lengths` | CONFIRMED | datatracker draft page | The draft URL serves text whose IANA tables cite "RFC 9977", i.e. the published RFC. |
| §1 blocklisting/throttling text | CONFIRMED | RFC 9977 §1 | /48 or /56 can overblock different users' VMs; /64, /96 or /128 fill blocklists; /128 plus RFC 8981 leads to unwanted unblocking; the same applies to throttling. |
| §1 rate-limiting/CAPTCHA text | CONFIRMED | RFC 9977 §1 | Grouping at /48 or /56 when the ISP hands out /64s makes neighbours jointly hit rate limits or CAPTCHAs. |
| §3.2 CGN/proxy marker | CONFIRMED | RFC 9977 §3.2 | The third CSV field gives the number of CGN or proxy end-sites per stated prefix length, so providers can adjust rate limits. |
| Applies to `inet6num:` | CONFIRMED | RFC 9977 §1 | Wherever `inetnum:` appears, `inet6num:` is also meant. |
| §8: RIPE NCC implements `prefixlen:` as of Nov 2025 | CONFIRMED | RFC 9977 §8; live RIPE template (§11.4) | RFC 9977 §8 states it, and the RIPE `inet6num` template independently shows `prefixlen:`. |
| Other RIRs use `remarks:` meanwhile | CONFIRMED | RFC 9977 §4, §8 | Consumers MUST accept both `remarks: Prefixlen <URL>` and `prefixlen:` until every RIR migrates. |
| Refetch cadence | CONFIRMED | RFC 9977 §7 | No frequent real-time lookups; honour Expires/Cache-Control; without them, no more often than weekly. |
| Publishers can lie | CONFIRMED | RFC 9977 §9 | Operators could falsely claim CGN to evade throttling, or publish /128 sizes to bloat consumer storage. Treat the data as third-party input. |
| §8.4 wording "any fixed prefix is wrong for some networks" | NUANCED | RFC 9977 §1 | RFC 9977 lists over- and under-aggregation failures for example prefixes. That fixed prefixes misfit some networks is my inference, not quoted text. |

---

## Summary table

| # | Fact | Verdict | Load-bearing correction |
|---|---|---|---|
| 1 | /64 IID boundary | NUANCED | Normative for IIDs (RFC 4291/7136); RFC 7421 is Informational; routing has no fixed /64 |
| 2 | RFC 8981 rotation | CONFIRMED | 1-day preferred / 2-day valid; RFC 7217 addresses do **not** rotate; attackers are unbounded by either |
| 3 | End-site sizes | CONFIRMED | /56 residential, /48 business (RIPE-690); longer than /56 strongly discouraged; **a /64 is a LAN, not a subscriber** |
| 4 | Mobile /64 | NUANCED | One /64 per bearer, shared by tethered devices; bound to connection lifetime, so reconnect ≈ new /64 |
| 5 | RFC 7934 | CONFIRMED | Hosts SHOULD get many addresses; NOT RECOMMENDED to limit to one per prefix |
| 6 | Shared prefixes | NUANCED | AWS /80 per ENI, GCP /96 per NIC, both inside a single-tenant /64; cross-tenant sharing at /48–/56 (RFC 9977); RFC 6888 sets **no** sharing ratio; RFC 6269 §13.1 penalty-box failure |
| 7 | Java/Tomcat text forms | NUANCED | Mapped → `Inet4Address`/`1.2.3.4`; full uncompressed IPv6 text; `%scope` on link-local; **RemoteIpValve sets remoteAddr verbatim, can be a hostname** |
| 8 | Operational practice | NUANCED | Cloudflare legacy /64, Spamhaus CSS /64, HAProxy/Envoy configurable (Envoy default /128), nginx none; **RFC 9977 says no fixed prefix is right**; RFC 6583 is not about this |
| 9 | NIST 800-63B-4 §3.2.2 | NUANCED | Mandatory limit is per account; IP address appears only as a MAY adaptive signal |
| 10 | Java masking | CONFIRMED with correction | `getByName` resolves non-literals; `ofLiteral` is Java 22+ (unusable on 21); Guava `forString` is DNS-free; Tomcat literal-only holds in socket mode only |
| 11 | APNIC region / Singapore | NUANCED; SG ISP sizes UNVERIFIED | APNIC allows /64–/48 per end site (APNIC-114 §10.1), so **a /64 per subscriber is conformant here**, unlike RIPE-690. No first-party SG ISP or carrier prefix size found; SIMBA advises an IPv4/IPv6 APN. APNIC Whois has **no `prefixlen:`** (`remarks:` only). Public IM8 Reform catalogue and IMDA IPv6 Profile set no IPv6-reachability mandate |
| 12 | RFC 9977 re-check | CONFIRMED | Exists; *Publishing End-Site Prefix Lengths*; Standards Track; May 2026; §1 and §8 statements accurate; RIPE template shows `prefixlen:`. §8.4's "any fixed prefix is wrong" is an inference (NUANCED) |
