# Inventory part G — tickets 17, 27, 28, 29, 30, 31, 32

Scope: `issues/17-*.md` (442 lines), `27-*` (343), `28-*` (466), `29-*` (458), `30-*` (225), `31-*` (460), `32-*` (178),
each read in full. Line numbers are as the files stand now.

Conventions used here:
- Ticket 17's inherited sections are recorded under the ticket that owes the item (06, 15, 16, 25, 32), with the 17 line as source.
  Where 17 only restates a group ticket's own list (28, 29, 30, 31 sections at 17:320–399), the row cites the owning ticket's
  line first and the 17 restatement second; it is **not** a second row.
- Ticket 17's pre-resolution sketches (Deliverable 1 known entries, Deliverable 2 seven-item sketch, Deliverable 3 glossary list)
  are owner 17. Their status says which resolved ticket now covers them; "outside group, unverified" means I did not open that file.
- `adr-amend` rows are owned by the ticket making the amendment; the target ADR and its owner are named in the gist.
- `adr-rejected` rows carry shape `—`: the ticket already applied the test and my read agrees.
- Kind abbreviations: A new, M amend, X reversed, J rejected, R register, G glossary, T trigger, H handover.

## 1. Items

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 17-R-1 | register | 17 | 17:23–25 | Sketch: account-hygiene scheduled jobs (90-day inactivity, 180-day role revocation, ShedLock) deferred; largest deferral | live; covered by 16-R-1 (17:409) | |
| 17-R-2 | register | 17 | 17:26 | Sketch: BCrypt instead of preferred Argon2id/scrypt (also sketch ADR 17-A-3) | live; ticket 02 §7 (02:400) appears to cover, outside group, unverified | |
| 17-R-3 | register | 17 | 17:27–28 | Sketch, conditional: password history, composition or lockout duration where currency research put standard behind current guidance | live; outside group (02/07/09), unverified | |
| 17-R-4 | register | 17 | 17:29 | Sketch, conditional: role-based authorization matrix, if admin-module ticket deferred it | live; outside group (11), unverified | |
| 17-R-5 | register | 17 | 17:30–31 | Sketch, conditional: durable 90-day audit retention if log lines cannot satisfy it | live; covered by 15-R-5 (17:259) and 16-R-2 (17:410) | |
| 17-R-6 | register | 17 | 17:32–33 | Sketch: OWASP Dependency-Check as Maven verify gate, not CI gate | live; covered by 16-R-4 (17:412) | |
| 17-R-7 | register | 17 | 17:34 | Sketch: no TLS on login endpoint for local development | live; covered by 15-R-1 (17:256) and 16-R-6 (17:415) | |
| 17-R-8 | register | 17 | 17:38–40 | Sketch: registration gives no clear conflict error; PRD Story 1 AC3 not met, stated in exactly those terms | live; covered by 06-A-3 (17:90–91) and 15-R-7 (17:261) | |
| 17-R-9 | register | 17 | 17:41 | Sketch, conditional: PRD lockout duration 15→20 minutes | live; outside group (09), unverified | |
| 17-R-10 | register | 17 | 17:42–44 | Sketch: seven beyond-PRD additions built (session limit, timeouts, history, password change, verification, tombstones, admin unlock); may be seven rows | live; no group ticket covers | |
| 17-R-11 | register | 17 | 17:46 | Sketch: accepted risks carried from threat model | live; covered by ticket 15 inherited section (17:231–316), rows 15-R-* | |
| 17-A-1 | adr-new | 17 | 17:61–62 | Sketch: session cookies over JWT, incl. JWT logout needing a blacklist | live; outside group (08), unverified | adr |
| 17-A-2 | adr-new | 17 | 17:63 | Sketch: Spring Session JDBC, driven by invalidate-all-sessions requirement | live; outside group (08), unverified | adr |
| 17-A-3 | adr-new | 17 | 17:64–65 | Sketch: BCrypt over Argon2id, deviation from standard's preference | live; 02:400 says "the ADR should say so", outside group, unverified | adr |
| 17-A-4 | adr-new | 17 | 17:66–67 | Sketch: dual rate limiting resolving PRD/standard contradiction | live; outside group (09), unverified | adr |
| 17-A-5 | adr-new | 17 | 17:68 | Sketch: enumeration-resistant registration at cost of PRD acceptance criterion | live; covered by 06-A-3 (17:90–91) | adr |
| 17-A-6 | adr-new | 17 | 17:69 | Sketch: Flyway over `ddl-auto`, vendor-neutral SQL against H2 | live; outside group (12; 04:242 notes Liquibase line), unverified | borderline |
| 17-A-7 | adr-new | 17 | 17:70 | Sketch: session-bound synchronizer CSRF token and its same-site deployment constraint | live; outside group (08/20), unverified | adr |
| 17-G-1 | glossary | 17 | 17:76 | Managed user | live | |
| 17-G-2 | glossary | 17 | 17:76 | Current user | live | |
| 17-G-3 | glossary | 17 | 17:76 | Role definition | live | |
| 17-G-4 | glossary | 17 | 17:76–77 | Authorization matrix | live | |
| 17-G-5 | glossary | 17 | 17:77 | Tombstone | live | |
| 17-G-6 | glossary | 17 | 17:77 | Failed login counter | live | |
| 17-G-7 | glossary | 17 | 17:77 | Account lockout | live | |
| 17-G-8 | glossary | 17 | 17:77 | Absolute session timeout | live | |
| 17-G-9 | glossary | 17 | 17:78 | Unverified vs disabled ("and whatever else got pinned down" — open-ended) | live | |
| 06-A-1 | adr-new | 06 | 17:85–87 | RFC 9457 envelope, deviating from login recipe's `sendError`/`BasicErrorController` posture | live | adr |
| 06-A-2 | adr-new | 06 | 17:88–89 | `sendError` prohibited; one `ProblemDetailWriter` across four producers; deviation from prescribed failure handler | live | borderline |
| 06-A-3 | adr-new | 06 | 17:90–91 | Self-registration returns uniform 202 plus activation token; deviates from PRD Story 1; `USER_EXISTS` admin-only | live | adr |
| 06-A-4 | adr-new | 06 | 17:92–93 | MFA `detail` prose-matching dropped; clients branch on `code`; deviation from MFA_Core §3.2 | live | register |
| 06-A-5 | adr-new | 06 | 17:94–96 | `account locked` is a log reason, never a wire code; resolves §3.2's self-cancelling bullet | live | borderline |
| 06-R-1 | register | 06 | 17:98–100 | Register points at `docs/api/error-contract.md` and closed `code` enum, not restating contract (artifact pointer) | live | |
| 25-R-1 | register | 25 | 17:218–223 | Adopted-reading note: NIST §4.2.1 Reading B (~70%); alternative would have given shell break-glass a compliance route | live | |
| 25-R-2 | register | 25 | 17:225–227 | Citation hygiene for every row: no RFC 2119 modals for ASVS; no cross-walk of V6.5 onto 63B-4 | live; rule on all rows, not a row | |
| 25-R-3 | register | 25 | 17:211–216 | Drift family 6.3.1, 6.1.2, 6.2.11, 16.2.3, 16.3.3; 13.2.4/13.2.5 cited as companions only | live; content rule, not a row | |
| 25-R-4 | register | 25 | 17:192–195 | Constraint: extractor in Maven `verify`, fails on committed-vs-regenerated drift | live; structural, not a row | |
| 25-R-5 | register | 25 | 17:196–198 | Constraint: compliance rendering carries per-row anchor into operational procedure | live; structural, not a row | |
| 25-R-6 | register | 25 | 17:199–205 | Constraint: fixed row schema (responsibility/status/priority, acceptance check or sentinel, deployment-assumption header) | live; structural, not a row | |
| 15-R-1 | register | 15 | 17:256 | Local HTTP stands: nobody nominated to terminate TLS (as-10, dp-3) | live | |
| 15-R-2 | register | 15 | 17:257 | Stubbed email, row 1: total takeover from log read in `dev` | live | |
| 15-R-3 | register | 15 | 17:257 | Stubbed email, row 2: no channel at all outside `dev` | live | |
| 15-R-4 | register | 15 | 17:258 | "No MFA" accepted risk | withdrawn by 17:258 (ticket 19 put admin TOTP in scope; regular users are out-of-scope, not residual) | |
| 15-R-5 | register | 15 | 17:259 | No durable audit store: ASVS 16.4.2 (L2) F and 16.4.3 (L2) F; V16 has no L1; possibly two rows | live | |
| 15-R-6 | register | 15 | 17:260 | Single-instance in-memory rate limiting; TM-07 adds unbounded aggregate CPU/thread occupancy across sources | live; source-rotation pricing re-priced by 31 §8 (31:307) | |
| 15-R-7 | register | 15 | 17:261 | Enumeration narrowed to `USERNAME_UNAVAILABLE` at registration; ASVS 6.3.8 (L3) deliberately failed; 5/min per IP | live | |
| 15-R-8 | register | 15 | 17:263–264 | TM-08: flat ADMIN, no separation of duties | live; widened by 15-R-8a | |
| 15-R-8a | register | 15 | 30:123–129, 30:189; 17:394–398 | TM-08 widened: at exactly two admins A can fully take over B (password plus factor reset); detectors named | live | |
| 15-R-9 | register | 15 | 17:264 | TM-09: tombstone's missing role | live | |
| 15-R-10 | register | 15 | 17:264–267 | Mass permanent-lockout residual carried unchanged from 09 §R.2 (possible duplicate of a 09 register row) | live; its 09 §R.2 figures corrected by 31:175–176 | |
| 15-A-1 | adr-new | 15 | 17:276–280 | Threat model is an artefact of the map, not the build; inherited threats marked Mitigated | live | borderline |
| 15-A-2 | adr-new | 15 | 17:281–284 | `daily` is attacker-influenceable; audit volume bounded by rule, not table (per ticket 26) | live | adr |
| 15-A-3 | adr-new | 15 | 17:285–291 | Inbound trace context, per ticket 27 | superseded by 27:298 (same ADR, now 27-A-1) | adr |
| 15-A-4 | adr-new | 15 | 17:292–293 | The rebinding token's channel, per ticket 28 | superseded by 28:423 (token removed; channel "dissolved", now 28-A-2) | adr |
| 15-M-1 | adr-amend | 15 | 17:297–299 | Target: ticket 23's factor-composition ADR gains layering asymmetry (factor gate single-layered, role gate double) | live | adr |
| 15-M-2 | adr-amend | 15 | 17:300–302 | Target: ticket 11's two-admin-invariant ADR gains third/fourth non-mutation channels; "guarded on mutation, monitored everywhere" | live | adr |
| 15-J-1 | adr-rejected | 15 | 17:304–306 | ADR for `GET /api/hello` matrix row (TM-05); fails all three parts; one-line amendment to 11 | live (rejection) | — |
| 15-T-1 | trigger | 15 | 17:310–313 | Mail transport entering scope: bad half, §4.2.2.2 recovery codes with `dev` stub logging inverts containment to total | live; also amended by 28-T-5, 31-T-5 | |
| 15-T-2 | trigger | 15 | 17:314–316 | Any inline script in production document: also invalidates ticket 23's re-auth-on-enrolment decline | live | |
| 16-R-1 | register | 16 | 17:409 | Not built: §5 hygiene rows (90-day inactivity, 180-day role revocation, scheduler serialisation, batch-job session kill); possibly four rows | live | |
| 16-R-2 | register | 16 | 17:410 | Not built: 90-day audit retention | live | |
| 16-R-3 | register | 16 | 17:411 | Not built: owner notifications | live | |
| 16-R-4 | register | 16 | 17:412 | Not built: Dependency-Check as CI gate (bound to `verify`) | live | |
| 16-R-5 | register | 16 | 17:414 | Fidelity: H2 locking and types | live | |
| 16-R-6 | register | 16 | 17:415 | Fidelity: TLS, HSTS behaviour, `__Host-` over HTTPS | live | |
| 16-R-7 | register | 16 | 17:416 | Fidelity: multi-instance consistency (§5:453) | live | |
| 16-R-8 | register | 16 | 17:417 | Fidelity: deployer's launch command | live | |
| 16-R-9 | register | 16 | 17:418 | Fidelity: wall-clock timing | live | |
| 16-R-10 | register | 16 | 17:419 | Fidelity: WebKit/Safari | live | |
| 16-R-11 | register | 16 | 17:420 | ASVS 6.3.8 (L3) "verified by mechanism": `matches()` call count, not timing | live | |
| 16-R-12 | register | 16 | 17:421 | Failsafe/Surefire pin 3.6.0 residual: `-DskipITs`, `-Dmaven.test.skip` accepted bypasses | live | |
| 16-R-13 | register | 16 | 17:442; 32:135 | Runner single-use-token row (16:205, ASVS 6.4.1 L1); was a test row, would have needed a register row | withdrawn by 17:442 (ticket 28 removed token) | |
| 16-T-1 | trigger | 16 | 17:421 | Any Surefire/Failsafe version bump reopens the pin residual | live | |
| 27-A-1 | adr-new | 27 | 27:298–311; 17:287–291 | Restart inbound trace context at the application boundary; three options priced; wrapper and strip set; triggers in ADR | live | adr |
| 27-J-1 | adr-rejected | 27 | 27:315 | Baggage off: trivially reversible; amendment to 03 only | live (rejection) | — |
| 27-J-2 | adr-rejected | 27 | 27:316 | `correlation.id` dropped: unsurprising given 03:277; amendment to 03 only | live (rejection) | — |
| 27-G-1 | glossary | 27 | 27:320–322; 17:290–291 | Trace restart | live | |
| 27-T-1 | trigger | 27 | 27:306; 27:281 | Upstream tracing gateway introduced | live | |
| 27-T-2 | trigger | 27 | 27:307; 27:281–283 | SPA sending `traceparent` (tripwire at ticket 05 `allowedHeaders`) | live | |
| 27-T-3 | trigger | 27 | 27:308 | Outbound call to a traced service | live; duplicated by 32-T-2 | |
| 27-T-4 | trigger | 27 | 27:218–219 | Any ticket needing a correlation field (must keep caller-set values out of audit stream) | live | |
| 27-H-1 | handover | 27 | 27:271–283 | Application restarts every inbound trace; do not re-enable continuation by config; deployer/asserted-by-test/recommended | live; replaces 15's proxy item (15:330–334) | |
| 27-H-2 | handover | 27 | 27:284–294 | Keep trace headers out of Tomcat access log pattern; deployer/unmitigated/recommended | live | |
| 28-A-1 | adr-new | 28 | 28:422; 17:324 | Offline process model and planned outage, with AUTO_SERVER, loopback-listener and in-app rejections | live | adr |
| 28-A-2 | adr-new | 28 | 28:423; 17:324 | Credential in, nothing out; batch mints nothing; scoped 6.4.6 (L3) withdrawal | live | adr |
| 28-A-3 | adr-new | 28 | 28:424; 17:325 | Plan/apply digest: tuple, batch concatenation, `--operator` excluded as two-person capability | live | adr |
| 28-A-4 | adr-new | 28 | 28:425; 17:325 | Runner-mode preconditions: full refresh, `IFEXISTS`, no migration, no seeder, lazy appender | live | borderline |
| 28-A-5 | adr-new | 28 | 28:426; 17:325 | Audit shape: dry-run, intent, outcome rows; single batch row; `labels.operator_claimed_id` | live | borderline |
| 28-M-1 | adr-amend | 28 | 28:428–429; 17:326 | Target: ticket 09 §R.3 four-constraints ADR; constraints 1, 3, 4 superseded; 6.4.1 citation corrected | live | adr |
| 28-M-2 | adr-amend | 28 | 28:429; 17:326–327 | Target: ticket 24's eleventh prohibited-configuration entry, replaced (vacuous, 28:205–206) | live | borderline |
| 28-R-1 | register | 28 | 28:432–434; 17:329 | ASVS 6.4.6 (L3): scoped withdrawal of volunteered pass on runner path; state exactly, not "failed" (17:337–338) | live | |
| 28-R-2 | register | 28 | 28:435–438; 17:330 | PDPA trade: raw staff identifier retained ≥90 days, deliberately unresolvable | live | |
| 28-R-3 | register | 28 | 28:439; 17:331 | Stale-copy residual, `procedural` | live | |
| 28-R-4 | register | 28 | 28:440; 17:332 | Mass-lockout RTO uncosted (up to 240 sequential interactive runs) | live | |
| 28-R-5 | register | 28 | 28:441; 17:333 | Post-commit window, detectable by intent row, not closed | live | |
| 28-R-6 | register | 28 | 28:442; 17:334 | JDK-22 console behaviour change, bounded to one execution | live | |
| 28-R-7 | register | 28 | 28:443; 17:335 | ASVS 16.2.3 (L2): operator terminal added as documented destination | live | |
| 28-G-1 | glossary | 28 | 28:445–446 | Candidate "planned-outage recovery" (a description) | withdrawn by 28:445 (rejected candidate) | |
| 28-G-2 | glossary | 28 | 28:445–446 | Candidate "plan/apply" (borrowed practice) | withdrawn by 28:446 (rejected candidate) | |
| 28-T-1 | trigger | 28 | 28:384 | Rehearsal recurrence: any change to runner, password policy or bootstrap | live | |
| 28-T-2 | trigger | 28 | 28:385–386; 28:305–306 | Rehearsal recurrence: any JDK, Boot or H2 major upgrade (incl. JDK-22 console path) | live | |
| 28-T-3 | trigger | 28 | 28:386 | Rehearsal recurrence: deployer-set calendar ceiling | live | |
| 28-T-4 | trigger | 28 | 28:388–389; 30:144–145 | Red rehearsal grades ASVS 6.1.1 (L1) F and reopens ticket 28 | live | |
| 28-T-5 | trigger | 28 | 28:219–220 | Amends TM-13 mail-transport trigger: batch leg is a second, larger cause | live | |
| 28-H-1 | handover | 28 | 28:394–396 | Named, reachable runner operator with deploy-level access; sentinel proof | live | |
| 28-H-2 | handover | 28 | 28:397–399 | Recovery through runner is a planned outage: stop, dry-run, apply, restart | live | |
| 28-H-3 | handover | 28 | 28:400–402 | Use jar matching deployed version | live | |
| 28-H-4 | handover | 28 | 28:403–405 | Compare printed DB path, schema version, mtime before applying (stale-copy, register line 3) | live | |
| 28-H-5 | handover | 28 | 28:406–408 | Hand operator-set password to user out of band, not via audit stream | live | |
| 28-H-6 | handover | 28 | 28:409–412 | Host sudo/ssh trail is human attribution, retained ≥ audit file | live | |
| 28-H-7 | handover | 28 | 28:413–414 | Rehearse on §11 recurrence triggers | live | |
| 28-H-8 | handover | 28 | 28:415–417 | Plan capacity for mass-lockout event; costed RTO or signed acceptance | live | |
| 29-A-1 | adr-new | 29 | 29:402–403; 17:349 | Only one route creates anonymous sessions: CSRF repository wrapper, `loadDeferredToken` trap, load-bearing `getSession(true)` | live | adr |
| 29-A-2 | adr-new | 29 | 29:404–405; 17:349 | Anonymous expiry pinned at creation + W; deviation from 08:118–121; invalidate-don't-set; login reset | live | adr |
| 29-A-3 | adr-new | 29 | 29:406–407; 17:349 | Shedding on count line and N-scaled free-space reserve; rejected `k × F` latch; dwell; unevaluable-means-shed | live | adr |
| 29-A-4 | adr-new | 29 | 29:408; 17:354–355 | `N_max` count cap as deviation from ADR 3's rationale; measured-range argument and lever | live | adr |
| 29-A-5 | adr-new | 29 | 29:409 | Rejected recourse: trusted-range exemption, offline purge verb, 503 | live | borderline |
| 29-M-1 | adr-amend | 29 | 29:411; 29:239–240; 17:349 | Target: ticket 21's "declined session-row gauge" ADR, reversed on verified facts (kind also adr-reversed for 21) | live | adr |
| 29-G-1 | glossary | 29 | 29:414; 17:350 | Anonymous session | live | |
| 29-G-2 | glossary | 29 | 29:415; 17:350 | Pin / pinned expiry | live | |
| 29-G-3 | glossary | 29 | 29:416; 17:350 | Shedding / shed episode | live | |
| 29-G-4 | glossary | 29 | 29:417; 17:350 | Reserve | live | |
| 29-R-1 | register | 29 | 29:420 | 08:118–121 deviated (second anchor) | live | |
| 29-R-2 | register | 29 | 29:421 | `N_max` as login-denial primitive at ~196 sources, independent of disk size | live; re-framed as improvement by 29:442–446 / 31:311 | |
| 29-R-3 | register | 29 | 29:422 | `k = 1.5`, `b = 17 KB` planning values; DELETE peak unsampled | live | |
| 29-R-4 | register | 29 | 29:423 | §F.6 COUNT cost under concurrent writes unverified, bounded by `N_max` | live | |
| 29-R-5 | register | 29 | 29:424 | Mount-sharing detection informational only | live | |
| 29-T-1 | trigger | 29 | 29:427 | Measurement extending `b` past 100k rows (raises `N_max`) | live | |
| 29-T-2 | trigger | 29 | 29:428 | Any change setting `AUTH_INSTANT` or `PRINCIPAL_NAME` other than at password login | live | |
| 29-T-3 | trigger | 29 | 29:429 | H2 upgrade changing MVStore retention or compaction | live | |
| 29-T-4 | trigger | 29 | 29:456 | Any change to `spring.session.jdbc.cleanup-cron` (from ticket 16 amendment) | live | |
| 29-H-1 | handover | 29 | 29:358–368 | Size DB volume to §7 sizing line (~6.96 GB); shared/asserted-by-test/required | live | |
| 29-H-2 | handover | 29 | 29:369–381 | Shedding denies new logins; what clears it; edge per-source limit is the remedy | live; tightened by 29:447–448 and 31-H-2 | |
| 29-H-3 | handover | 29 | 29:382–389 | Keep H2 data dir as `app.db.data-dir`, under datasource URL; deployer/enforced/required | live | |
| 30-A-1 | adr-new | 30 | 30:188; 30:102–118; 17:386–393 | Factor reset exempt from two-admin count; who-can-reverse argument; go-live-at-three rejected | live | adr |
| 30-M-1 | adr-amend | 30 | 30:185–186; 17:372–376 | Target: ADR 12 (ticket 11 bootstrap): one admin seeded, conditional on second enrolled admin before go-live | live | adr |
| 30-M-2 | adr-amend | 30 | 30:187; 30:133–145; 17:377–385 | Target: ADR 13 (owner 11 or 09, not established in group files): three recovery routes on `authenticable` | live | adr |
| 30-H-1 | handover | 30 | 30:168–171 | Two enrolled admins before go-live; pending invites don't count; not a gate | live | |
| 30-H-2 | handover | 30 | 30:172–174 | Promote before demote from day one at two admins | live | |
| 30-H-3 | handover | 30 | 30:175–178 | Enable OTLP export and alert rule "authenticable admins < 2" | live | |
| 30-H-4 | handover | 30 | 30:179–181 | Route 2 waits on route 1: wait out lockout before runner | live | |
| 31-A-1 | adr-new | 31 | 31:410–411; 17:361 | /64 source key via one property; RIPE/APNIC/VPC trade; bytes-not-strings; one resolver with raw-path ban | live | adr |
| 31-A-2 | adr-new | 31 | 31:412–413; 17:361 | `source.ip_hash` hashes source key: pinned input, IPv4 hash change, prefix-change correlation break, exact join | live | borderline |
| 31-M-1 | adr-amend | 31 | 31:416–417 | Target: ticket 09 §R.6 ADR; honest ceiling re-priced (~100× per bucket); reading (B) with expiry pin | live | adr |
| 31-M-2 | adr-amend | 31 | 31:418 | Target: ticket 09 §R.5 ladder ADR; fencepost corrected (840/260/580); startup floor | live | adr |
| 31-M-3 | adr-amend | 31 | 31:419 | Target: ticket 13 ADR, `source.ip_hash` input | live | borderline |
| 31-G-1 | glossary | 31 | 31:422–424; 17:362 | Source key | live | |
| 31-R-1 | register | 31 | 31:427–431 | Second /56 network key for third axis declined (also reads as adr-rejected) | live | |
| 31-R-2 | register | 31 | 31:432–433 | Global lockout-rate cap declined (also reads as adr-rejected) | live | |
| 31-R-3 | register | 31 | 31:434–435; 31:186–188; 17:363 | Ladder constants as a named owed input from ticket 09 (keys at 09:1219–1220) | withdrawn by 17:422 (keys now exist; 32:137) | |
| 31-R-4 | register | 31 | 31:436 | /64 stops a host, not a subscriber, VPC or /48 holder | live | |
| 31-R-5 | register | 31 | 31:437 | Teredo and 6to4 granularity; Teredo layout UNVERIFIED | live | |
| 31-R-6 | register | 31 | 31:438 | Dual-stack hosts get two budgets | live | |
| 31-R-7 | register | 31 | 31:439 | No host-level forensics inside one /64 | live | |
| 31-R-8 | register | 31 | 31:440 | `unparseable` bucket becomes whole-internet bucket under total proxy misconfiguration | live | |
| 31-R-9 | register | 31 | 31:441 | Singapore ISP delegation sizes UNVERIFIED | live | |
| 31-R-10 | register | 31 | 31:442 | R.6 reading (B): full set refuses non-members on that egress ~1 h after fifth first-lock | live | |
| 31-R-11 | register | 31 | 31:443 | `SourceKeyAuthenticationDetails.toString()` in 7.1.1 unverified at source, settled by test | live | |
| 31-T-1 | trigger | 31 | 31:446–447 | Change to 09 ladder constants, lock threshold, cap or alert threshold rejected by startup floor | live | |
| 31-T-2 | trigger | 31 | 31:448 | Change to `k`, or `P` growing well past 100 | live | |
| 31-T-3 | trigger | 31 | 31:449 | User population known to sit on /56s | live | |
| 31-T-4 | trigger | 31 | 31:450 | APNIC adopts `prefixlen:`, or RFC 9977 feed for deployment's ranges | live | |
| 31-T-5 | trigger | 31 | 31:451; 31:282 | Mail transport landing (route (c) real; deploy access no longer needed per disable) | live | |
| 31-H-1 | handover | 31 | 31:351–359 | Declare whether public hostname publishes AAAA | live | |
| 31-H-2 | handover | 31 | 31:360–369 | Edge per-source limit aggregates IPv6 at chosen prefix; tightens 26:546, 29:372 | live | |
| 31-H-3 | handover | 31 | 31:370–380 | Edge access logs with raw addresses: ≥9.7 h, bounded max (30 days planning) | live | |
| 31-H-4 | handover | 31 | 31:381–393 | Runbook for lockout-cap alert warning window; routes (a)/(b)/(c); key holder; `--rebind` | live | |
| 31-H-5 | handover | 31 | 31:395–401 | Trusted proxy writes IP literals in `X-Forwarded-For`; alert on unparseable counter | live | |
| 32-R-1 | register | 32 | 17:432; 32:84 | Std §5:452 redemption half: reset-confirm limited per IP only; deviation | live | |
| 32-R-2 | register | 32 | 17:433 | Std §5:472: admin create issues invite token, no forced-change flag or 30-day grace; deviation | live | |
| 32-R-3 | register | 32 | 17:434 | Std §5:473: N/A, no generic update or lock route | live | |
| 32-R-4 | register | 32 | 17:435 | Std §5:494 batch half: N/A, batch reset declined | live | |
| 32-R-5 | register | 32 | 17:436 | MFA_Core §5.1 PIN tests (five items): N/A, only TOTP built; possibly five rows | live | |
| 32-R-6 | register | 32 | 17:437 | Logging §5:352–353: async trace/MDC propagation N/A (nothing `@Async`) | live | |
| 32-R-7 | register | 32 | 17:438 | Logging §5:355: outbound correlation-ID injection N/A (no outbound calls) | live | |
| 32-R-8 | register | 32 | 17:439 | Logging §5:358: no general request log built; access log off | live | |
| 32-R-9 | register | 32 | 17:440 | Logging §5:374: logging under load not exercised; fidelity item | live | |
| 32-T-1 | trigger | 32 | 17:437 | An executor is added (reopens 32-R-6) | live | |
| 32-T-2 | trigger | 32 | 17:438 | An outbound call (reopens 32-R-7; same event as 27-T-3) | live | |

