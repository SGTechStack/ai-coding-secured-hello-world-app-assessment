# ADR routing — reconciliation (ticket 34)

Scaffolding. Lives in `.scratch/` and dies with it. The surviving outputs are `docs/adr/README.md` (the reserved
index and the rejection log) and, via ticket 38, the test-plan `rationale` column. Nothing below may be copied into
`docs/` verbatim, because it cites tickets and lines.

Inputs: `deferral-register/inventory/part-A…G.md`, every ADR-kind row (`adr-new`, `adr-amend`, `adr-reversed`,
`adr-rejected`) plus each part's cross-file effects. Zoomed at source where a gist was not enough: 17:48–73,
17:269–307, 17:502–605, 19:271–286, 22:626–653, 24:130–137, 24:413–414, 24:484–490, 03:253–270, 09:124–127,
the PRD in full, and the ID/control columns of `docs/test-plan/test-plan.md`.

**Row keys.** Inventory IDs collide across parts (`11-M-1`, `11-M-2`, `23-M-1`, `23-M-2`, `03-X-1`, the G-part
restatement of `06-A-1…5`). Every row below is keyed `part:id`, e.g. `B:11-M-1`.

## 1. How the filter was applied

The filter is 17 §1's one question: **would a maintainer reading the code and the spec plausibly undo this?**

17 §1 also routes two filter-passing invariants (`CompromisedPasswordChecker` not a bean,
`alwaysPerformAdditionalChecksOnUser = true`) to a test-plan rationale rather than an ADR. A maintainer would
plausibly undo both. So "plausibly undo" is read as **undo and get away with it**. Three outcomes follow:

| Filter answer | Destination |
|---|---|
| No: nothing in the code invites the reversal, or reversing it is harmless | register, spec or handover, plus a rejection-log line |
| Yes, but a named test fails at the moment of reversal and one standalone sentence carries the whole reason | test-plan row + rationale, plus a rejection-log line |
| Yes, and the reason needs options weighed or consequences stated (no single sentence carries it, or no test stops it) | **ADR** |

This reading is a judgement recorded here so tickets 35/36/39–43 can challenge it by ID. Its effect is to send
26 rejection-log rows to a test-plan rationale, because a named `T-…` row already pins the invariant.

**Grouping.** A decision is the set of owed bullets that would be reversed together (17 §1). Where two bullets
touch one component but reverse separately, they stay separate (for example, the 72-byte ceiling and the pepper
decline). Where one bullet is the mechanism or the consequence of another, they merge.

**Owner.** 17 §2's rule: the side that owns the decision's earliest *resolved* statement. One exception, argued
at ADR-072.

## 2. The ADR set — 74 decisions

`also →` names the other destinations the same decision owes. "Register" means a row handed to ticket 33 (§5).

### Owner 35 — 28 ADRs

#### Written by ticket 35 (passwords, hashing, credential tokens) — 9

| ADR | Title | Merged sources | Attached amendments | Filter answer | also → |
|---|---|---|---|---|---|
| ADR-001 | BCrypt at cost 12 behind `DelegatingPasswordEncoder`, not Argon2id | A:02-A-3, A:04-A-2, B:07-A-1, B:07-A-9, G:17-A-3 | A:02-M-1, A:07-M-a | Yes: the standard prefers Argon2id and the admin recipe hard-codes it, so a maintainer holding either would switch. | register (standard deviation) |
| ADR-002 | 15-character password minimum, overriding the PRD's 12 | D:19-A-3, A:02-A-2, B:07-A-7 (floor half) | B:07-M-1, B:07-M-7, C:07-M-x2 | Yes: PRD Story 1 says 12, and a maintainer holding the PRD would lower it. | register (PRD Story 1 AC1) |
| ADR-003 | Reject passwords over 72 UTF-8 bytes rather than pre-hash | B:07-A-2, C:10-A-7 | B:07-M-4, C:07-M-x1 (6.2.9 part) | Yes: a maintainer seeing the byte cap would pre-hash to lift it, which reopens the known BCrypt pre-hash pitfalls. | register (ASVS 6.2.9 (L2) knowingly failed); T-CRED-001 |
| ADR-004 | No pepper or keyed pre-hash | B:07-A-10 | B:07-M-3, B:07-M-6, C:07-M-x3 | Yes: NIST SP 800-63B-4 SHOULD and OWASP both recommend one; a maintainer would add it. | register (declined SHOULD) |
| ADR-005 | A zxcvbn score-3 strength gate replaces composition rules | B:07-A-3, B:07-A-5 | B:07-M-2 | Yes: the recipes carry composition regexes, and a maintainer holding them would put them back or drop the gate as non-NIST. | T-CRED-004 |
| ADR-006 | Admins issue credentials by single-use token, never by generated password | A:04-A-3, C:10-A-2, C:10-A-3 | C:07-M-x1 (generator deleted) | Yes: the admin recipe generates a plaintext password, and a maintainer would restore it. | register (Std §5:472 deviation) |
| ADR-007 | One `credential_tokens` table, domain-separated SHA-256, no HMAC, no Spring OTT | C:10-A-4 | C:10-M-2, C:10-M-3, C:10-M-4 | Yes: the PRD's data model names `password_reset_tokens`, and Spring's OTT is the obvious framework swap. | register (PRD data model); T-CRED-011, T-CRED-012 |
| ADR-008 | A self-service password-change endpoint exists and always requires the current password | C:10-A-5 | — | Yes: the standard's forced-change diagram submits only a new password, and verifying through `AuthenticationManager` is the idiomatic swap. *Amended by ticket 35: the draft's "the standard admits no such endpoint" is false — Std §2 Happy Path 13, §2 Decision Logic, §3.1 and §5 Self-Service Password Change Tests all provide one; §2:121 bars generic profile updates and §4:401 constrains reset, not change. R-CRED-013's `fail` grade rests on that misreading (amendment owed to ticket 33).* | register (Std §2:121, §2:166–167) |
| ADR-009 | Reset redemption clears the password lockout, never TOTP state | C:10-A-6 | C:10-M-1, C:09-M-x1 | Yes: a maintainer would either clear both axes or neither. *Amended by ticket 35: the standard is not silent — Std §2 Decision Logic (line 132) says an admin-reset locked account "remains locked until the lock expires or the administrator explicitly unlocks it". Clearing at redemption of an admin-issued token is a deviation, recorded in ADR-009; R-STD-024 ("undefined") understates it (amendment owed to ticket 33).* | T-LCK-017, T-CRED-024 |

