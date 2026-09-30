# Ticket 17 sizing gate — inventory part E (tickets 20, 21, 22, 23)

Files read in full: `20-deployment-origin-topology.md` (382 lines), `21-observability-signals.md` (857),
`22-mfa-core-recipe-extraction.md` (766), `23-totp-enrolment-stepup-and-reset-flows.md` (1206).

Conventions used here:

- Where an item's owner is a ticket outside E and that ticket's own number for it is unknown, the id carries
  an `E` counter (e.g. `19-M-E1`) so the aggregator can re-key it.
- **`handover` rows:** none of the four files contains a heading spelled exactly `### Handover items (ticket 25)`.
  Items explicitly owed to ticket 25 under other headings are still listed, as kind `handover`, and each gist
  says `[non-exact heading]`. The aggregator can drop or re-kind them.
- **Register rows marked `[implied]`** come from a ticket that has no register list and describes the item as
  "recorded" or "a deviation" in its body.
- The `*Superseded by the test-plan table (ticket 32)*` markers throughout all four files re-home **test**
  bullets to ticket 32. They do not touch any ADR, register, glossary, trigger or handover item, so no status
  below cites them.

## 1. Items

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 20-A-1 | adr-new | 20 | 20:322–323 | `SameSite=Strict` instead of the mandated `Lax`. Names the four `Lax` sites and the prescribed test at std line 499, which fails by design. | live | adr |
| 20-A-2 | adr-new | 20 | 20:324–325 | Two origins kept over Boot-serves-the-bundle single origin. Names the forgone nonce capability and the §5 reopening trigger. | live | adr |
| 20-A-3 | adr-new | 20 | 20:326–327 | Three-layer document CSP delivery, templated meta tag, and why CSP intersection makes an invariant meta floor impossible. | live | adr |
| 20-A-4 | adr-new | 20 | 20:328 | No nonce, justified by the bundle having no inline scripts, not by static-nonce weakness. | live | borderline |
| 20-A-5 | adr-new | 20 | 20:329–330 | Dev-only `'unsafe-inline'` in two directives, with `vite preview` as the CSP verification surface. | live | borderline |
| 20-A-6 | adr-new | 20 | 20:331–332 | `__Host-` prefix with a per-profile cookie name. Admits the non-dev profile is never exercised. | live | borderline |
| 20-A-7 | adr-new | 20 | 20:333 | API-side CSP kept as labelled defence in depth, recording the §3.5 argument surface from §6. | live | borderline |
| 20-R-1 | register | 20 | 20:157–163 | [implied] Deviation: our `Strict` fails the standard's prescribed test line 499 (`SameSite=Lax`). The test asserts `Strict`, with an ADR comment. Twin of 20-A-1. | live | |
| 20-R-2 | register | 20 | 20:118–122 | [implied] Fidelity: prod-only values (`Secure`, HSTS, `__Host-`, real origins, strict CSP) are asserted by test but never executed against a real deployment. | live | |
| 20-R-3 | register | 20 | 20:270–275 | [implied] Known argument surface: a static-host-delivered document CSP can be read as §3.5-prohibited infrastructure injection. The meta tag is the compensating position. | live | |
| 20-G-1 | glossary | 20 | 20:335–337 | *same-site vs cross-origin*. Re-derived in 08, 19 and 20, and a research pass got it wrong. | live | |
| 20-G-2 | glossary | 20 | 20:337–338 | *document context* vs API origin. The distinction the CSP half turns on. | live | |
| 20-T-1 | trigger | 20 | 20:253–255 | Reopen the topology if the production document ever needs an inline script (a static host cannot nonce it). | live; extended by 20:366–370 | |
| 20-T-2 | trigger | 20 | 20:366–370 | (Amendment from 23) Relaxing `script-src`, adding `'unsafe-inline'` or admitting `data:` also invalidates 23's decline of re-auth on enrolment. Add this to 20-T-1's text. | live | |
| 20-H-1 | handover | 20 | 20:279–283 | [non-exact heading] The cookie posture holds only while SPA and API share a registrable domain. Different domains break `Strict` outright. | live | |
| 20-H-2 | handover | 20 | 20:284–285 | [non-exact heading] The static host must emit the production header set, including `frame-ancestors 'none'`. | live | |
| 20-H-3 | handover | 20 | 20:286 | [non-exact heading] Someone must terminate TLS. The PRD requires it and nominates nobody. | live | |
| 20-H-4 | handover | 20 | 20:287–288, 20:345 | [non-exact heading] Never allow `localhost` in the prod CORS allow-list. The prod origin is a required handover input. | live | |
| 21-A-1 | adr-new | 21 | 21:597 | Actuator exposure-and-access posture (`health` only, `max-permitted: read-only`), including `info` non-exposure on a no-benefit reason. | live; trigger added by 21:719–720 | adr |
| 21-A-2 | adr-new | 21 | 21:598 | Push-not-expose (OTLP, export disabled), motivated by the loopback-default `localhost:4318` finding. | live | adr |
| 21-A-3 | adr-new | 21 | 21:598–599, 21:476, 21:595 | Probes and `db` health disabled while `diskspace` kept. Carries the `lead_days` recomputation trigger. | live; amended by 21:827–829 (21-M-1) | borderline |
| 21-A-4 | adr-new | 21 | 21:599 | Three-class alert taxonomy (per-event, rate-above, rate-below) with the state-clearing discriminator. | live; amended by 21:807–819 (21-M-2) | adr |
| 21-A-5 | adr-new | 21 | 21:599, 21:631–636 | Frontend half of lm-16 declined (no client error-ingestion endpoint). | live | register |
| 21-A-6 | adr-new | 21 | 21:600, 21:267–271 | Actuator health body exempted from 06/08's envelope contract (a seventh producer), test-pinned to `/actuator/**`. | live | borderline |
| 21-A-7 | adr-new | 21 | 21:600–601, 21:386–389 | `server.tomcat.mbeanregistry.enabled: true`, justified by ticket 09's thread-exhaustion argument, so it isn't deleted as cruft. | live | borderline |
| 21-A-8 | adr-new | 21 | 21:601–603 | Test harness disables OTLP specifically, not metrics defaults, plus `use-global-registry: false`. A wrong-looking-correct choice with a silent failure mode. | live | borderline |
| 21-M-1 | adr-amend | 21 (target 21-A-3) | 21:823–838 | (Amendment from 29) Session-row gauge decline reversed. Health becomes ping + diskspace + custom `h2Data`, adding two gauges. | live | borderline |
| 21-M-2 | adr-amend | 21 (target 21-A-4) | 21:807–819 | (Amendment from 28) New fourth class, absence detection ("intent with no outcome"). Collector-side, and may be declined. | live (undecided) | borderline |
| 21-R-1 | register | 21 | 21:588, 21:538–542 | lm-16 confirmed as a narrowed WARN/High, not closed. Its alerting half is undischargeable without a deployment. | live (stakes lowered, grade unchanged: 21:692–697) | |
| 21-R-2 | register | 21 | 21:588, 21:544–549 | as-13: `/actuator/health` `permitAll` fails the literal base-path check. ac-1 WARN/Medium precedent cited so it isn't double-counted. | live | |
| 21-R-3 | register | 21 | 21:589, 21:530–534 | Residual: build-time dependency-check cannot see CVEs published after the last build. Post-deploy re-scan owed. | live | |
| 21-R-4 | register | 21 | 21:589, 21:574–578 | Owed input: alert thresholds owned by ticket 09 (seam rule), parameterised with property keys. | superseded by 21:647–664 (09 §R discharges the reopening: 50 consecutive, window dropped) | |
| 21-R-5 | register | 21 | 21:589–590 | Never-executed assertions for the OTLP enablement path, per 20's precedent (see 20-R-2). | live | |
| 21-R-6 | register | 21 | 21:590, 21:631–636 | lm-16 frontend half declined as a declared partial. Twin of 21-A-5. | live | |
| 21-R-7 | register | 21 | 21:590–591, 21:435–437 | Total log-transport failure is undetectable in-process. It needs external absence-of-logs detection. | live | |
| 13-R-E1 | register | 13 | 21:591–592, 21:144–149 | ASVS 16.4.2 / 16.4.3 (L2) inherited as F with deployer obligations. Restated in 21 so it is seen not to re-grade them. | live | |
| 21-R-9 | register | 21 | 21:592–594, 21:491–510 | Design note: audit volume bounded by a per-window distinct-source cap with truncation row 46. | superseded by 21:737–765 (TM-01: rows 12/13/14 unbounded, owned by 26) | |
| 21-R-10 | register | 21 | 21:779–787 | (Amendment from 15, TM-14) Owed input: the zero-authenticable-admins signal needs rows for two out-of-band channels that ticket 28 is still deciding. | live | |
| 26-R-E1 | register | 26 | 21:761–765 | (Amendment from 15) Owed input: row 46's `N` has no value, property key or binding test. The seam-rule debt passes to 26. | live | |
| 21-G-1 | glossary | 21 | 21:605 | *per-event alert* | live | |
| 21-G-2 | glossary | 21 | 21:605 | *rate-above* | live | |
| 21-G-3 | glossary | 21 | 21:605 | *rate-below* | live | |
| 21-G-4 | glossary | 21 | 21:605 | *transition-keyed row* | live | |
| 21-G-5 | glossary | 21 | 21:605–606 | *observability boundary* | live | |
| 21-T-1 | trigger | 21 | 21:476–478, 21:595 | `lead_days` = 2 is a judgement. Recompute the diskspace threshold when the mount is resized or logs move. Lives in the ADR (21-A-3). | live | |
| 21-T-2 | trigger | 21 | 21:719–724 | (Amendment from 25) Adding `git-commit-id` or build-info plugins reactivates `info` exposure at build time. Enforce via 24's 13.4.1 jar-content test. | live | |
| 21-H-1 | handover | 21 | 21:557–558 | [non-exact heading] Alerting and rate computation for rate-above and rate-below classes. Acceptance: §6 thresholds exist as rules. | live | |
| 21-H-2 | handover | 21 | 21:559–560 | [non-exact heading] External absence-of-logs detection. Acceptance: an alert fires when the app stops emitting. | live | |
| 21-H-3 | handover | 21 | 21:561–562 | [non-exact heading] Re-enable `probes.enabled` and `health.db.enabled` together, `add-additional-paths` false. Acceptance: probe paths return 200. | live | |
| 21-H-4 | handover | 21 | 21:563–565 | [non-exact heading] OTLP collector: flip `enabled`, supply the URL under required-property validation. Service-connection bean overrides both. | live; extended by 21:846–857 (authenticable-admin gauge alert <2 owed here) | |
| 21-H-5 | handover | 21 | 21:566–567 | [non-exact heading] Post-deployment dependency re-scan, monthly and on any dependency change. Acceptance: a dated scan report. | live | |
| 21-H-6 | handover | 21 | 21:569–570, 21:767–768 | [non-exact heading] The deployer sizes the mount and owns recomputing `threshold`. 15's amendment adds that `daily` was unbounded. | live | |
| 21-H-7 | handover | 21 | 21:812–817 | [non-exact heading] (Amendment from 28) Absence-detection correlator for intent-with-no-outcome rows is collector-side, a deployer obligation, with timeout > recovery outage. | live (undecided) | |
| 09-X-E1 | adr-reversed | 09 | 21:649–651 | 09 §R: ticket 09's ADR 3 flips from declined to implemented (NIST §3.2.2 cap at 100 consecutive), retiring the deviation. | live (reversal) | |
| 22-A-1 | adr-new | 22 (decision in 19/23) | 22:646–653, 22:737–738 | Per-request → per-session assurance change against MFA_Core §4.1 ("on each request"). This is the reviewable deviation, and 19 missed it. | live; residual restated at 23:472–475 but absent from 23's ADR list | adr |
| 19-M-E1 | adr-amend | 19 (19's ADR 1) | 22:636–640, 22:736–737 | Rewrite 19's ADR 1: the router never put MFA_Critical_Transaction on our path, and MFA_Core mandates no AOP. | live | adr |
| 19-X-E1 | adr-reversed | 19 | 22:633–635, 22:738 | Drop any ADR for the PIN skip. §4.1 and Q3 sanction TOTP-only, so a citation suffices. | withdrawn by 22:738 | register |
| 19-M-E2 | adr-amend | 19 (re-homing ADR) | 22:641–643, 22:265–268 | AuthenticationProvider re-homing is licensed, not a deviation. Flag that the corpus `MultiFactorAuthenticationProvider` is interface-incompatible with Spring's. | live | borderline |
| 19-M-E3 | adr-amend | 19 (key-location ADR) | 22:326–329, 22:644, 22:739 | Add the Q13-vs-§3.4 governance contradiction as supporting material for the DB-key override. | live | adr |
| 22-R-1 | register | 22 | 22:683–684 | Standards defect: `NEG-REQ-03` does not exist (sequence 01, 02, 04, 05). | live | |
| 22-R-2 | register | 22 | 22:684 | Standards defect: NEG-REQ-04 cites §2.4, which contains no such rule. | live | |
| 22-R-3 | register | 22 | 22:684–686 | Standards defect: §7 omits the standard's own highest-severity negatives (plaintext secret, key logging, in-memory counters, unconfirmed pending). | live | |
| 22-R-4 | register | 22 | 22:686–687 | Standards defect: `CON-06` is referenced in §8 and never defined. | live | |
| 22-R-5 | register | 22 | 22:687–689 | Standards defect: digit length stated three inconsistent ways, with no startup validation specified. | live | |
| 22-R-6 | register | 22 | 22:689–691 | Standards defect: `TotpUtilities` declared twice with disjoint members, and neither is complete. | live | |
| 22-R-7 | register | 22 | 22:413–419 | Standards defect: the "1-hour sliding window" is not sliding. A DoS lever against a known admin username, and the map's third defective control. | live (mitigated by 23-A-7) | |
| 22-R-8 | register | 22 | 22:420–422 | Standards defect: empty-string `X-TOTP` counts as a failed attempt (Recipe 12 null check only). | live (fixed in 23:773–776) | |
| 22-R-9 | register | 22 | 22:423–425 | Standards defect: three readings of the same counter (cumulative / consecutive / code's reset-on-gap). | live | |
| 22-R-10 | register | 22 | 22:86–88, 22:429–431 | Standards defect: no TOTP unlock path anywhere. A locked sole admin is unrecoverable without a DB edit. | live (resolved by 23:613–619) | |
| 22-R-11 | register | 22 | 22:458–463 | Standards defect: 412 prescribed in prose, produced by no code (the handler yields 422/401). | live | |
| 22-R-12 | register | 22 | 22:470–474 | Standards defect: the mandated `"User Details not found."` detail string is unproducible from any recipe exception. | live | |
| 22-R-13 | register | 22 | 22:479–481 | Standards defect: §3.2's "distinguish by HTTP status alone" is false on its own table. | live | |
| 22-R-14 | register | 22 | 22:495–505 | Standards defect: Generate-QR contradiction, §3.2 always-enabled vs §5 disabled. §5 is correct. | live | |
| 22-R-15 | register | 22 | 22:395–400 | Standards defect: the claimed ±30 s tolerance is effectively +1 window after first success, because of the replay rule. | live | |
| 22-R-16 | register | 22 | 22:329–332 | Standards defect: the yearly key-rotation MUST is unimplementable with no key-version column. | live | |
| 22-R-17 | register | 22 | 22:676–679 | Governance contradiction: §3.4 enforced-constraint tags vs Q13/Q15/Q16 presenting the same three items as integrator choices. | live | |
| 22-R-18 | register | 22 | 22:367–370 | Standards defect: Recipe 12 cannot compile against Recipe 5's entity (the lockout columns are missing). | live | |
| 22-R-19 | register | 22 | 22:72–77 | Standards defect: no provisioning recipe exists. It is deferred to the MCC corpus. | live | |
| 22-R-20 | register | 22 | 22:103–107 | Standards defect: Recipe 13 hard-requires `PINAuthenticationProvider`, and `getRequestKey` is called five times but never declared. | live | |
| 24-R-E1 | register | 24 (or 17) | 22:668–670 | Q14 key-rotation schedule and re-encryption procedure still open. "Ticket 24 or a formal deferral in 17". | superseded by 23:752 (ticket 25 owns writing it) | |
| 23-A-1 | adr-new | 23 | 23:923 | JSON provisioning envelope `{otpauthUri, secretBase32, qrPng}` with manual-entry secret, deviating from PNG bytes. Client-side SVG fallback rejected (23:173–178). | live | adr |
| 23-A-2 | adr-new | 23 | 23:923–925, 23:326 | Hand-composed per-matcher rules rejecting `@EnableMultiFactorAuthentication` and the factory idiom, with role-first ordering as the reason. | live; amended by 23:1167 (23-M-2) | adr |
| 23-A-3 | adr-new | 23 | 23:925–926, 23:468–470 | `validDuration` on the read rule is a fail-closed type guard, not a time bound. The text must say so explicitly. | live | adr |
| 23-A-4 | adr-new | 23 | 23:926, 23:186–189, 22:174–177 | POST provisioning against STD L38's GET. | live | borderline |
| 23-A-5 | adr-new | 23 | 23:926, 23:212–217 | JSON `{ code }` against `X-TOTP`, deviating from two normative statements. | live | borderline |
| 23-A-6 | adr-new | 23 | 23:926, 23:222–233 | Successful confirmation grants `FACTOR_TOTP`, so confirmation and verification share one mechanism. | live | borderline |
| 23-A-7 | adr-new | 23 | 23:926–928, 23:522, 23:900–902 | Two-tier factor lockout: 55% / 0.03% arithmetic, conditional-on-password framing, cumulative-vs-consecutive note, provider-side precondition cited. | live; amended by 23:1042–1056 (23-M-1) | adr |
| 23-A-8 | adr-new | 23 | 23:928, 23:613–619 | One unlock endpoint clearing both axes (password + TOTP tier 1). Does not clear tier 2. | live | borderline |
| 23-A-9 | adr-new | 23 | 23:928–929 | Context-prefix inside the plaintext as the AAD substitute, with the key-source override of STD L253. | live | adr |
| 23-A-10 | adr-new | 23 | 23:929, 23:851–861 | 412 for missing factor against RFC 9110, naming RFC 9470 as the road not taken. | live | borderline |
| 23-A-11 | adr-new | 23 | 23:929–930, 23:778–786 | ±1 skew against ASVS 6.5.5 (L2), with the 89-second figure. Also register row 23-R-1. | live | register |
| 23-M-1 | adr-amend | 23 (target 23-A-7) | 23:1042–1056 | (Amendment from 09 §R) Tier-2 disable forces password rebinding at next login. The cumulative-never-reset arithmetic supports the narrow reading. | live | adr |
| 23-M-2 | adr-amend | 23 (target 23-A-2) | 23:1148–1176 | (Amendment from 15, TM-04) State the asymmetry: the factor gate is single-layered (matchers only), while the role gate has two layers. | live | borderline |
| 23-R-1 | register | 23 | 23:935, 23:785–786 | ASVS 6.5.5 (L2) deviated: ±1 skew gives ~89 s against a 30 s maximum lifetime. | live | |
| 23-R-2 | register | 23 | 23:935, 23:625–627 | ASVS 6.4.4 (L2) N/A-with-rationale. | superseded by 23:1084–1094 (regraded satisfied-by-parity) | |
| 23-R-3 | register | 23 | 23:935–936, 23:698–704 | ASVS 6.3.3 (L2) relaxed: MFA on admin only, with IM8 ac-2 rationale and mitigating controls. The L2 whole-app claim stays false. | live | |
| 23-R-4 | register | 23 | 23:936, 23:682–696 | ASVS 6.1.3 and 6.3.4 (L2) satisfied by the three-pathway inventory. | live; amended by 23:1071–1082 (fourth "not a pathway" row) | |
| 23-R-5 | register | 23 | 23:936–937, 23:544–545 | ASVS 6.1.1 (L1) satisfied by §5's lockout paragraph. | live | |
| 23-R-6 | register | 23 | 23:937, 23:557–562 | ASVS 6.5.1 (L2) satisfied by atomic replay rejection under the row lock. | live | |
| 23-R-7 | register | 23 | 23:937–938, 23:790–792 | ASVS 6.5.8 (L3) satisfied though non-binding (one `Clock` bean). | live | |
| 23-R-8 | register | 23 | 23:938, 23:525–531 | NIST §3.2.2 satisfied at the cap. Tier 1 recorded as an additional control, not a deviation. | live | |
| 23-R-9 | register | 23 | 23:938–939, 23:888–895 | NIST §4.1.2.1 notification SHALL failed. Compensated by the enrolment audit event and the 409 detection signal. | live | |
| 23-R-10 | register | 23 | 23:939, 23:648–653 | NIST §4.1.2.1 AAL rule satisfied and load-bearing for the first-enroller race. | live; reframed by 23:1102–1108 (post-enrolment binding, not first enrolment) | |
| 23-R-11 | register | 23 | 23:940, 23:755–764 | NIST SP 800-38D satisfied (16-byte random IV). Removes an entry that was heading for the register. | live | |
| 23-R-12 | register | 23 | 23:1096–1100 | (Amendment from 25) ASVS 6.5.6 (L3) added as a supporting positive citation for factor revocation. | live | |
| 23-R-13 | register | 23 | 23:797–798 | [implied] Deviation: properties prefix `app.mfa.totp`, not the recipe's `spring.eds.mfa.totp`. Not in 23's register list. | live | |
| 23-R-14 | register | 23 | 23:863–864 | [implied] `FACTOR_ENROLMENT_REQUIRED` at 422 is inherited and off-label ("one line in the register"). Not in 23's register list. | live | |
| 23-R-15 | register | 23 | 23:472–475 | [implied] Residual deviation: STD L310 per-request verification vs our per-session factor. Twin of 22-A-1. Not in 23's register or ADR lists. | live | |
| 23-G-1 | glossary | 23 | 23:932 | *pending enrolment* | live | |
| 23-G-2 | glossary | 23 | 23:932 | *tier-1 lock* | live | |
| 23-G-3 | glossary | 23 | 23:932 | *tier-2 disable* | live | |
| 23-G-4 | glossary | 23 | 23:932 | *enrolment binding* | live | |
| 23-G-5 | glossary | 23 | 23:932 | *context prefix* | live | |
| 23-G-6 | glossary | 23 (shared with 09) | 23:1007–1008 | (Amendment from 09 §R) The same integer 100 under different counting rules on the two axes. "Recorded … in the glossary". | live | |
| 23-T-1 | trigger | 23 | 23:176–178 | If ticket 14 finds Blob reconstruction fights Base UI, fall back to client-side SVG QR, which costs one ADR. | live | |
| 23-H-1 | handover | 23 | 23:542–545, 23:947 | [non-exact heading] Break-glass as a second trigger on 19's lost-authenticator runbook control (self-burned tier 2). | live; route set by 23:1189–1197 (28) | |
| 23-H-2 | handover | 23 | 23:656–657, 23:947 | [non-exact heading] Enrol the seeded admin before the deployment is reachable by anyone else (first-enroller race). | live | |
| 23-H-3 | handover | 23 | 23:786–787, 23:947 | [non-exact heading] TOTP depends on the server clock. A device ~25 s behind succeeds once, then fails. | live | |
| 23-H-4 | handover | 23 | 23:752, 23:947–948 | [non-exact heading] Key-rotation re-encryption procedure (Q14), made possible by the key-version column. | live | |

## 2. Per-ticket tally (stated vs enumerated)

| ticket | kind | stated | enumerated | note |
|---|---|---|---|---|
| 20 | adr-new | 7 (numbered list, 20:320–333) | 7 | match |
| 20 | glossary | 2 (named, 20:335–338) | 2 | match |
| 20 | register | — | 3 | **mismatch**: 20 has no register section. The three rows are implied deviations/fidelity items (21:589–590 treats 20-R-2 as a register precedent). |
| 20 | trigger | 1 (20:253) | 2 | **mismatch**: 23's amendment (20:366–370) asks to extend the trigger. Counted as a second row. Could merge into 20-T-1. |
| 20 | handover | — | 4 | **flag**: §7 "Deployment constraints" (20:277) is not the exact handover heading. 20:290 says "these three", but §7 lists four bullets. |
| 21 | adr-new | 8 ("Eight ADRs", 21:597) | 8 | match. 21:388 "ADR'd" is the same item as 21-A-7. |
| 21 | adr-amend | — | 2 | from amendments 29 and 28. 21-M-2 is undecided ("If you decline it", 21:816). |
| 21 | register | 8 + a ninth ("Eight register entries … Plus a ninth", 21:588–594) | 9 from the list + 2 from amendments = 11 | **mismatch**: 21-R-10 (from 15, TM-14) and 26-R-E1 (row 46 `N`, owner 26) were added by the amendment from 15 after the count. One listed row (13-R-E1) is owned by 13. |
| 21 | glossary | 5 ("Five glossary terms", 21:605) | 5 | match |
| 21 | trigger | — | 2 | 21-T-1 is folded into an ADR per 21:595. 21-T-2 comes from 25's amendment. |
| 21 | handover | "five items" + disk item (21:551–570) | 7 | **flag**: the heading is "One section for ticket 25", not exact. 5 + disk + 1 from amendment 28. Amendment 30 extends item 4. |
| 22 | adr-new | 1 ("ticket 17 owes an ADR", 22:652–653) | 1 | match. Its owner is disputed: 22 raises it for decisions made in 19/23. |
| 22 | adr-amend / reversed (on 19) | "four of ticket 19's six owed ADRs" (22:631) | 3 amend + 1 reversed = 4 | match on count. 19's own ADR numbers are unknown except ADR 1. The aggregator must map 19-M-E2/E3 and 19-X-E1. |
| 22 | register | none stated. §15 heading "for the deferral register" (22:681) + "Beyond those above" | 6 in §15 + 14 earlier-labelled defects = 20, + 24-R-E1 | **flag**: "Beyond those above" imports defects scattered through §§1–14. I counted only those explicitly called defects/contradictions. Frontend corpus gaps (22:543–590) were excluded and could add ~5. |
| 23 | adr-new | 11 ("ADRs (11)", 23:923) | 11 | match |
| 23 | adr-amend | — | 2 | from the 09 §R and 15 amendments |
| 23 | register | none stated; 11 listed (23:935–940) | 11 listed + 1 amendment + 3 implied = 15 | **mismatch**: 23-R-13 and 23-R-14 are called "deviation"/"one line in the register" in the body but are missing from §15's list. 23-R-15 is "recorded" (23:472) but appears in neither list. 23-R-2 is superseded. |
| 23 | glossary | 5 (23:932) | 6 | **mismatch**: 09 §R's amendment says the 100-with-different-counting-rules split is "recorded … in the glossary" (23:1008). "Factor freshness" is owed by 08 and not counted. |
| 23 | trigger | — | 1 | the conditional SVG fallback |
| 23 | handover | 4 under "Amendments raised: 25 (...)" (23:947–948) | 4 | **flag**: no exact heading |
| 23 | ADR coverage | — | — | **flag**: 22-A-1 (per-session vs §4.1) is not among 23's 11 ADRs, although 23:472–475 records the residual. Either 17 owns it standalone or it folds into 23-A-3. |

## 3. Cross-file effects (items here that act on tickets outside group E)

- **09**: 21:649–651 (09 §R amendment) flips 09's ADR 3 from declined to implemented (09-X-E1). 09's NIST §3.2.2 deviation, and its register row if any, is retired.
- **09**: 21:647–664 discharges 21's reopening of 09. 21-R-4 ("thresholds owed by 09") is superseded. If 09 holds a matching owed-input row, retire it.
- **09**: 21:391–396 and 21:684–685 require `.recordStats()` on 09's Caffeine builders, now three structures. This is a design amendment, not a 17 item. Listed for completeness.
- **13**: 21:591–592 restates ASVS 16.4.2/16.4.3 as F (13-R-E1). The aggregator should dedupe against 13's own register rows.
- **26**: 21:761–765 hands row 46's `N` (value, key, binding test) to ticket 26 as an owed input (26-R-E1). 21:737–765 also supersedes 21-R-9's "audit volume bounded" claim.
- **28**: 21:779–787 registers the zero-authenticable-admins signal as dependent on ticket 28's out-of-band audit rows (21-R-10).
- **24**: 21:721–724 moves `git.properties` / `build-info.properties` enforcement onto 24's 13.4.1 jar-content test. 22:668–670's Q14 rotation deferral (24-R-E1) is superseded by 23:752, which assigns the procedure to 25.
- **19**: 22:636–640 and 22:736–737 require rewriting 19's ADR 1 (19-M-E1). 22:738 withdraws any PIN-skip ADR (19-X-E1). 22:641–643 reframes the re-homing ADR (19-M-E2). 22:739 adds Q13-vs-§3.4 to the key-location ADR (19-M-E3).
- **19**: 22:468 corrects 19's "three contradictory statuses" to two plus a misattribution. 22:84–85 says 19 "named the wrong" deviation.
- **19**: 23:946 raises amendments to 19: the envelope is 48 → 69 bytes (23:743–746), AAD is replaced by the context prefix (23:714–726), and the read rule is now bounded (23:452–466). The last two likely amend 19's encryption and enforcement ADRs (19 numbering unknown).
- **19**: 23:755–756 says 800-38D "replaces an entry that was heading for the deferral register". If 19 (or another ticket) holds a 96-bit-IV deviation row, it is withdrawn.
- **11**: 23:1200–1206 (from ticket 30) exempts `DELETE /api/admin/users/{uuid}/totp` from the two-enrolled-admins count. This amends 11's `AdminActionGuard` invariant, including any 11 ADR on it. 23:613–619 extends 11's unlock endpoint to both axes (see 23-A-8).
- **08**: 23:932–933 notes glossary term *factor freshness* is owed by 08. It is not counted here.
- **25**: 23:1084–1094 (from 25) supersedes 23-R-2. 21:702–724 (from 25) adds 21-T-2. Handover rows 20-H-*, 21-H-* and 23-H-* all target 25's table but sit under non-exact headings.
- **14**: 23:176–178's trigger (23-T-1) fires on a ticket 14 finding and would add one ADR.
