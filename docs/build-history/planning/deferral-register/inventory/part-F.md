# Inventory part F — tickets 24, 25, 26

Files read in full: `24-secrets-and-configuration-handling.md` (1104 lines), `25-operational-handover-document.md`
(971 lines), `26-unbudgeted-routes-and-audit-volume.md` (692 lines). Line numbers are from the files as they are now.
Rows whose owner is outside group F use an `x` suffix on `n` (e.g. `09-R-x1`) to avoid colliding with that ticket's
own numbering.

## 1. Items

### Ticket 24

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 24-A-1 | adr-new | 24 | 24:161–179 (claim 24:172) | All secrets bind via `@Validated @ConfigurationProperties`, deviating from IM8 as-8's named `@Value`; Boot docs as authority; value checks in `@Bean` factory | live (caveat: 24:951–956 says Binder path is lenient on unresolved placeholders) | adr |
| 24-R-1 | register | 24 | 24:143–149 | Corpus `${ENV_VAR:dev-default}` + `.env.sit` pattern falls back to dev credentials (MCC_Shared_Auth_Recipes.md:1663); rejected outright as fail-open. Standards defect/deviation | live | |
| 24-R-2 | register | 24 | 24:151–156 | "Eighth standards defect": cited Mcc_Project_Bootstrap §3.6 profile-configuration contract and `ProfileDotenvPostProcessor` do not exist; four standards reference them | live | |
| 24-R-3 | register | 24 | 24:176–179 | `im8-review` as-8 PASS greps for `@Value("${...}")`; our config may read as absent — tool false negative (fidelity) | live | |
| 24-R-4 | register | 24 | 24:401–404, 24:691–692 | ASVS 11.5.1 (L2) satisfied-by-procedure; only printable-ASCII and all-identical checks enforced | live | |
| 24-R-5 | register | 24 | 24:424–431 | Deliberate deviation from Boot kebab-case leaf recommendation: new properties dot-separated at every segment to avoid dash-removal env mapping | live | |
| 24-R-6 | register | 24 | 24:485–490 | Secrets deliberately absent (N/A): no password pepper, no token keyed hash, no session `hash` key, no CSRF cookie secret | live | |
| 24-R-7 | register | 24 | 24:591–613, 24:694 | ASVS 13.2.1/13.2.2 (L2) failed for H2 datasource (table-scope GRANT, h2database#2846, first user is admin); 13.2.3 satisfied | live | |
| 24-R-8 | register | 24 | 24:615–621 | H2 datasource credential declared non-secret (N/A), outside §3 assertion; `CIPHER=AES` declined for three reasons | live | |
| 24-R-9 | register | 24 | 24:623–628 | Dev H2 file read yields live session hijack via `SPRING_SESSION.SESSION_ID`; earlier "no secrets" claim withdrawn; scoped, not mitigated | live | |
| 24-R-10 | register | 24 | 24:632–648 | Committed `.env.development`/`.env.production` vs as-8 ".env gitignored"; `VITE_*` declared non-secret; allowlist + placeholder assertions | live | |
| 24-R-11 | register | 24 | 24:669–673 | Secret scanner runs only locally (no CI); pre-commit hooks unshared and bypassable with `--no-verify` (residual) | live | |
| 24-R-12 | register | 24 | 24:680–687 (also 24:270) | ASVS 13.3.1 (L2) + 13.3.3 (L3) failed: no vault/KMS/HSM; TOTP seeds in scope; one deferral, two IDs; compensating controls listed | live | |
| 24-R-13 | register | 24 | 24:689–691 | Partial: 11.2.2 (L2) tombstone versions never retire; 13.3.4 (L3) tombstone forward-only | live (13.3.4 log-key "freely" qualified by 24:871–877) | |
| 24-R-14 | register | 24 | 24:696–706 | Satisfied set: 13.1.4 conditional on 25; 11.1.1; 11.1.2 with algorithm inventory; 11.3.2, 11.2.3, 11.4.1, 13.2.3, 13.4.5 | live (13.4.5 "authenticated base path" reason struck by 24:1029–1033 / 25:682–684) | |
| 24-R-15 | register | 24 | 24:725–729 | ASVS 13.4.1 (L1) source-control metadata unowned; assigned to 20 or 25 | superseded by 25:283–288 and 25:664–666 (adopted by 25) | |
| 24-R-16 | register | 24 | 24:515–523 | Runtime `app.origins.api` vs build-time `VITE_API_ORIGIN` lifecycle drift, unchecked, presents as CSP failure; also a required handover line | live | |
| 24-R-17 | register | 24 | 24:789–791 | Build-once-deploy-many does not hold: bundle is environment-specific; deviation from ordinary deployment practice | live | |
| 24-R-18 | register | 24 | 24:820–838 | Tombstone HMAC key rotation exception justified as blind-index key (one-bit disclosure); distinct from TOTP key; HKDF pre-empted | live | |
| 24-R-19 | register | 24 | 24:884–899 | Jackson `INCLUDE_SOURCE_IN_LOCATION` must be disabled but cannot join refresh validator; asserted by mapper outcome test ("where a control cannot live" #1) | live | |
| 24-R-20 | register | 24 | 24:901–907 | `spring.jackson.use-jackson2-defaults` checked and not a prohibited entry (N/A), with stated caveat | live | |
| 24-R-21 | register | 24 | 24:940–963 | Fail-fast idiom gap: `@ConfigurationProperties` Binder accepts unresolved `${VAR}` literal; `setRequiredProperties` is the real mechanism; all such claims to be re-read | live | |
| 24-R-22 | register | 24 | 24:965–969 | `management.otlp.metrics.export.headers.*` is a conditional fourth secret with map namespace; name-absence assertion must handle it | live | |
| 24-R-23 | register | 24 | 24:990–1000 | Eleventh prohibited config: rebinding runner's `System.out` token must never reach logging; not validator-expressible (instance #3) | withdrawn by 24:1058–1061 (ticket 28: runner emits no secret) | |
| 24-R-24 | register | 24 | 24:1072–1073 | `h2.bindAddress` is a JVM system property invisible to the validator (instance #4); moot once TCP server bean prohibited | live | |
| 24-R-25 | register | 24 | 24:1048–1052 | V11/V13 register citation hygiene: no RFC 2119 modals in ASVS; V6.5 cites 800-63-3; 112-bit hard-coded vs NIST pointer | live | |
| 24-R-26 | register | 24 | 24:1043–1046 | ASVS 13.4.1 is disjunctive, first branch taken; runtime git metadata exposure is 13.4.6 (L3) + 13.4.5, not 13.4.1 | live | |
| 24-G-1 | glossary | 24 | 24:546–549 | **Prohibited configuration**: well-formed, binding value argued must never be set; distinct from invalid configuration | live | |
| 24-T-1 | trigger | 24 | 24:854–856 | Tombstone retention becomes bounded → old HMAC versions become retirable, converting forward-only into full rotation | live | |

### Ticket 25

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 25-A-1 | adr-new | 25 | 25:791, detail 25:481–494 | One generated source table, two renderings (17 compliance, 25 operational), `verify`-phase drift gate | live (amended by 25-M-1) | adr |
| 25-A-2 | adr-new | 25 | 25:791–792, detail 25:515–542 | Three-field row schema (responsibility/status/priority), `priority` structurally absent on limitations | live | borderline |
| 25-A-3 | adr-new | 25 | 25:792, detail 25:569–587 | Deployment-sequence ordering from application failure points, severity excluded | live | borderline |
| 25-A-4 | adr-new | 25 | 25:793, detail 25:589–660 | Break-glass as NIST §4.2.2.2 option 2, adopted Reading B recorded; earlier two-limb draft withdrawn | live | adr |
| 25-A-5 | adr-new | 25 | 25:793–795, detail 25:629–649 | Distinct-recovery-address mitigation preferred over recovery contacts (rejected) and saved codes (deferred to 19) | live | adr |
| 25-A-6 | adr-new | 25 | 25:794–795, detail 25:461–479 | Roles-not-people; vacancies encoded as unmet acceptance checks, never a responsibility value | live | borderline |
| 25-M-1 | adr-amend | 25 | 25:964 | Target 25-A-1: drift gate is one JUnit test under Failsafe 3.6.0, not also a plugin execution; bypass flags named (from ticket 16) | live | adr |
| 25-R-1 | register | 25 | 25:785, detail 25:655–656 | NIST §4.2.3 account-recovery notification SHALL failed; cause no transport outside `dev` | live | |
| 25-R-2 | register | 25 | 25:785–786, detail 25:739–745 | §4.6 two-notification-address SHALL declined; one residual, three address axes (capacity, delivery, §4.2.1.2 recovery addresses) | live | |
| 25-R-3 | register | 25 | 25:786, detail 25:629–649 | Issued-code/password correlation via mailbox accepted as documented choice | live | |
| 25-R-4 | register | 25 | 25:787 | ASVS 13.3.1 (L2) / 13.3.3 (L3) inherited — duplicate of 24-R-12 | live | |
| 25-R-5 | register | 25 | 25:787, detail 25:313–319 | ASVS 16.4.2 / 16.4.3 (L2) inherited as deployer obligations (from ticket 13) | live | |
| 25-R-6 | register | 25 | 25:787–788, detail 25:264–272 | ASVS 13.2.1 / 13.2.2 (L2) accepted — duplicate of 24-R-7 | live | |
| 25-R-7 | register | 25 | 25:788 | ASVS 11.5.1 (L2) satisfied-by-procedure — duplicate of 24-R-4 | live | |
| 25-R-8 | register | 25 | 25:788, detail 25:698–704 | ASVS 13.4.6 (L3) satisfied-by-absence (no git/build-info properties), with plugin trigger | live | |
| 25-R-9 | register | 25 | 25:789, detail 25:359–361 | lm-16 alerting half (ticket 21) recorded as a High | live | |
| 25-R-10 | register | 25 | 25:775, detail 25:510–513 | ASVS 13.2.4 / 13.2.5 cited as 13.1.1's enforcement companions, not drift-family members (owed to 17's register) | live | |
| 25-R-11 | register | 25 | 25:775–776, detail 25:600–609 | Register carries adopted-reading note on NIST §4.2.1 (Reading B, ~70%); nothing filed against 02 | live | |
| 25-R-12 | register | 25 | 25:424–427; 25:916–917 | ASVS 6.1.1 (L1) pass-with-note conditional on this document and operable runner; pending first rehearsal. Owner could be 09 | live (re-asserted at 25:916–917) | |
| 09-R-x1 | register | 09 | 25:420–423 | ~100 unauthenticated requests permanently disable a password authenticator; cardinality limiter 7×, IP rotation defeats; ~10 h lead time | superseded figures by 25:930–932 (ticket 31: ≈100×, ≈9.7 h) | |
| 25-G-1 | glossary | 25 | 25:802–803 | **Acceptance check**: how a reader proves a deployer obligation was discharged, distinct from an enforced check | live | |
| 25-G-2 | glossary | 25 | 25:803–804 | **Declared vacancy**: named role with no holder, encoded as an unmet acceptance check | live | |
| 25-T-1 | trigger | 25 | 25:797, also 25:565 | Any real deployment: unexercised-config row flips `responsibility` to `deployer` | live | |
| 25-T-2 | trigger | 25 | 25:798 | Mail transport enters scope: §6 conditional pass closes, three notification SHALLs resolve | live (consequence TM-13 attached by 25:870–874; build constraint 25:966–969) | |
| 25-T-3 | trigger | 25 | 25:798–799, also 25:772–773 | Ticket 19 reverses recovery-codes deferral: saved-plus-issued supersedes the mitigation; ≥112-bit minting | live | |
| 25-T-4 | trigger | 25 | 25:799–800, also 25:771 | `git.properties` / `build-info.properties` plugin added | live | |
| 28-T-x1 | trigger | 28 | 25:916–917 | A red step-7 rehearsal grades 6.1.1 F and reopens ticket 28 | live | |

### Ticket 26

| id | kind | owner | source | title / gist (≤25 words) | status | shape |
|---|---|---|---|---|---|---|
| 26-A-1 | adr-new | 26 | 26:567–569 | Budget table stays request-rate allowlist; unlisted routes metered on session-store misses; default-deny refused on 09's admin-availability reason | live (amended by 26-M-2) | adr |
| 26-A-2 | adr-new | 26 | 26:570–571 | Rejecting unmatched paths before `CsrfFilter` declined: route oracle, unverifiable mapped-set union, redundant with miss budget | live | adr |
| 26-A-3 | adr-new | 26 | 26:572 | Two-tier emitter bound, and why a limiter cannot substitute | live (amended by 26-M-2) | adr |
| 26-A-4 | adr-new | 26 | 26:573 | Count-and-first-seen field pair; 10,000-entry counting set retracted | live | borderline |
| 26-A-5 | adr-new | 26 | 26:574 | Row 46 widened to two exact counts plus truncated row's identity | live | borderline |
| 26-A-6 | adr-new | 26 | 26:575–576 | `N_src`=20, `N_usr`=500, 15-min window as one published judgement with recomputation trigger | live (amended by 26-M-2) | borderline |
| 26-A-7 | adr-new | 26 | 26:577 | 256-char raw-URI cap with truncation marker, cited to `:255` and 16.2.1, not 16.4.1 | live | borderline |
| 26-A-8 | adr-new | 26 | 26:578 | Row 35 keyed rather than deleted — recipe-deviation note, what remains true after keying | live | borderline |
| 26-A-9 | adr-new | 26 | 26:579 | Session-id cap; paired explicit `CookieSerializer` bean as its safety condition | live | adr |
| 26-A-10 | adr-new | 26 | 26:580 | `daily` as formula with one deployer input; `bytes_per_row` measured | live (amended by 26-M-1) | borderline |
| 26-A-11 | adr-new | 26 | 26:581 | Generated three-registry coverage assertion, verified scope and unverified exclusions | live | borderline |
| 26-A-12 | adr-new | 26 | 26:582 | Every §9 standards hook adopted-because-cheap at L2 against declared L1 target | live | register |
| 26-M-1 | adr-amend | 26 | 26:679–681 | Target 26-A-10 (from ticket 29): disk sizing gains H2 term, ≈6.96 GB not 2.4 GB | live | adr |
| 26-M-2 | adr-amend | 26 | 26:685–687 | Targets 26-A-1/3/6 (from ticket 31): miss budget, tier-1 keys and `N_src` key on source key (IPv4 /32, IPv6 /64) | live | adr |
| 26-R-1 | register | 26 | 26:591–592 | Raw request rate on unlisted routes deferred to infrastructure, deployer-owned; 15.1.3/15.2.2 (L2) by documentation only | live | |
| 26-R-2 | register | 26 | 26:593–594 | Per-occurrence timestamps on keyed rows lost by design; 16.2.1 satisfied for keyed rows, exposure confined to row 46 | live | |
| 26-R-3 | register | 26 | 26:595 | Per-source identity beyond `N_src` lost; bracketed `[N, N + untracked]` | live | |
| 26-R-4 | register | 26 | 26:596 | IP rotation defeats miss budget — 15.3.4 (L2) caveat, compensated by emitter bound | live (re-unitised "per source key" by 26:688–689) | |
| 26-R-5 | register | 26 | 26:597–598, detail 26:269–278 | `__Host-` ordering closure absent in `dev`, no same-host duplicate stop, ignores ports — ticket 08's residual | live | |
| 26-R-6 | register | 26 | 26:599–600 | `getHandlerMethods()` coverage assertion does not reach actuator, functional or servlet-registered routes | live | |
| 08-R-x1 | register | 08 | 26:279–280, 26:640–641 | Consistency note for 17: 08 adopted `__Host-` (Internet-Draft) unrecorded while 09 declined `RateLimit` headers over the same | live | |
| 26-G-1 | glossary | 26 | 26:586 | Keyed row | live | |
| 26-G-2 | glossary | 26 | 26:586 | Truncation row | live | |
| 26-G-3 | glossary | 26 | 26:586 | Tier 1 / Tier 2 (by key space, not severity) | live | |
| 26-G-4 | glossary | 26 | 26:586–587 | Session-store miss | live | |
| 26-G-5 | glossary | 26 | 26:587 | Miss budget | live | |
| 26-G-6 | glossary | 26 | 26:587 | Keying window | live | |
| 26-G-7 | glossary | 26 | 26:587 | Untracked source | live | |
| 26-T-1 | trigger | 26 | 26:604–607 (also 26:307–308) | Real mail transport arrives: `min(P, N_usr)` re-argued against attacker-growable `P` | live | |
| 26-T-2 | trigger | 26 | 26:608–611 | Non-root `server.servlet.context-path`: `__Host-` cookie path breaks, total session loss | live | |
| 26-T-3 | trigger | 26 | 26:612–613 | `bytes_per_row` measures above pinned bound, or new keyed row class added | live | |
| 26-T-4 | trigger | 26 | 26:614–615 | Any new audit row emitted from the security filter chain changes `C₁`/`C₂` and `daily` | live | |
| 26-H-1 | handover | 26 | 26:546–550 | Raw request rate on unlisted routes bounded at infrastructure layer; 15.1.3/15.2.2; proven by edge policy | live (tightened by 26:691–692: IPv6 prefix aggregation, ticket 31) | |
| 26-H-2 | handover | 26 | 26:551–554 | Recompute diskspace threshold from §7 formula on mount/population/window change; 15.1.3 | live | |
| 26-H-3 | handover | 26 | 26:555–559 | Alerting and rate computation for row 46 and count fields; 16.3.3 second limb, 2.4.1 detection | live | |
| 26-H-4 | handover | 26 | 26:560–563 | Log inventory records keyed and truncated forms; 16.1.1, precondition for 16.3.3 | live | |

## 2. Per-ticket tally

| ticket | kind | stated | enumerated | note |
|---|---|---|---|---|
| 24 | adr-new | — (none stated) | 1 | Only prose ADR claim is 24:172 |
| 24 | register | — | 26 | No register count stated; rows drawn from §§1–13 and amendments |
| 24 | glossary | — | 1 | "Glossary entry owed" 24:548 |
| 24 | trigger | — | 1 | 24:854 ("Reopening trigger, exact") |
| 24 | handover | — | 0 | No `### Handover items (ticket 25)` heading in 24 |
| 25 | adr-new | 6 (25:791) | 6 | match |
| 25 | adr-amend | — | 1 | from ticket 16 amendment |
| 25 | register | 9 (25:785) | 12 (+1 owned by 09) | **MISMATCH**: 25-R-10/11 come from §10's amendment to 17 (25:775–776); 25-R-12 and 09-R-x1 from inherited §2 (25:418–427); 3 of the 9 duplicate 24's rows |
| 25 | glossary | 2 (25:802) | 2 | match |
| 25 | trigger | 4 (25:797) | 4 (+1 owned by 28) | 28-T-x1 at 25:916–917 is outside the stated four |
| 25 | handover | — | 0 | No declaration heading in 25; see §4 |
| 26 | adr-new | 12 (26:640) | 12 | match |
| 26 | adr-amend | — | 2 | from tickets 29 and 31 amendments |
| 26 | register | 6 (26:640) | 6 (+1 owned by 08) | 08-R-x1 is the "consistency note" 26:640 lists separately from the six |
| 26 | glossary | — (7 terms listed) | 7 | no count stated |
| 26 | trigger | 4 (26:640) | 4 | match |
| 26 | handover | — | 4 | heading at 26:544 |
| all | adr-reversed / adr-rejected | — | 0 | None found in group F |

**The "47 verdicts" figure (ticket 25).** Stated at 25:455–457: "an exhaustive extraction across `issues/` found
**47 verdicts** resting on this document or on deployer action (ASVS, IM8, ARC, NIST and the org standards) plus
**nine obligations with no owner anywhere**", with five L1 IDs among them (6.1.1, 6.3.1, 6.3.2, 6.4.1, 13.4.1,
25:458). Repeated at 25:483 ("the same 47 rows" populate both 17's register and 25's document) and 25:749 ("The 47-row
pass is not here" — deferred to ticket 17's re-split). The 47 rows are **not enumerated anywhere in ticket 25**; the
figure is the result of an extraction not recorded in the file. It is not comparable to this part's counts, which are
owed items per the spec, not verdicts.

## 3. Cross-file effects (targets outside group F)

From 24:
- **05** — `app.cors.allowed-origins` superseded by `app.origins.spa` (24:496, 24:736–737).
- **09** — `source.ip.hash` gets its own key `app.security.hmac.log.key`; "no new key and no new argument" withdrawn; "reasoned about independently at rotation" corrected (24:433–439, 24:738–741). Field renamed `source.ip_hash` (24:879–882, from ticket 13).
- **10** — link-origin property is `app.origins.spa` (24:742).
- **11** — tombstone key "cannot rotate, at all" corrected to "versions accumulate and never retire" (24:455–462, 24:743–744).
- **12** — 24:745 says a key-version column on the tombstone table; **reversed** in-file by 24:845–852 (ticket 12 amendment: no column needed). Ticket 12's rejection of forward-only versioning withdrawn (24:840–843).
- **20** — `.env.production` API origin is a placeholder with allowlist and still-a-placeholder assertions (24:747–748); 20's prod-only assertions must not refresh a context (24:570–576).
- **23** — `app.mfa.totp.encryption.key` length validation moves out of Bean Validation into `@Bean` factory (24:749–751); recommend renaming `key-version` → `key.version` (24:752–756).
- **21** — build-info "which is mild" is a reopening trigger for 21's `info` argument (24:1036–1038; also 25:771).
- **13** — Jackson `INCLUDE_SOURCE_IN_LOCATION` test owned by 13's negative-assertion set (24:888–890); §7's no-value-in-failure rule adopted into 13 (24:919–921).
- **28** — 24's eleventh prohibited entry replaced by ticket 16's content test (24:1058–1061).
- **32** — §9 table missing the dev-only reset-link logger entry (24:1102–1104).

From 25:
- **23** — §8 gains a "not a pathway" row; ASVS 6.4.4 (L2) regraded N/A → satisfied-by-parity; 6.5.6 (L3) added (25:762–766).
- **13** — two new event families for 16.3.3: input-validation rejections, business-logic rule violations (25:736–737, 25:767). Citation note: stdout topology is 13 §12, not §13 (25:894–895, inherited by 28).
- **21** — "which is mild" flagged as reopening trigger (25:771).
- **19** — reopening trigger: saved-plus-issued is the branch if recovery-codes deferral is reversed (25:772–773).
- **07** — 15-character floor recorded as a §3.1.1.2 SHALL barred two ways (25:708–715, 25:774).
- **17** — 13.2.4/13.2.5 as companions; adopted-reading note on §4.2.1 (25:775–776) — captured as 25-R-10/11.
- **08** — `Clear-Site-Data` item taken from body, not handover bullet (25:777–778, 25:826–830).
- **09 / 10** — third throttled counter on recovery-code verification (25:779–780).
- **09** — 25:146–151 "20-minute auto-lift" copied from 09:535–541, corrected to a ladder; NIST-deviation argument superseded by cap (25:953–955, ticket 30).
- **30** — sole-admin recovery path is "ADR 13's three routes (ticket 30 §4)" (25:955); aggregator should resolve which ticket's ADR 13 this is.
- **02** — nothing filed (25:781, 25:608–609).

From 26:
- **08** — stale cookie not self-healing except via CSRF bootstrap; duplicate cookies/ordering/`__Host-` no-`Domain` absent from 08; Internet-Draft inconsistency (26:621–624) → 08-R-x1.
- **09** — budget table explicitly an allowlist; `AuthRateLimiter` third call site/axis; row 5 gains `RATE_LIMITED_SOURCE_MISSES` (26:625–628). "Eviction is a bypass" sentence belongs to 09:1264 §R.6, not 21 (26:360–361, 26:645).
- **11** — `GET /api/hello` exists only in amendment prose at 11:861–863, not in table 11:208–218 (26:462–465, 26:629–630).
- **12** — keyed-row structures bounded at `N_src`/`N_usr`, no schema consequence (26:631).
- **13** — rows 11, 12, 13, 14, 35 keyed; rows 5 and 11 gain reasons; row 46 widened; raw-URI cap numbered (26:632–634).
- **14** — interceptor must handle 429 on stale cookie (26:635–637).
- **16** — twelve tests (26:638–639).
- **17** — twelve ADRs, six register entries, four triggers, consistency note (26:640–641).
- **21** — `daily` is a formula; row 46's `N` discharged; rows 11 and 35 go back for a monitoring story (26:642–645).
- **29** — graduated ticket (26:647–663); 29's amendment adds a CSRF-repository write-side fix (26:671–675).

## 4. Owed-to-25 content outside the declaration heading (not counted as `handover`)

The spec's `handover` kind is limited to bullets under `### Handover items (ticket 25)`; only 26:544 has that heading
in group F. Ticket 25's own required contents and several 24 lines owe items to 25's table in prose. Listed so the
aggregator can decide whether to count them:

- 25 original required contents 1–6: 25:25–56.
- From 11: three contents, 25:88–110 (corrected by 25:945–958, ticket 30).
- From 09: four contents, 25:122–151.
- From 23: five contents, 25:159–202.
- From 24: six contents plus 13.4.1, 25:214–288.
- From 13: five contents plus two sharpenings, 25:298–344.
- From 21: five-item section plus recomputation and framing note, 25:359–387.
- From 09 §R: runner's four items and lockout note, 25:397–442 (runner items superseded in place at 25:891–918).
- From 14 (post-resolution capture): two items, 25:816–824; back-fill scope for 08, 02, 01, 14 at 25:832–838.
- From 15 amendment: step-7 rehearsal replacement and recurrence, 25:906–917.
- From 31 and 30: five and four items declared under their own headings (25:933–936, 25:942), outside group F.
- In 24: 24:283–285 (source-of-record sentence), 24:503 (why-not-shortcut text), 24:522–523 (origin-drift line), 24:757–763 (§14 bullet to 25), 24:782–791 (frontend build owner).

## 5. Note for ticket 17: ADR-shaped decisions in 24 with no ADR claim

Ticket 24 states no ADR set. Decisions that look ADR-shaped but are not claimed as owed: tombstone/log key separation
(24:433–453), tombstone forward-only rotation (24:455–471), deterministic AE declined (24:473–483), generate-on-first-run
rejected (24:306–310), `configtree:` delivery with env override (24:243–299), build-time API origin (24:765–800),
consolidated prohibited-configuration validator (24:529–560). Not rows; flagged for the sizing decision.