#### Written by ticket 39 (lockout, throttling, source keying) — 11

| ADR | Title | Merged sources | Attached amendments | Filter answer | also → |
|---|---|---|---|---|---|
| ADR-010 | Dual rate limiting: per-account lockout and per-source throttling as independent limiters | A:04-A-4, G:17-A-4 | — | Yes: the standard's Q16 advises against per-IP limiting behind NAT, so a maintainer would drop one limiter. | — |
| ADR-011 | Escalating lockout ladder 20/40/60 minutes instead of the PRD's flat 15 | B:09-A-1, B:09-A-15 | B:09-M-1, B:09-M-8, G:31-M-2 | Yes: 17 §1 names this case. A maintainer holding PRD Story 3 would put 15 minutes back. | register (PRD Story 3 AC1); T-LCK-010, T-LCK-011 |
| ADR-012 | Failures count only inside an observation window | B:09-A-2 | B:09-M-2 | Yes: the standard defines no window, and a maintainer would remove it as an unrequested addition. | T-LCK-001, T-LCK-013 |
| ADR-013 | NIST §3.2.2 cap: the password authenticator is disabled after 100 consecutive failures | B:09-X-1, E:09-X-E1, B:09-A-11 | — | Yes: 100 against a 5-attempt lockout looks redundant, and the consecutive-versus-cumulative split with the TOTP axis invites "harmonising". | register (NIST §3.2.2 satisfied); T-LCK-016, T-LCK-018 |
| ADR-014 | No sleep-based progressive delay | B:09-A-6 (delay half) | B:09-M-3 | Yes: progressive delay is a textbook control a maintainer would add. It exhausts the request thread pool. | register (bot detection and `RateLimit` headers declined, the other two halves of 09-A-6) |
| ADR-015 | A per-source cardinality axis caps distinct accounts driven into lockout | B:09-A-16 | B:09-M-7, B:09-M-9, G:31-M-1 | Yes: a third limiter with first-insertion expiry reads as over-engineering. | register (09-R-2, 09-R-3, 31-R-10); T-RL-006–009, T-RL-030, T-RL-031 |
| ADR-016 | Narrow multi-authenticator reading of NIST §3.2.2: a tier-2 factor disable forces password rebinding | B:09-A-17 | — | Yes: the wider reading is the one a reviewer reaches first. | — |
| ADR-017 | The budget table stays a request-rate allowlist; unlisted routes are metered on session-store misses | F:26-A-1, G:15-A-2 | F:26-M-2 | Yes: default-deny budgeting is the reflex a maintainer would apply, and it would throttle `/api/admin/**`. | register (26-R-1); handover (26-H-1); T-RL-016 |
| ADR-018 | Unmatched paths are not rejected before `CsrfFilter` | F:26-A-2 | — | Yes: an early 404 filter looks like free hardening. It is a route oracle. | — |
| ADR-019 | A two-tier emitter bound caps audit volume, because a limiter cannot | F:26-A-3 | F:26-M-2 | Yes: a maintainer would try to bound audit volume with the existing limiter. | T-AUD-033–037 |
| ADR-020 | Source key: IPv6 aggregated to /64 by one property, from one resolver, with the raw-address path banned | G:31-A-1 | — | Yes: keying on the full address is the default and looks more precise. | register (31-R-4–9, 31-R-11); T-RL-025–029, T-CFG-037 |

#### Written by ticket 40 (MFA factor) — 8

| ADR | Title | Merged sources | Attached amendments | Filter answer | also → |
|---|---|---|---|---|---|
| ADR-021 | Session-scoped factor authority instead of per-request possession proof, with bounded validity | D:19-A-1, E:22-A-1, E:23-A-3 | E:19-M-E1, E:19-M-E2, D:19-M-4 | Yes: MFA_Core §4.1 says "on each request", and the admin-read rule's session-length `validDuration` looks like a bug. | register (23-R-15 residual); T-CFG-019, T-MFA-008 |
| ADR-022 | The TOTP seed encryption key lives outside the database (AES-GCM, key-version column) | D:19-A-2 | D:19-M-1, C:19-M-x1, E:19-M-E3 | Yes: MFA_Core puts the key in the database, and a maintainer would follow it. | register (13.3.1 fail inherited); handover (key rotation) |
| ADR-023 | TOTP is required for administrators and offered to no one else | D:19-A-4 | — | Yes, both ways: a maintainer holding the PRD ("MFA out of scope") would remove it, and one holding `MFA_Frontend/Standalone` would extend it to all users. | register (PRD Out-of-scope; ASVS 6.3.3 (L2) relaxed) |
| ADR-024 | Recovery codes deferred; two enrolled admins and break-glass compensate | D:19-A-6 | D:19-M-5, D:19-M-6 | Yes: recovery codes are the expected companion to TOTP, and adding them naively inverts containment while email is stubbed. | register (deferral) |
| ADR-025 | Provisioning returns a JSON envelope with a manual-entry secret, and the SPA renders the server PNG | E:23-A-1, D:14-A-7 | — | Yes: the standard returns PNG bytes, and client-side QR rendering is the obvious frontend alternative. | T-FE-003, T-FE-025 |
| ADR-026 | Factor rules are hand-composed per matcher, role first, not `@EnableMultiFactorAuthentication` | E:23-A-2 | D:19-M-2, E:23-M-2, G:15-M-1 | Yes: the annotation is Spring Security 7's documented idiom. | register (TM-04 single-layer residual); T-MFA-002, T-MFA-018 |
| ADR-027 | Two-tier TOTP lockout: tier 1 auto-lifts, tier 2 disables cumulatively | E:23-A-7 | E:23-M-1, D:23-M-2 | Yes: MFA_Core prescribes one sliding-window lock. | register (22-R-7, 22-R-9); T-MFA-020, T-MFA-021 |
| ADR-028 | A context prefix inside the plaintext substitutes for AES-GCM AAD | E:23-A-9 | D:19-M-3, C:19-M-x2 | Yes: a maintainer seeing a prefix in the plaintext would move it to AAD, which `AesGcmBytesEncryptor` cannot take. | T-MFA-003, T-MFA-017 |