## 2. Per-ticket tally (stated vs enumerated)

| owner | kind | stated | enumerated | note |
|---|---|---|---|---|
| 17 | adr-new (sketch) | 7 (17:53) | 7 | match; 17:53 itself says 7 is not the population |
| 17 | register (sketch) | none | 11 | no stated count; 17-R-10 bundles seven additions |
| 17 | glossary | none | 9 | list is open-ended ("whatever else", 17:78) |
| 06 | adr-new | 5 (17:83) | 5 | match |
| 06 | register | 1 artifact pointer (17:80) | 1 | match |
| 25 | register | 47 rows (17:184, 17:207) | 6 | **mismatch**: the 47 rows live in ticket 25's table (outside group); the 6 here are notes/constraints on the register, not rows |
| 15 | adr-new | 4 (17:269) | 4 | match; 2 of 4 now superseded (27, 28) |
| 15 | adr-amend | 2 (17:269) | 2 | match |
| 15 | adr-rejected | none | 1 | — |
| 15 | register | 6 named going in + 3 added (17:251, 17:263) | 11 | **mismatch** by design: stubbed email split into two rows, "No MFA" withdrawn, plus 15-R-8a (30's widening) |
| 15 | trigger | 2 (17:308) | 2 | match |
| 15 | handover | 6 (17:244) | 0 | **mismatch**: rows live in ticket 15's `## Answer` (outside group) |
| 16 | register | none | 13 | 12 live, 1 withdrawn (16-R-13) |
| 16 | trigger | none | 1 | — |
| 27 | adr-new | 1 (27:298; 17:285) | 1 | match |
| 27 | adr-rejected | none | 2 | — |
| 27 | glossary | 1 (27:318; 17:290) | 1 | match |
| 27 | register | 0 (27:324; 17:291) | 0 | match |
| 27 | trigger | 3 in ADR (27:304–308) | 4 | **mismatch**: 27:218 adds a fourth (correlation-field) trigger outside the ADR list |
| 27 | handover | none | 2 | — |
| 28 | adr-new | 5 (28:421; 17:324) | 5 | match |
| 28 | adr-amend | 2 (28:428; 17:326) | 2 | match |
| 28 | register | 7 (28:431; 17:328) | 7 | match |
| 28 | glossary | 0 (28:445) | 0 live, 2 withdrawn | match (two rejected candidates recorded) |
| 28 | trigger | none | 5 | — |
| 28 | handover | none | 8 | — |
| 29 | adr-new | 5 (29:401; 17:349) | 5 | match |
| 29 | adr-amend | 1 (29:411; 17:349) | 1 | match |
| 29 | glossary | 4 (17:350) | 4 | match |
| 29 | register | 5 (17:351) | 5 | match |
| 29 | trigger | 3 (17:352) | 4 | **mismatch**: ticket 16's amendment (29:456) adds the cleanup-cron trigger after 29 §11 was written |
| 29 | handover | none | 3 | — |
| 30 | adr-new | 1 (30:188; 17:369) | 1 | match |
| 30 | adr-amend | 2 (30:185–187; 17:369) | 2 | match |
| 30 | register | 1 change (30:189; 17:369) | 1 | match, but owned by 15 (15-R-8a), not 30 |
| 30 | glossary | 0 (30:190; 17:399) | 0 | match |
| 30 | handover | none | 4 | — |
| 31 | adr-new | 2 (31:409; 17:361) | 2 | match |
| 31 | adr-amend | 3 (31:415; 17:361) | 3 | match |
| 31 | glossary | 1 (31:421; 17:362) | 1 | match |
| 31 | register | 11 (17:362) | 11 | count matches; **live is 10**, since 31-R-3 was withdrawn by 17:422 |
| 31 | trigger | 5 (17:363) | 5 | match |
| 31 | handover | none | 5 | — |
| 32 | register | 9 (32:84; 17:430) | 9 | match; 32-R-5 bundles five PIN tests |
| 32 | trigger | none | 2 | 32-T-2 duplicates 27-T-3 |

Group totals: adr-new 30 (2 superseded: 15-A-3, 15-A-4; 7 are 17's pre-resolution sketch), adr-amend 10, adr-reversed 0
(29-M-1 is in effect a reversal of 21's ADR), adr-rejected 3, register 74 (3 withdrawn: 15-R-4, 16-R-13, 31-R-3; 11 are 17's
sketch; 6 are 25's structural notes, not rows), glossary 17 (2 withdrawn candidates), trigger 23, handover 22. Total 179 rows.

## 3. Cross-file effects (for the aggregator)

1. **15-A-3 → 27-A-1** (27:298; 17:287): ticket 15's ADR 3 is the same file as 27's one ADR. Count once.
2. **15-A-4 → 28-A-2** (28:423; 28:205): "rebinding token's channel" has no subject; the token was removed.
3. **Ticket 15 handover "upstream proxy must strip or regenerate `traceparent`"** (15:330–334) is replaced by 27-H-1 (27:273–275).
4. **15-R-4 "No MFA"** withdrawn (17:258).
5. **15-R-8 TM-08** widened by ticket 30 (30:123–129, 30:189; 17:394–398).
6. **Ticket 09 §R.3 four-constraints ADR**: constraints 1, 3, 4 superseded, 6.4.1/6.4.6 citation corrected (28:144–148, 28:428–429, 28:450).
7. **Ticket 24's eleventh prohibited-configuration entry** replaced (28:205–206, 28:429); 24 also gains four prohibitions and three validator entries (28:458).
8. **Ticket 25's TM-12 row** edited in place: now "no secret is emitted", `asserted-by-test` / `shared`; step-7 third clause replaced (28:374–390). This hits 25's extracted table.
9. **Ticket 21's declined session-row-gauge ADR** reversed (29:239–240, 29:411). Record as 21-X in part owning 21.
10. **Ticket 08's decision at 08:118–121** deviated by 29-A-2 / 29-R-1.
11. **The 450-per-source figure** carried by 08, 09, 12 corrected to 480/510 (29:302–304).
12. **Ticket 05's cleanup-cron snippet** `0 */5 * * * *` superseded (29:455).
13. **ADR 12 (ticket 11)** and **ADR 13** amended by 30 (30:185–187); 11:394–400 premise corrected in place (30:147); 19:512–514 factor-reset path exempt (30:218).
14. **Ticket 09 §R.6 and §R.5 ADRs** amended by 31-M-1/M-2; **ticket 13's `source.ip_hash` ADR** amended by 31-M-3; 24:715 inventory row amended (31:247).
15. **Register residuals re-priced by 31 §8** (31:305–312): 09 §5 per-IP rotation (confirmed, re-worded); 09 §R.6 "7×" (re-priced to ~100×); 09 mass-primitive residual (re-priced); 26 register entry 4 (re-unitised); 29-R-2 (improvement). 15-R-6 and 15-R-10 inherit these.
16. **Ticket 09 §R.2 figures** (240 × 6.7 h, 540 × 15 h) corrected to 228 × 6.3 h and 504 × 14 h (31:175–176). This touches 15-R-10.
17. **31-H-2 tightens ticket 26's edge-limit handover item** (26:546) as well as 29-H-2 (31:360–361).
18. **Ticket 16 test row 16:205** (runner single-use token) retired (32:135; 17:442), giving 16-R-13 withdrawn.
19. **Ticket 16's amendment withdraws 31-R-3** (17:422). This is within group but depends on 09's amendment-from-16 keys (32:137).
20. **Ticket 27's amendments to 03** (baggage off, `correlation.id` struck; 27:232–238, 27:332–335) are text-only, with no ADR or register row (27:315–316, 27:217–218).
21. **Duplicates to collapse**: 17-A-5 ≡ 06-A-3; 17-R-8 ≈ 06-A-3 + 15-R-7; 17-R-1 ≡ 16-R-1; 17-R-5 ≈ 15-R-5 ≡ 16-R-2; 17-R-6 ≡ 16-R-4; 17-R-7 ≈ 15-R-1 ≈ 16-R-6; 32-T-2 ≡ 27-T-3; mail-transport trigger appears as 15-T-1, 28-T-5, 31-T-5 (and on ticket 25's list, per 17:310).
