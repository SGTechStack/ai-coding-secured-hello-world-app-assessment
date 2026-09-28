# Ticket 17 sizing gate — inventory part A (tickets 01–06)

Line numbers are 1-based, counted on the files as they are now. Ids with a lowercase suffix (`13-A-a`) mark items
owned by a ticket outside group A that I found inside a group A file. That ticket has no local numbering here, so the
aggregator should renumber them.

## 1. Items

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 01-R-1 | register | 01 | 01:132–138, 01:307 | Register keyed by control ID: all 124 rows (36 IM8 + 88 ARC), five-value verdict, self-contained one-sentence reason each | live | |
| 01-R-2 | register | 01 | 01:136, 01:228, 01:307 | ac-3 inactive/expired accounts: DEFERRED (not N/A) with written justification; note missing `lastLoginAt`/`accountExpiresAt` columns | live | |
| 01-R-3 | register | 01 | 01:229, 01:280, 01:307 | ac-4 access review: DEFERRED only for the scheduled-revoke half; declared permission baseline is cheap and not to be deferred | live | |
| 01-R-4 | register | 01 | 01:93, 01:249 | ac-2 MFA option: accept the FAIL and register it as a deferral with written justification | superseded by 06:17 (ticket 19 added a TOTP factor gate) | |
| 01-R-5 | register | 01 | 01:69, 01:286, 01:307 | Declared risk classification (recommend Low Risk); without it no finding severity is derivable | live | |
| 01-R-6 | register | 01 | 01:70, 01:251–256, 01:307 | User-population declaration: standalone reference app, making ac-7/ac-8/ac-12/dp-8/lm-18/st-3 N/A on one basis. The user decides this | live | |
| 01-R-7 | register | 01 | 01:120–123, 01:258, 01:307 | ARC interpretation: N/A as product requirement; `op` code-generation controls claimed as workflow controls | live | |
| 01-R-8 | register | 01 | 01:143, 01:307 | Boot 4.1 → Boot 3.4 config-spelling mapping note so `im8-review`'s literal checks don't read compliant config as absent | live | |
| 01-R-9 | register | 01 | 01:242, 01:307 | Standards defect: `im8-review` st-3 footer snippet has typo `herf` for `href`. Flag it if the footer route is chosen | live | |
| 02-R-1 | register | 02 | 02:100–106, 02:109 | Deferral wording: credential expiry is not implemented because NIST §3.1.1.2 prohibits it (N/A-by-prohibition), not merely deferred | live | |
| 02-A-1 | adr-new | 02 | 02:140–145 | Conditional: if the reviewer insists on literal class quotas for admin-generated passwords, ADR citing NIST App. A.3 and the entropy cost | live (conditional) | borderline |
| 02-A-2 | adr-new | 02 | 02:331–339, 02:343 | Retain 12-char minimum below NIST 15-char single-factor floor (or raise to 15); ADR states the citation | live (conditional on 07's choice) | adr |
| 02-A-3 | adr-new | 02 | 02:74, 02:400–404, 02:437–452 | BCrypt over Argon2id: honest trade-off (legacy option, memory-hardness, 72-byte ceiling), mitigations, and pepper not taken | live | adr |
| 02-M-1 | adr-amend | 07 | 02:327–329 | Hashing ADR (owner 07; 02-A-3) must record the measured work factor and the machine it was measured on | live | adr |
| 02-R-2 | register | 02 | 02:421–426, 02:449 | Pepper / keyed second hash pass (NIST SHOULD, OWASP optional) not taken, blocked on unresolved secrets handling | live | |
| 02-R-3 | register | 02 | 02:388–390 | Deviation stricter than standard: `SameSite=Strict` + `__Host-` prefix. Explicitly "needs no ADR" (could also be read as adr-rejected) | live | |
| 02-R-4 | register | 02 | 02:390–392 | Profile-driven cookie name/`Secure` flag for HTTP local dev belongs in HTTPS/HSTS handover notes (not under the exact handover heading) | live | |
| 03-R-1 | register | 03 | 03:136–138 | Schema gap: `auth.method` used by recipes and app standard but undefined in `Log_Schema.md` | live | |
| 03-R-2 | register | 03 | 03:136, 03:139 | Schema gap: `session.max_inactive_interval` used by recipe, undefined in `Log_Schema.md` | live | |
| 03-R-3 | register | 03 | 03:136, 03:140 | Schema gap: `export.id` used by recipe, undefined in `Log_Schema.md` | live | |
| 03-R-4 | register | 03 | 03:263, 03:444 | Schema defect: `session.hash` example is 32 hex chars (MD5-length) though prose says SHA-256 | live | |
| 03-R-5 | register | 03 | 03:93, 03:445 | Schema defect: §3.1 names `thread.name`/`logger.name`; `Log_Schema.md` names `process.thread.name`/`log.logger` | live | |
| 03-R-6 | register | 03 | 03:247, 03:447 | Schema gap: no `event.action` distinguishes role change, enable/disable, delete. All collapse onto `user-administration` | live | |
| 03-R-7 | register | 03 | 03:249, 03:448 | Schema gap: no identity/authentication `event.category`; `process` is the catch-all | live | |
| 03-R-8 | register | 03 | 03:449 | Schema defect: `Log_Schema.md` title scopes it to batch/interface apps, yet interactive apps are bound to it | live | |
| 03-R-9 | register | 03 | 03:426–428, 03:512 | Standards discrepancy: lockout level ERROR (AuthN recipe) vs WARN (User Access Standard); resolved as ERROR | superseded by 03:590 (reversed to WARN) | |
| 03-R-10 | register | 03 | 03:464–466, 03:512 | Org-schema extension request: `user.target.id` for admin-action target | live; amended by 03:531 and 03:608–611 (valid ECS that `Log_Schema.md` is behind on) | |
| 03-R-11 | register | 03 | 03:518–522 | Org-schema extension: `user.target.roles` used in the role-change example but unregistered (added by ticket 11) | live; reclassified by 03:610 as valid ECS | |
| 03-R-12 | register | 03 | 03:338, 03:512 | Integrator obligation: platform-side 90-day retention (index lifecycle) is not the app's responsibility | live; in-app half added by 03:635–638 | |
| 03-R-13 | register | 03 | 03:311, 03:512 | Decision owed: pin logging dependency versions ourselves; the standard pins none | live | |
| 03-R-14 | register | 03 | 03:296, 03:512 | Obligation: retest the custom `StructuredLogEncoder` subclass after every Spring Boot upgrade (non-public API). Could also be a trigger | live | |
| 03-A-1 | adr-new | 03 | 03:261, 03:508, 03:512 | `session.hash` computed as HMAC with system-wide key rather than plain SHA-256 (standard requires only plain) | live; confirmed at 03:630 | borderline |
| 03-A-2 | adr-new | 03 | 03:352–353, 03:438–440, 03:512 | No Logback `MaskingJsonGeneratorDecorator` (incompatible encoder); prevention at source plus redaction in custom encoder | live | borderline |
| 03-X-1 | adr-reversed | 03 | 03:418, 03:512 | C1: log cleartext `source.ip` on security events, specific-beats-general over §3.3's client-IP ban | withdrawn by 03:577 (replaced by 13-A-a) | adr |
| 03-A-3 | adr-new | 03 | 03:496–498, 03:512 | C9: omit `user.id` on password-reset-requested, include on reset-completed (derived, not stated by standards) | live; affirmed by 03:605 | borderline |
| 11-R-a | register | 11 | 03:524–531 | Org-schema extension: `user.target.unlockReason` closed enum on admin unlock event | live; renamed `user.target.unlock_reason` by 03:612–613 | |
| 11-R-b | register | 11 | 03:533–537 | `user.target.count` on `adminUserListed`; whether it needs extension treatment is 13's call | live; 13 declares it custom at 03:612 | |
| 13-R-a | register | 13 | 03:585–588, 03:612 | Genuinely custom field `source.ip_hash` (not `source.ip.hash`), part of the extension package | live | |
| 13-A-a | adr-new | 13 | 03:577–583 | Narrower replacement for C1: hash `source.ip` everywhere, deviating from the AuthN recipe's `source.ip`, not from §3.3 | live | adr |
| 27-T-a | trigger | 27 | 03:698 | Reopen ticket 27 (trace restart / baggage off) when any ticket needs a correlation field | live | |
| 04-A-1 | adr-new | 04 | 04:89–99, 04:308–309 | `SameSite` resolution: `None; Secure` deviation from §3.5's `Lax` mandate, or shared-site topology that avoids it | live (resolution owned by 08/20) | adr |
| 04-A-2 | adr-new | 04 | 04:171–176, 04:309 | BCrypt over admin recipe's hard-coded Argon2id; cite Q12's cost ≥12 floor. Overlaps 02-A-3 | live | adr |
| 04-A-3 | adr-new | 04 | 04:123–134, 04:309–310 | Hashed single-use reset token over §3.5 / admin recipe's generated plaintext password; standard contradicts itself | live | adr |
| 04-A-4 | adr-new | 04 | 04:199–203, 04:310 | Add per-IP rate limiting against Q16's guidance for internal apps behind NAT (PRD mandates dual limiting) | live | borderline |
| 04-A-5 | adr-new | 04 | 04:241–243, 04:311 | Flyway where Q3's bootstrap options say Liquibase: "explicit line in the ADR" rather than silent substitution | live | register |
| 04-R-1 | register | 04 | 04:308 | Account hygiene jobs deferral (already on the map), cited as baseline; duplicates 01-R-2 | live | |
| 04-R-2 | register | 04 | 04:322, 04:327–333 | Buildability defect: headers recipe `PathRequest.toH2Console()` needs `spring-boot-h2console` on Boot 4 | live | |
| 04-R-3 | register | 04 | 04:334–341 | Buildability defect: recipe's Bucket4j `Limit.of(...)` API does not exist; library unnamed and unpinned | live | |
| 04-R-4 | register | 04 | 04:342–346 | Buildability defect: starter-internal types presented as API (`UserManagementRepositoryHandler`, `PasswordUtil`, etc.); behaviour prescriptive, code not adoptable | live | |
| 04-R-5 | register | 04 | 04:347–353 | Standards defect: `hasRole` vs `hasAuthority` mismatch across recipes; Q18 contradicts the RBAC recipe | live | |
| 04-R-6 | register | 04 | 04:354–356 | Recipe defect: `@Autowired` fields on a `new`-constructed `ChangeCurrentUserPasswordCommand` will be null | live | |
| 04-R-7 | register | 04 | 04:360–365 | Config namespace incoherent across recipes (two squat in `spring.*`); declare one prefix and note the deviation | live | |
| 04-R-8 | register | 04 | 04:366–371 | Two incompatible `PasswordChangeFilter` implementations; self-service one deadlocks `/csrf`. Adopt the admin recipe's | live | |
| 04-R-9 | register | 04 | 04:542–545 | RBAC recipe defect: `url-guards` registered before `whitelist` under first-match-wins; register whitelist first | live | |
| 04-R-10 | register | 04 | 04:508–511 | Record API-side CSP as defence-in-depth, not the XSS control, so a green `curl -I` isn't read as compliance | live | |
| 07-M-a | adr-amend | 07 | 05:1133–1144 | BCrypt ADR wording (owner 07; 02-A-3/04-A-2): encode/verify asymmetry is CVE-2025-22228's fix; 22234 is its consequence | live | adr |
| 27-T-b | trigger | 27 | 05:785–786 | CORS tripwire: adding `traceparent`/`tracestate`/`b3`/`X-B3-*`/`baggage` to `allowedHeaders` reopens 27; revisit trace-restart ADR first | live | |
| 06-A-1 | adr-new | 06 | 06:113–116, 06:335 | RFC 9457 envelope, deviating from login recipe's `sendError`/`BasicErrorController` posture | live | adr |
| 06-A-2 | adr-new | 06 | 06:303–307, 06:336 | `sendError` prohibited; single `ProblemDetailWriter` across all four producers | live | borderline |
| 06-A-3 | adr-new | 06 | 06:216–232, 06:337–338 | Self-registration uniform 202 + activation token, deviating from PRD Story 1; `USER_EXISTS` admin-only | live; amended by 06-M-1..3 | adr |
| 06-A-4 | adr-new | 06 | 06:258–269, 06:339 | MFA `detail` prose-matching ("User Details not found.") dropped; clients branch on `code` | live | borderline |
| 06-A-5 | adr-new | 06 | 06:148–159, 06:340–341 | `account locked` reclassified as log reason only, never a wire code, resolving §3.2's self-cancelling bullet | live | adr |
| 06-M-1 | adr-amend | 06 | 06:360–362 | Target 06-A-3 (from 11): `USER_EXISTS` also covers tombstone hits; uniform 202 covers live and tombstone duplicates | live | adr |
| 06-M-2 | adr-amend | 06 | 06:371–376 | Target 06-A-3 (from 10): self-registration username collision reported specifically as `VALIDATION_FAILED`/`USERNAME_UNAVAILABLE`; email existence never observable | live | adr |
| 06-M-3 | adr-amend | 06 | 06:381–387 | Target 06-A-3 (from 10): credential moved to activation to delete BCrypt timing oracle on uniform-202 path (ASVS 6.3.8) | live | adr |
| 06-R-1 | register | 06 | 06:427–431 | Deviation (from 23): 412 for missing factor is off-label per RFC 9110 §15.5.13; kept for envelope consistency | live | |
| 06-R-2 | register | 06 | 06:427–431 | Deviation (from 23): 422 `FACTOR_ENROLMENT_REQUIRED` is properly 403; kept; RFC 9470 named but not authoritative | live | |
| 13-R-b | register | 13 | 06:446–448 | Residual: presence/absence of `user.id` on failed-login rows is a log-reader oracle, unavoidable under include-when-resolved | live | |
| 16-R-a | register | 16 | 06:481 | N/A: Standard §5:473 (username change / lock via generic update) N/A by construction; no generic update endpoint | live | |

No `glossary` or `handover` items were found in 01–06. None of these files has a `### Handover items (ticket 25)`
heading or a `CONTEXT.md` glossary section. No `adr-rejected` candidates are explicit. 02-R-3 ("needs no ADR
justification") is the closest, and I recorded it as a register row.

## 2. Per-ticket tally (stated vs enumerated)

| ticket (as owner) | kind | stated | enumerated | note |
|---|---|---|---|---|
| 01 | register | none stated as a count (124 rows describes the register's size, not items owed) | 9 | 01-R-4 superseded by ticket 19 |
| 01 | adr-* | none | 0 | pm-6 wants an `/adr` directory (01:241), but that is the container, not an item |
| 02 | adr-new | none stated; verdict table routes items 1, 5, 7 to 17 (02:68, 02:72, 02:74) | 3 | **Mismatch in routing:** row 1 → 17 is register wording (02-R-1), not an ADR. 02-A-1 (quotas) is not routed to 17 in the table |
| 02 | adr-amend (owner 07) | none | 1 | |
| 02 | register | none | 4 | |
| 03 | adr-new/reversed | 512 names 3 ADRs (C1, C6, C9) plus the "session.hash decision"; 261 says "Raise as an ADR" | 4 (A-1, A-2, A-3, X-1) | Matches if session.hash counts as an ADR. C1 is withdrawn, so 3 ADRs are live |
| 03 | register | 512 lists 10 open-item groups (C1, C3, C6, C7, C8, C9, session.hash, retention, pinning, retest) | 14 register + 4 ADR | **Mismatch by granularity:** C7 split into 5 rows (R-4..R-8). 136–140 adds 3 gap rows (R-1..R-3) not in 512's list. 11's amendment adds R-11 |
| 04 | adr-new | "inherits five new entries" (04:308) | 5 | Matches. 04-A-5 (Flyway) reads as a register line, not an ADR |
| 04 | register | "Five items will not compile … Two more" (04:324, 04:358) = 7 | 7 (R-2..R-8) + 3 others | Matches for the risk register. R-1, R-9 and R-10 are extra, found in prose |
| 05 | any | none | 0 owned by 05 | Everything in 05 that is owed to 17 belongs to other tickets (07-M-a, 27-T-b) |
| 06 | adr-new | "ADRs owed by this ticket (5)" (06:333) | 5 | Matches. The inline "ADR owed" markers at 06:115, 06:230, 06:269, 06:306 map onto list items 1, 3, 4, 2. Item 5 has no inline marker |
| 06 | adr-amend | none | 3 | All three amend 06-A-3 in place. No amendment updates the "(5)" list |
| 06 | register | none | 2 | Both deviations were added by ticket 23's amendment |
| 11 (found in 03) | register | 03:531 "three fields" | 2 (11-R-a, 11-R-b) + 03-R-11 | **Mismatch:** 03:531 counts `user.target.id`/`roles`/`unlockReason` as three. `count` (03:533) is a fourth, left undecided. 03:608 says the package "shrinks from five to three", so it counts five by including `source.ip_hash` |
| 13 (found in 03, 06) | adr-new / register | 03:608–612: three genuinely custom fields | 1 ADR, 2 register | Of 13's three custom fields, 03 only carries `source.ip_hash` as a 13-owned row. `unlock_reason` and `count` are recorded under 11 |
| 16 (found in 06) | register | none | 1 | |
| 27 (found in 03, 05) | trigger | none | 2 | |

## 3. Cross-file effects (for the aggregator)

- **03:577–583 (from 13):** withdraws 03's C1 `source.ip` ADR (03-X-1) and replaces it with a narrower 13-owned ADR
  (13-A-a). Ticket 13's own ADR list should contain this ADR. If it also carries C1, drop the C1 entry.
- **03:585–588 (from 13):** rejects the field name `source.ip.hash` in favour of `source.ip_hash`. Update any 09 or 13
  register row that uses the old spelling.
- **03:590–595 (from 13):** reverses C3. Lockout logs at WARN with `error_code` aligned to 401. Any 09 or 13 item
  that says ERROR/423 is superseded.
- **03:608–616 (from 13):** renames ticket 11's `user.target.unlockReason` to `unlock_reason`. It also reclassifies
  `user.target.id`/`roles` as valid ECS, and declares `unlock_reason`, `count` and `source.ip_hash` custom. This
  affects 11's register/ADR items.
- **03:518–537 (from 11):** adds `user.target.roles`, `unlockReason` and `count` to 03's extension package, which
  widens 03-R-10.
- **03:559 (from 12):** withdraws the "separate `uuid` column" implication. Not a ticket 17 item, but any 12 register
  row citing 03:391 is affected.
- **03:698 (from 27):** reopening trigger owned by 27 (27-T-a).
- **05:785–786:** CORS tripwire that reopens 27 and names 27's "trace restart ADR" (27-T-b). Check that it is on
  27's trigger list.
- **05:1133–1144 (from 11):** corrects the CVE attribution to be carried into 07's BCrypt ADR wording (07-M-a). The
  same ADR is claimed at 02-A-3 and 04-A-2, so dedupe to a single ADR owned by 07.
- **05:1149–1157 (from 11):** CVE-2026-22753 produces an 11-owned standing rule prohibiting `spring.mvc.servlet.path`.
  CVE-2026-22746 goes to 09. Neither is framed as a 17 item here. Check 11 and 09 for an ADR or register row.
- **05:1182–1185 (from 09):** a deliberate 429 exemption from 06's uniform-401 rule inside the failure handler. It
  is not framed as an ADR or register row. The aggregator should check whether 09 registers it.
- **05:1206 (from 16):** ticket 29 now owns `cleanup-cron`, superseding 05:299–300. Not a 17 item.
- **06:17 (ticket 19 inherited):** supersedes 01-R-4 (the MFA accept-FAIL deferral).
- **06:381–387 (from 10), 06:360–362 (from 11):** amend 06-A-3. Ticket 10's activation sub-flow and 11's tombstone
  rule should cite 06-A-3 rather than raise their own ADR.
- **06:427–431 (from 23):** two status deviations are recorded against 06 (06-R-1, 06-R-2). If ticket 23 also lists
  them, dedupe.
- **06:446–448 (from 13):** residual owned by 13 (13-R-b).
- **06:481 (from 16):** N/A row owned by 16 (16-R-a).
- **06:490–494 (from 32):** 06's enum table misses `FACTOR_ALREADY_ENROLLED` (409) and `FACTOR_DISABLED` (423). The
  table cites 14:637 as the amendment ticket 14 owes. This is a lost hand-off, not a 17 item.
- **Overlap to dedupe:** 02-A-3, 04-A-2, 02-M-1 and 07-M-a are one BCrypt ADR, with 07 as the likely owner.
  02-R-3 and 04-A-1 both concern `SameSite`: 02 says Strict needs no ADR, 04 says `None` would need one. Whichever
  resolution 08/20 chose decides which survives. 01-R-2 and 04-R-1 are the same hygiene deferral.

Not counted, but noted in case the aggregator wants them: 05:331–332 (diff Spring Session DDL on every version bump,
an upgrade-checklist item), 05:248 (keep JDK session serialization), 04:177–179 (admin recipe composition regex is
narrower than §3.5, a defect outside the risk register), and 06:29–31 (MFA enumeration carve-out "written down", which
06:271–273 satisfies).