### Owner 36 — 46 ADRs

#### Written by ticket 36 (error envelope, sessions, CSRF) — 13

| ADR | Title | Merged sources | Attached amendments | Filter answer | also → |
|---|---|---|---|---|---|
| ADR-029 | Server-side session cookies, not JWT | G:17-A-1 | — | Yes: JWT is the common SPA default, and the PRD documents it as the alternative. | — |
| ADR-030 | Spring Session JDBC, not Redis or container sessions | G:17-A-2 | — | Yes: a maintainer would swap the store without seeing that password reset needs principal-indexed invalidation. | T-SES-009 |
| ADR-031 | One RFC 9457 envelope from one writer; `sendError` prohibited | A:06-A-1, A:06-A-2, G:06-A-1, G:06-A-2 | — | Yes: the login recipe prescribes `sendError` and `BasicErrorController`. | spec (error contract); T-AUTH-001, T-AUTH-008–011 |
| ADR-032 | Enumeration-resistant two-step registration: uniform 202, password set at activation | A:06-A-3, G:06-A-3, C:10-A-1, G:17-A-5 | A:06-M-1, A:06-M-2, A:06-M-3 | Yes: 17 §1 names this case. A maintainer holding the PRD would "fix" Story 1 AC3 back. | register (PRD Story 1 AC1, AC2; ASVS 6.3.8 (L3) username axis); T-AUTH-014 |
| ADR-033 | Password lockout is never a wire code; the factor lock is, behind the password | A:06-A-5, G:06-A-5, D:14-A-2 | — | Yes, both ways: a maintainer would add a "locked" response at login, or harmonise the factor's `LOCKED` away. | T-AUTH-006, T-MFA-019 |
| ADR-034 | A failed login does not invalidate an existing session | B:08-A-1 | — | Yes: the standard's Failure Path 1 prescribes invalidation. | register (standard defect four) |
| ADR-035 | Self-service credential change terminates the user's other sessions, not the current one | B:08-A-2 | — | Yes: the standard says terminate all sessions. | T-SES-012, T-SES-013 |
| ADR-036 | Session-bound synchronizer CSRF token, header only, with the same-site constraint it imposes | B:08-A-4, G:17-A-7 | — | Yes: a cookie-based `CsrfTokenRepository` is the SPA norm, and the `_csrf` parameter is Spring's default fallback. | T-CSRF-001, T-CSRF-004 |
| ADR-037 | Sessions are invalidated on admin disable, role change, delete, lockout and cap disable | B:08-A-6 | B:08-M-2, B:08-M-3, C:08-M-x1 | Yes: the standard lists fewer triggers, and each extra row looks optional. | T-SES-003, T-SES-004, T-SES-016–021 |
| ADR-038 | The session id rotates on factor grant and credential change, with one `AUTH_INSTANT` stamping point | B:08-A-7 | B:08-M-1, B:08-M-4, B:08-M-5 | Yes: the filter-ordering invariant is invisible in code and breaks silently. | T-SES-005, T-SES-006, T-SES-034, T-AUD-015 |
| ADR-039 | Session invalidation is dispatched after commit, backed by an idempotent reconciliation sweep | B:08-A-9 | — | Yes: a maintainer would move the dispatch inside the transaction for "atomicity", which `REQUIRES_NEW` cannot give. | T-SES-022 |
| ADR-040 | Anonymous sessions are created on demand, by exactly one route | G:29-A-1, D:14-A-5 | — | Yes: prefetching the CSRF token on app load is the common SPA pattern, and it creates a session row per visitor. | T-SES-026, T-SES-027, T-CSRF-008 |
| ADR-041 | Anonymous-session shedding: a count line and a free-space reserve | G:29-A-3, G:29-A-4, G:29-A-5 | — | Yes: shedding denies new logins, and the count cap looks redundant beside the disk reserve. | register (29-R-2–5); handover (29-H-2); T-RL-024 |

#### Written by ticket 41 (admin module, data model) — 12

| ADR | Title | Merged sources | Attached amendments | Filter answer | also → |
|---|---|---|---|---|---|
| ADR-042 | Two roles, one per user: YAML is the truth, and a seeded read-only `roles` table enforces the foreign key | C:11-A-1 | C:11-M-15 | Yes: the PRD models role as an enum column, and a role hierarchy is the framework reflex. | register (PRD data model); T-ADM-011, T-ADM-017 |
| ADR-043 | The authorization matrix is adopted narrowly, whitelist first | C:11-A-3 | C:11-M-10, C:11-M-13 | Yes: the RBAC recipe registers guards before the whitelist. | register (ac-4 revoke half deferred); T-ADM-006, T-ADM-007, T-ADM-012 |
| ADR-044 | Deletion leaves a tombstone: HMAC of the email, plaintext username, indefinite retention | C:11-A-7 | C:11-M-7 | Yes: PRD Story 11 says the account "is removed". | register (PDPA framing; TM-09); T-ADM-002, T-ADM-004 |
| ADR-045 | Identifier canonicalisation: NFC, trim, lowercase, no dot or tag folding; the canonical value replaces the stored one | C:11-A-8, C:12-A-7 | C:11-M-8, C:11-M-9 | Yes: Gmail-style folding, or preserving the submitted case, are both likely "fixes". | register (seam 6; RFC 5321 case); T-CRED-006, T-CRED-007 |
| ADR-046 | Forced-change credentials expire lazily at login after 30 days, refused before the password check, not by a reaper | C:11-A-11, 44 §Answer | C:11-M-1, C:11-M-6, C:11-M-12 | Yes, twice: a scheduled reaper is the expected shape, and `isCredentialsNonExpired()` is the idiomatic home for credential expiry, which is exactly the post-authentication slot that would turn the audit reason into a password oracle. | register (R-AUD-018, R-LCK-001); T-ADM-015, T-ADM-030 |
| ADR-047 | Bootstrap: refresh-phase validation and runner seeding, one admin, conditional on a second enrolled admin | C:11-A-12 | C:11-M-11, C:11-M-14 (bootstrap part), G:30-M-1 | Yes: Flyway seeding is the obvious simplification. | handover (30-H-1); T-ADM-022–026 |
| ADR-048 | Two-admin invariant: guarded on mutation paths, monitored everywhere | C:11-A-13 | C:11-M-2, C:11-M-3, C:11-M-4, C:11-M-5, C:11-M-14, B:11-M-2, G:30-M-2, G:15-M-2 | Yes: 17 §1 names it. | register (12-R-2 phantom residual; TM-08); T-ADM-010, T-ADM-014 |
| ADR-049 | Factor reset is exempt from the two-admin count | G:30-A-1 | — | Yes: exempting one admin mutation from the invariant looks like a hole. | T-ADM-027–029 |
| ADR-050 | UUIDv4 as the sole primary key, generated by the application, not UUIDv7 | C:12-A-1 | — | Yes: v7 is the modern index-friendly default, but it discloses creation time. | T-CRED-022 |
| ADR-051 | `ddl-auto: validate` is demoted; the schema gate is a negative-test set with `NAMED` validation | C:12-A-3 | C:12-M-1, C:12-M-2 | Yes: a maintainer trusting `validate` would delete the negative tests as redundant. | T-CFG-002–006 |
| ADR-052 | Tombstone HMAC key: forward-only versioning, old versions never retire | C:12-A-11 | C:24-M-x1 | Yes: "rotate every key" is the standing policy, and this key cannot follow it. | register (24-R-18; 11.2.2 partial); trigger (12-T-3) |
| ADR-053 | MFA enrolment is row existence on shared-key tables, not a flag | C:12-A-12 | — | Yes: `im8-review` greps for an MFA flag on the user, so a maintainer chasing a green scan would add one. | register (12-R-7 false negative) |

#### Written by ticket 42 (logging, observability, configuration, topology) — 10

| ADR | Title | Merged sources | Attached amendments | Filter answer | also → |
|---|---|---|---|---|---|
| ADR-054 | Log correlation fields are keyed hashes: `source.ip_hash` over the source key and `session.hash`, never the address | D:13-A-3, A:13-A-a, A:03-A-1, G:31-A-2 | D:13-M-2, G:31-M-3 | Yes: the AuthN recipe logs cleartext `source.ip`, and `Log_Schema.md` prescribes plain SHA-256. | register (13-R-13, 13-R-17); T-AUD-006, T-AUD-008, T-AUD-041 |
| ADR-055 | One data-driven `AuditEvent` enum and a single `emit`, not one method per event | D:13-A-5 | — | Yes: the typed-module recipe prescribes one method per event. | T-AUD-007, T-AUD-017 |
| ADR-056 | The audit stream is duplicated to stdout, against the separate-destination constraint | D:13-A-7 | — | Yes: a maintainer holding the org constraint would remove the console copy. | register (ASVS 16.2.3 by documentation) |
| ADR-057 | Reset links are logged only in `dev`, by a non-audit logger with three enforcement controls | D:13-A-8 | — | Yes: PRD Story 6 says the stub logs the link, so a maintainer would enable it everywhere. | register (PRD Story 6 AC2); T-AUD-030, T-CFG-009–011 |
| ADR-058 | `SameSite=Strict`, not the standard's `Lax` | E:20-A-1 | — | Yes: the standard mandates `Lax` and prescribes a test that fails by design. | register (Std §5:499); T-SES-002 |
| ADR-059 | Two origins, not Boot serving the bundle, and therefore no CSP nonce | E:20-A-2, E:20-A-4 | — | Yes: single-origin serving is simpler and would re-enable nonces. | trigger (20-T-1) |
| ADR-060 | Document CSP is delivered in three layers, with a templated meta tag | E:20-A-3 | — | Yes: CSP intersection makes the layering look redundant. | T-HDR-003, T-HDR-004 |
| ADR-061 | Actuator exposes `health` only, read-only, and metrics are pushed over OTLP | E:21-A-1, E:21-A-2 | — | Yes: exposing `/actuator/prometheus` is the default monitoring pattern. | T-OBS-009–012 |
| ADR-062 | Secrets bind through `@Validated @ConfigurationProperties`, not `@Value` | F:24-A-1 | — | Yes: IM8 as-8 names `@Value`, and `im8-review` greps for it. | register (24-R-3 false negative; 24-R-21 Binder gap); T-CFG-023 |
| ADR-063 | Inbound trace context is restarted at the application boundary | G:27-A-1, G:15-A-3 | — | Yes: continuing inbound `traceparent` is the framework default. | handover (27-H-1); T-OBS-002–004, T-OBS-015 |

#### Written by ticket 43 (test harness, threat model, handover design, recovery runner) — 11

| ADR | Title | Merged sources | Attached amendments | Filter answer | also → |
|---|---|---|---|---|---|
| ADR-064 | The threat model is an artefact of the plan, not the build | G:15-A-1 | — | Yes: a maintainer would delete a pre-build model as stale, and IM8 pm-6's diagram refusal rests on it. | — |
| ADR-065 | Security-control tests boot the full context; `@WebMvcTest` and all slices are banned | D:16-A-1 | — | Yes: slices are the documented fast path for controller tests. | — |
| ADR-066 | A forward-only mutable clock and clock-built cache adapters are production design | D:16-A-2 | — | Yes: production code shaped for tests is the first thing a simplifier removes. | T-ARCH-001, T-RL-018 |
| ADR-067 | No `test` profile; tests override capture beans in the default context | D:16-A-5 | — | Yes: `application-test.yml` is the Boot convention. | — |
| ADR-068 | A traceability gate binds each test to a canonical table row with `@Proves` | D:16-A-6 | — | Yes: an annotation gate over a markdown table looks like ceremony. | T-BLD-007, T-BLD-008 |
| ADR-069 | One generated source table renders both the register and the handover document, gated for drift in `verify` | F:25-A-1 | F:25-M-1 | Yes: two hand-maintained documents is the default. | T-BLD-006 |
| ADR-070 | Break-glass recovery as NIST §4.2.2.2 option 2, under an adopted reading of §4.2.1 | F:25-A-4 | — | Yes: the reading is contestable, and the other reading changes what counts as recovery. | register (25-R-11 adopted-reading note) |
| ADR-071 | A distinct recovery address, not recovery contacts or saved codes | F:25-A-5 | — | Yes: recovery contacts are a NIST-listed alternative a maintainer would reach for. | register (25-R-2, 25-R-3) |
| ADR-072 | Operator recovery is an offline same-jar runner run inside a planned outage | G:28-A-1, B:09-A-14 | B:09-M-6, G:28-M-1 | Yes: an in-app admin endpoint or a live loopback listener is the obvious "no outage" fix. | handover (28-H-1–8); T-RUN-004, T-RUN-010 |
| ADR-073 | The runner takes a credential in and emits nothing; batch mode mints nothing | G:28-A-2 | — | Yes: printing a one-time password is the obvious runner output, and it was the earlier design. | register (28-R-1, 6.4.6 scoped withdrawal); T-AUD-027, T-RUN-013 |
| ADR-074 | Runner changes are bound by a plan/apply digest | G:28-A-3 | — | Yes: a dry-run digest reads as ceremony for a single-operator tool. | T-RUN-011 |

**ADR-072's owner.** Its earliest statement is 09 §R.3 (owner 35), but ticket 28 inverted the process model, and
only one of 09's four constraints survives. The decision a maintainer would reverse, an offline runner in a
planned outage, is 28's. Ticket 36's own scope names "the recovery runner". So it goes to 36. This is the one
departure from 17 §2's rule.

## 3. Rejection log (filter said no, or a test carries it)

These lines go into `docs/adr/README.md` as written there. The `part:id` → `REJ-` map is here only.

| REJ | Candidate rows | Went to |
|---|---|---|
| REJ-001 | A:03-A-2 | spec §Logging |
| REJ-002 | A:03-A-3 | spec §Audit event catalogue |
| REJ-003 | A:04-A-5, G:17-A-6 | register; spec §Data model |
| REJ-004 | B:07-A-4 (+ B:07-M-5, C:07-M-x1 word-list part) | spec §Password policy; handover (blocklist refresh); register (07-R-6) |
| REJ-005 | B:07-A-6 | T-AUTH-013 |
| REJ-006 | B:07-A-7 (property-prefix half) | register (with 04-R-7) |
| REJ-007 | B:07-A-8 | T-CRED-002; register (currency note) |
| REJ-008 | B:08-A-3 | T-CFG-032 |
| REJ-009 | B:08-A-5 | register (08-R-2, 08-R-3) |
| REJ-010 | B:08-A-8 (+ B:08-M-6) | T-HDR-005; register; handover |
| REJ-011 | B:08-J-1 | spec §Session lifecycle |
| REJ-012 | B:08-J-2 | spec §Session lifecycle |
| REJ-013 | B:09-A-4 | register (PRD Story 3 AC3) |
| REJ-014 | B:09-A-5 (+ B:09-M-10) | register (ASVS 6.1.1 verdict) |
| REJ-015 | B:09-A-7 | T-CFG-025; handover (name trusted proxies) |
| REJ-016 | B:09-A-8 | spec §Credential flows; handover |
| REJ-017 | B:09-A-9 | spec §Data model |
| REJ-018 | B:09-A-10 | register (16-R-5 fidelity) |
| REJ-019 | B:09-A-12 | T-LCK-009 |
| REJ-020 | B:09-A-13 | spec §Data model |
| REJ-021 | B:09-A-18 | T-ADM-022 |
| REJ-022 | C:10-A-8 | T-CRED-016, T-CRED-017, T-CRED-018 |
| REJ-023 | C:11-A-2 | register (04-R-5, 11-R-13); spec §Authorization |
| REJ-024 | C:11-A-4 | T-CFG-024 |
| REJ-025 | C:11-A-5 | register (Story 10 reading of Std §3.5) |
| REJ-026 | C:11-A-6 | register (N/A, batch reset) |
| REJ-027 | C:11-A-9 | spec §Identifiers; register (11-R-16) |
| REJ-028 | C:11-A-14 | spec §Audit event catalogue |
| REJ-029 | C:11-A-15 | register (population declaration) |
| REJ-030 | C:12-A-2 | register (seam register rows) |
| REJ-031 | C:12-A-4 | spec §Data model; register (seam 2) |
| REJ-032 | C:12-A-5 | spec §Data model |
| REJ-033 | C:12-A-6 | T-MFA-003, T-MFA-004 |
| REJ-034 | C:12-A-8 | T-ADM-021 |
| REJ-035 | C:12-A-9 | spec §Data model |
| REJ-036 | C:12-A-10 | register (residual); trigger (12-T-2) |
| REJ-037 | C:12-A-13 | register; trigger (12-T-1) |
| REJ-038 | C:12-A-14 | register |
| REJ-039 | C:12-A-15 | register (with the account-hygiene deferral) |
| REJ-040 | C:12-A-16 | T-BLD-001 |
| REJ-041 | C:12-A-17 | spec §Data model |
| REJ-042 | D:13-A-1 | T-AUD-025; register (13-R-12 residual) |
| REJ-043 | D:13-A-2 | spec §Audit event catalogue |
| REJ-044 | D:13-A-4 (+ C:03-M-x1) | register (13-R-5 schema-extension request) |
| REJ-045 | D:13-A-6 (+ D:13-M-1) | T-AUD-011; register (13-R-4); handover |
| REJ-046 | D:13-A-9 | register (B:13-R-1 idle-expiry decline) |
| REJ-047 | D:13-A-10 | spec §Standards precedence |
| REJ-048 | D:13-A-11 | none: a process rule for amending tickets, with no subject once the plan is handed off |
| REJ-049 | D:14-A-1 | spec §Error contract; register (off-label 423) |
| REJ-050 | D:14-A-3 | spec §Error contract |
| REJ-051 | D:14-A-4 | T-FE-017 |
| REJ-052 | D:14-A-6 | spec §Frontend |
| REJ-053 | D:14-A-8 | spec §Frontend |
| REJ-054 | D:14-A-9 | register |
| REJ-055 | G:15-J-1 | spec §Authorization (matrix row) |
| REJ-056 | D:16-A-3 | T-AUTH-003; register (16-R-14) |
| REJ-057 | D:16-A-4 | spec §Test harness |
| REJ-058 | D:16-J-1 | spec §Test harness |
| REJ-059 | D:19-A-5 | register (22-R-11, 22-R-13); spec §Error contract |
| REJ-060 | E:20-A-5 | T-HDR-004 |
| REJ-061 | E:20-A-6 | T-SES-011; register (08-R-x1 draft-status note) |
| REJ-062 | E:20-A-7 | register (04-R-10, 20-R-3) |
| REJ-063 | E:21-A-3 (+ E:21-M-1, G:29-M-1) | spec §Observability; handover (21-H-3) |
| REJ-064 | E:21-A-4 (+ E:21-M-2) | handover (21-H-1, 21-H-7); spec §Observability |
| REJ-065 | E:21-A-5 | register (21-R-6) |
| REJ-066 | E:21-A-6 | T-AUTH-017 |
| REJ-067 | E:21-A-7 | T-OBS-005 |
| REJ-068 | E:21-A-8 | spec §Test harness |
| REJ-069 | E:23-A-4 | register; spec §MFA endpoints |
| REJ-070 | E:23-A-5 | register; spec §MFA endpoints |
| REJ-071 | E:23-A-6 | spec §MFA endpoints |
| REJ-072 | E:23-A-8 | T-LCK-005 |
| REJ-073 | E:23-A-10 (+ D:23-M-1) | register (06-R-1, 06-R-2, 23-R-14) |
| REJ-074 | E:23-A-11 | T-MFA-011; register (23-R-1) |
| REJ-075 | F:25-A-2 | spec §Register and handover schema |
| REJ-076 | F:25-A-3 | spec §Register and handover schema |
| REJ-077 | F:25-A-6 | spec §Register and handover schema |
| REJ-078 | F:26-A-4 | spec §Audit event catalogue |
| REJ-079 | F:26-A-5 | spec §Audit event catalogue |
| REJ-080 | F:26-A-6 | spec §Rate limiting; trigger |
| REJ-081 | F:26-A-7 | T-AUD-024 |
| REJ-082 | F:26-A-8 | register |
| REJ-083 | F:26-A-9 | T-SES-031 |
| REJ-084 | F:26-A-10 (+ F:26-M-1) | spec §Observability; handover (26-H-2) |
| REJ-085 | F:26-A-11 | T-ARCH-005; register (26-R-6) |
| REJ-086 | F:26-A-12 | register |
| REJ-087 | G:27-J-1 | spec §Logging |
| REJ-088 | G:27-J-2 | spec §Logging |
| REJ-089 | G:28-A-4 | spec §Recovery runner |
| REJ-090 | G:28-A-5 | spec §Audit event catalogue |
| REJ-091 | G:29-A-2 | T-SES-032; register (29-R-1) |
| REJ-092 | A:06-A-4, G:06-A-4 | spec §Error contract; register (MFA_Core §3.2 deviation) |

92 lines, covering 94 inventory rows.

## 4. Amendments attached to a non-ADR destination, and explicit drops

**Amendments whose target is not an ADR.** Amendments never become files. These follow their target:

| Row | Target | Goes to |
|---|---|---|
| C:06-M-x1, C:06-M-x2 | the closed error-code enum | spec §Error contract |
| C:03-M-x2 | `uuid` mapping in the log schema | spec §Data model; register |
| C:23-M-x1 | `pending_totp` columns | spec §Data model |
| D:23-M-1 | off-label statuses | register (REJ-073) |
| G:28-M-2 | a prohibited-configuration entry | register (the entry it replaces was withdrawn) |
| B:07-M-5, B:08-M-6, B:09-M-10, D:13-M-1, E:21-M-1, E:21-M-2, F:26-M-1, G:29-M-1, C:03-M-x1 | a rejected candidate | with that candidate (§3) |

**Explicit drops.** Each cites what superseded it. None of these is live.

| Row | Why dropped |
|---|---|
| A:02-A-1 | Subject gone: the admin password generator it would govern was deleted (07:531–533, 10:651–653). ADR-006 replaces generated passwords with tokens. |
| A:04-A-1 | Superseded by ADR-058 (20:322–323). |
| B:09-A-3, B:09-M-4 | Superseded by 09:1393 (the cap was implemented). See ADR-013. |
| B:09-M-5 | Superseded by 09:1193–1200 and 09:1392–1393. See ADR-014. |
| B:09-M-11 | Superseded by 09:1075–1082 and 09:1373–1378. |
| B:11-M-1 | Superseded by 09:1568–1572 (B:11-M-2, attached to ADR-048). |
| C:11-A-10, C:11-X-1 | Withdrawn by 11:613 (10:604–606). ADR-006 records it as the rejected alternative. |
| C:12-X-1 | Withdrawn by 12:1008, and already folded into 12-A-11's text. See ADR-052. |
| A:03-X-1, D:03-X-1, D:03-X-2 | Withdrawn by 13:135 (replaced by ADR-054 and REJ-043). |
| E:19-X-E1 | Withdrawn by 22:738. The PIN skip is sanctioned by MFA_Core §4.1 and Q3, and the register carries the N/A (32-R-5). |
| G:15-A-4 | Superseded by 28:423. The token it concerned no longer exists. See ADR-073. |

**Split rows.** B:07-A-7 (floor half → ADR-002, prefix half → REJ-006) and B:09-A-6 (delay half → ADR-014,
bot-detection and `RateLimit`-header halves → register).

## 5. Hand-offs to ticket 33 (register and handover inputs)

Ticket 33 mints the R-IDs. Every row below names what 34 routed. Most already exist as inventory `register` rows,
so this is a checklist, not new content:

- **PRD deviations (§6):** every row, whether or not it also has an ADR.
- **Standard deviations that also have an ADR:** ADR-001, 003, 004, 006, 008, 013, 021, 022, 023, 024, 026, 027,
  032, 034, 041, 042, 043, 044, 045, 048, 052, 053, 054, 056, 057, 058, 062, 070, 071, 073, and ADR-014's other two
  halves. The register row states the deviation and residual. The ADR states the choice. Each cross-references the
  other by ID.
- **Register-only destinations from §3:** REJ-003, 004, 006, 007, 009, 010, 013, 014, 018, 023, 025–027, 029–031,
  036–039, 042, 044–046, 049, 054, 056, 059, 061, 062, 065, 069, 070, 073, 074, 082, 085, 086, 091, 092.
- **Handover destinations from §2 and §3:** ADR-017, 022, 041, 047, 063, 072, and REJ-004, 010, 015, 016, 045,
  063, 064, 084.
- **Triggers carried by ADRs:** the ADR states its own reopening triggers (as 17:285–291 requires for ADR-063). The
  register keeps its trigger rows too. 33 cross-references them by ADR ID.

## 6. PRD deviations — the filter, applied to each (step 4)

Every row gets a register row regardless. The filter decides only whether it also gets an ADR.

| PRD clause | What we did | ADR? |
|---|---|---|
| Story 1 AC1: length ≥ 12 | 15-character minimum | **Yes: ADR-002** |
| Story 1 AC1: account created, `enabled = true`, on submit | Pending registration; the account activates on token redemption | **Yes: ADR-032** |
| Story 1 AC2: clear username/email conflict error | Uniform 202 on the email axis. Username conflict stays specific as `USERNAME_UNAVAILABLE` | **Yes: ADR-032** |
| Story 3 AC1: lock "e.g. 15 minutes" | Escalating 20/40/60 ladder | **Yes: ADR-011** |
| Story 3 AC3: one source cannot lock out a legitimate user | Not met as stated; the state-independence reading is adopted | No: a verdict on a criterion, with nothing in code to "fix back". REJ-013 |
| Story 2 AC3: locked until the lockout expires | A capped authenticator stays refused past expiry, until rebinding | **Yes: ADR-013** |
| Out of scope: MFA | TOTP for administrators, per IM8 ac-2 | **Yes: ADR-023** |
| Story 6 AC2: the stub logs the link | Logged in `dev` only; no channel elsewhere | **Yes: ADR-057** |
| Data model: `password_reset_tokens` | `credential_tokens`, typed | **Yes: ADR-007** |
| Data model: `users.role` enum | `roles` table with a foreign key | **Yes: ADR-042** |
| Story 11: the account "is removed" | Removed, and a tombstone is written | **Yes: ADR-044** |
| Story 12: seed an admin when none exists | Seeded by the runner path, conditional on a second enrolled admin before go-live | **Yes: ADR-047** |
| Stories 9–11: self-action guard only | Also a two-admin invariant (409) | **Yes: ADR-048** |
| Beyond-PRD additions: concurrent-session limit, idle/absolute timeouts, password history, self-service change, activation, admin unlock, admin create | Built, because the standard requires them | Change → **ADR-008**; activation → ADR-032; the rest: no (a maintainer would not remove a standard-mandated control; register rows only) |

## 7. Test-plan rationale sentences (for ticket 38's new `rationale` column)

Standalone, with primary citations only. Ticket 38 inserts them, and may reword for fit but not for substance.

| T-row | Rationale |
|---|---|
| T-AUTH-005 | `alwaysPerformAdditionalChecksOnUser` stays `true` because CVE-2026-22746 showed the account-status path skipping the timing normalisation for disabled, expired and locked accounts; the default reopens that difference. |
| T-AUTH-013 | No `CompromisedPasswordChecker` bean exists because `DaoAuthenticationProvider` would then raise a distinct exception at login, breaking the single failure response ASVS 6.3.8 (L3) requires; breach checking runs where passwords are set instead. |
| T-CRED-002 | The history of three stays although NIST SP 800-63B-4 neither requires nor forbids history, because the governing standard requires it and the standard governs how a control behaves. |
| T-CFG-032 | The session cookie carries no `Max-Age`, because a persistent cookie is an implicit remember-me; the server-side absolute lifetime is the only lifetime control. |
| T-HDR-005 | `Clear-Site-Data` is sent for compliance, but it clears only the API origin's state (the `cookies` directive reaches the registrable domain; `storage` and `cache` do not), so the SPA's own state clearing is the real control. |
| T-CFG-025 | `server.forward-headers-strategy: framework` is prohibited because it believes forwarded headers from any hop; trusted proxies are named explicitly, and startup fails if they are not. |
| T-LCK-009 | The cap refusal runs as a pre-authentication check, so that whether a capped account is refused never depends on the password being correct; a post-authentication placement would make the audit stream a password-correctness oracle. |
| T-ADM-022 | Reserved names (for example `admin`, `root`) are refused as usernames at bootstrap and at registration from one set, as an availability control against squatting, not as an anti-enumeration control. |
| T-CRED-016 | Link origins come from configured `app.origins.spa` only, never from the request, because a Host-derived link lets an attacker mint reset links to their own origin (OWASP Forgot Password Cheat Sheet). |
| T-CRED-017 | As T-CRED-016: `X-Forwarded-Host` is request-controlled input and never shapes a link origin. |
| T-CRED-018 | As T-CRED-016: no body or query field shapes a link origin. |
| T-CFG-024 | `spring.mvc.servlet.path` is prohibited because CVE-2026-22753 makes security matchers and handler mappings disagree under it; admin paths are mapped in controllers as `/api/admin/**`. |
| T-MFA-003 | The TOTP ciphertext column is width-checked with `OCTET_LENGTH`, because `ddl-auto: validate` does not see column widths. |
| T-MFA-004 | The `key_version` range check compensates for the same `validate` blindness as T-MFA-003. |
| T-ADM-021 | Deletion is by `ON DELETE CASCADE` alone, and the tombstone's user and deleter IDs carry no foreign key, because the tombstone must outlive the rows it names. |
| T-BLD-001 | The Spring Session DDL is a verbatim single copy, checked by blob hash with `eol=lf`, so that a vendor upgrade shows up as a diff rather than a silent schema drift. |
| T-AUD-025 | `user.id` is recorded on failed logins for existing accounts, against the logging recipes, because an investigation cannot reconstruct a targeted attack without it; the resulting log-reader existence oracle is a registered residual. |
| T-AUD-011 | The audit appender has no `total-size-cap`, because the cap deletes audit evidence silently and its arithmetic is unreliable; disk exhaustion is monitored instead. |
| T-FE-017 | Sign-out is terminal: the SPA never mints a fresh session after an explicit sign-out, only after idle expiry or eviction. |
| T-AUTH-003 | Timing uniformity is proven by counting `PasswordEncoder.matches()` calls rather than measuring wall-clock time, because timing assertions are flaky and prove nothing on a shared runner (ASVS 6.3.8 (L3)). |
| T-HDR-004 | `'unsafe-inline'` appears in two directives in `dev` only, for Vite's development server; `vite preview` serves the production bundle and is where the production CSP is verified. |
| T-SES-011 | The session cookie is `__Host-`-prefixed outside `dev`, and named per profile because the prefix requires `Secure`, which plain-HTTP local development cannot set. |
| T-AUTH-017 | The health body is exempt from the error envelope because orchestrators parse Actuator's own format; the exemption is pinned to `/actuator/**` and nothing else. |
| T-OBS-005 | `server.tomcat.mbeanregistry.enabled` is `true` because thread-pool saturation is the failure mode the lockout design argues against, and the meters need the registry. |
| T-LCK-005 | One unlock endpoint clears both the password and the TOTP tier-1 lock, and never TOTP tier 2, which only rebinding exits. |
| T-MFA-011 | TOTP accepts one step of skew either way, against ASVS 6.5.5 (L2)'s 30-second lifetime (effective ≈89 s), as a registered deviation for clock drift on user devices. |
| T-AUD-024 | Raw pre-routing `url.path` is capped at 256 characters with a truncation marker (ASVS 16.2.1 (L2)), so an attacker cannot inflate each audit row. |
| T-SES-031 | An explicit `CookieSerializer` bean is paired with the delegating session-id resolver, because registering the resolver otherwise drops the `server.servlet.session.cookie.*` properties. |
| T-ARCH-005 | Endpoint coverage is asserted across three registries, because `getHandlerMethods()` alone misses Actuator, functional and servlet-registered routes. |
| T-SES-032 | Anonymous sessions expire at creation plus the idle interval and are never extended, so an unauthenticated client cannot hold a session row open by pinging. |

**Verification note.** These sentences restate facts the source tickets verified and cited (the CVE mechanisms,
Spring and Tomcat property behaviour, ASVS levels). Ticket 38 must carry each fact's primary citation when it
writes the column, as 17 §0 requires, because `research/` dies at handoff.

## 8. Ticket-local ADR references → surviving IDs (for ticket 38)

The test plan's `clause` column cites 13 ticket-local ADRs. Each maps to:

| Local reference | Maps to |
|---|---|
| 08 ADR 2 | ADR-035 |
| 08 ADR 5 | REJ-009 (register: the two reinterpreted prescribed tests) |
| 08 ADR 6 | ADR-037 |
| 09 ADR 1 | ADR-011 |
| 09 ADR 2 | ADR-012 |
| 09 ADR 3 | ADR-013 |
| 09 ADR 7 | REJ-015 (T-CFG-025 rationale) |
| 10 ADR 4 | ADR-007 |
| 10 ADR 8 | REJ-022 (T-CRED-016–018 rationale) |
| 20 ADR 1 | ADR-058 |
| 20 ADR 3 | ADR-060 |
| 20 ADR 6 | REJ-061 (T-SES-011 rationale) |
| 21 ADR 6 | REJ-066 (T-AUTH-017 rationale) |

For any other `NN ADR k`, look up `NN-A-k` in §2 or §3. Inventory IDs follow the ticket's own numbering wherever
the ticket numbers its list.

## 9. Counts

| | Owner 35 | Owner 36 | Total |
|---|---|---|---|
| ADRs | 28 (35: 9, 39: 11, 40: 8) | 46 (36: 13, 41: 12, 42: 10, 43: 11) | **74** |

Row accounting, all against the inventory:

| Kind | Rows | → ADR (merged source) | → attached amendment | → rejection log | → non-ADR target | → explicit drop | Sum |
|---|---|---|---|---|---|---|---|
| adr-new | 202 (198 live + 4 superseded; includes G's restatement of 06-A-1…5) | 110 | — | 87 | — | 5 | 202 |
| adr-amend | 95 (91 live + 4 superseded) | — | 76 | 9 (with a rejected candidate) | 6 | 4 | 95 |
| adr-reversed | 8 (2 live) | 2 (ADR-013) | — | — | — | 6 | 8 |
| adr-rejected | 6 | — | — | 6 | — | — | 6 |

Conventions behind the sums:

- **Split rows.** B:07-A-7 is counted under ADR (ADR-002) and its other half sits at REJ-006. B:09-A-6 is counted
  under ADR (ADR-014), and its other halves go to the register.
- **Multi-target amendment.** C:07-M-x1 is counted once, under ADR-006. Its 6.2.9 part also informs ADR-003 and its
  word-list part informs REJ-004.
- **Superseded rows merged, not dropped.** G:15-A-3 is superseded by the ADR it equals, so it is counted under
  ADR-063.

A grep of this file for each `part:id` finds every inventory ADR-kind row. Ticket 18 may run that check.

## 10. Why the count is 74

There was no target, and 17 §1 withdrew the 90–110. The 110 ADR-bound rows collapse into 74 decisions by merging.
Of the 87 adr-new rows turned away, 26 went to a test-plan rationale. 17's own seven-item sketch survives in six ADRs
(029, 030, 001, 010, 032, 036) and one rejection (sketch 6 → REJ-003).
