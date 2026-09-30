# Compact index of staged rows (generated)

| key | pillar | kind | requirement | verdict | resp | subject | inv | dup |
|---|---|---|---|---|---|---|---|---|
| SG3-026 | ADM | deviation | Std §5:472 | fail | none | Admin create issues an invite token, not a flagged password | G:32-R-2 |  |
| SH-016 | ADM | deviation | PRD Story 11 AC1 | pass-with-note | application | Deletion writes a tombstone as well as removing the account | routing§6 | ≡? G:17-R-10 (SG1-005, soft-delete tombstones) |
| SH-017 | ADM | deviation | PRD Story 12 AC1 | pass-with-note | application | Admin seeded by a runner; go-live needs a second enrolled admin | routing§6 | ≡? G:30-H-1 (SG3-001) |
| SH-018 | ADM | deviation | PRD Story 9 AC2; PRD Story 10 AC2; PRD Story 11 AC2 | pass-with-note | application | Two-admin invariant added to the self-action guard | routing§6 | ≡? G:30-H-2 (SG3-002); ≡? D:12-R-2 (SD1-028) |
| SA-039 | ADM | n/a | Std §5:473 | n/a | none | Username change and lock via generic update: N/A by construction | A:16-R-a | ≡? C:16-R-x1 |
| SC-021 | ADM | n/a | IM8 ac-12 | n/a | none | SSO for internal services does not apply | C:11-R-2 | ≡? A:01-R-6 |
| SC-022 | ADM | n/a | IM8 ac-8 | n/a | none | Automated account lifecycle does not apply | C:11-R-3 | ≡? A:01-R-6 |
| SC-043 | ADM | n/a | Std §5:473 | n/a | none | Username change and generic lock tests do not apply | C:16-R-x1 | ≡? D:16-R-18; A:16-R-a |
| SD2-027 | ADM | n/a | Std §5:494 | n/a | none | Batch half of the body-size row is N/A | D:16-R-19 | ≡? C:11-A-6 |
| SG3-027 | ADM | n/a | Std §5:473 | n/a | none | No generic user update or lock route exists | G:32-R-3 | ≡? A:16-R-a; C:16-R-x1; D:16-R-18 |
| SG3-028 | ADM | n/a | Std §5:494 (batch half) | n/a | none | Batch password reset is not built | G:32-R-4 | ≡? D:16-R-19 |
| SA-002 | ADM | not-built | IM8 ac-3 | fail | none | Inactive and expired account disablement not built | A:01-R-2; A:04-R-1 | ≡? G:17-R-1; D:16-R-1 |
| SA-003 | ADM | not-built | IM8 ac-4 | partial | none | Access review: baseline declared, scheduled revoke not built | A:01-R-3 | ≡? C:11-R-1 |
| SC-020 | ADM | not-built | IM8 ac-4 | partial | none | Scheduled compare-and-revoke of permissions is deferred | C:11-R-1 |  |
| SD2-018 | ADM | not-built | Std §5 (account-hygiene rows) | — | none | Scheduled account-hygiene jobs are not built | D:16-R-9 | ≡? A:01-R-2; A:01-R-3; A:04-R-1; G:17-R-1; G:16-R-1 |
| SG1-001 | ADM | not-built | Std §5:486; Std §5:487; Std §5:488; Std §5:489; IM8 ac-3 | fail | application | Scheduled account-hygiene jobs not built | G:17-R-1; G:16-R-1 | ≡? D:16-R-9; A:01-R-2; A:04-R-1 |
| SC-023 | ADM | note | IM8 ac-7 | pass | none | Provisioning and deprovisioning are satisfied by admin endpoints | C:11-R-4 | ≡? A:01-R-6 |
| SG3-001 | ADM | obligation | ASVS 6.1.1 | pass-with-note | deployer | Two enrolled admins before go-live | G:30-H-1 | ≡? 25:88–99 (corrected at 25:945–946) |
| SG3-002 | ADM | obligation | Two-admin invariant (application rule; no standard clause) | — | shared | Promote before demote at exactly two enrolled admins | G:30-H-2 | ≡? 25:103–105 (origin 11:271–273) |
| SG3-004 | ADM | obligation | Admin recovery routes (application decision; no standard clause) | — | deployer | Wait out the other admin's lockout before the runner | G:30-H-4 |  |
| SC-040 | ADM | residual | ASVS 16.4.2 | fail | none | Any admin can take over any other admin | C:15-R-x1 | ≡? D:15-R-7; G:15-R-8; G:15-R-8a |
| SC-041 | ADM | residual | IM8 ac-7 | partial | none | The tombstone records no role | C:15-R-x2 | ≡? D:15-R-8; G:15-R-9 |
| SD1-028 | ADM | residual | Two-enrolled-admin invariant (beyond PRD Stories 9–11) | pass-with-note | application | Two-admin guard is decrement-safe, not phantom-safe | D:12-R-2 | ≡? D:16-R-1 (locking fidelity); C:12 text at 12:655–660 |
| SD2-006 | ADM | residual | IM8 ac-7; ASVS 16.4.2; ASVS 16.4.3 | fail | deployer | Any admin can take over any other admin (TM-08) | D:15-R-7; D:15-H-5 | ≡? G:15-R-8; G:15-R-8a |
| SD2-007 | ADM | residual | IM8 ac-7; ASVS 16.1.1 | — | deployer | Tombstone records no role; privileged-deletion evidence expires first (TM-09) | D:15-R-8; D:15-H-6 | ≡? G:15-R-9 |
| SE2-030 | ADM | residual | NIST SP 800-63B-4 §4.1.2.1 (AAL required to bind an additional authenticator) | pass | deployer | Seeded admin binds TOTP at AAL1, leaving a first-enroller race | E:23-R-10; E:23-H-2 | ≡? C:23-R-x1 (11:695–701); 25:179–184 |
| SG1-017 | ADM | residual | IM8 ac-7; ASVS 16.4.2 | partial | deployer | Any admin can take over any other admin; complete at exactly two | G:15-R-8; G:15-R-8a | ≡? C:15-R-x1; D:15-R-7; D:15-H-5 |
| SG1-018 | ADM | residual | IM8 ac-7; ASVS 16.1.1 | partial | deployer | The deletion tombstone records no role | G:15-R-9 | ≡? C:15-R-x2; D:15-R-8; D:15-H-6 |
| SB-025 | ADM | trigger | The lazy 30-day expiry of forced-change credentials, checked after password verification | — | application | 30-day credential expiry reason confirms a guessed password in the audit log | B:11-T-1 | ≡? C:09-T-x1; ≡? D:11-T-1; ≡? D:11-T-2 |
| SC-037 | ADM | trigger | Population declaration (administrators are external, application-local accounts) | — | none | Internal-officer administrators reopen the admin authentication design | C:11-T-1 |  |
| SC-038 | ADM | trigger | Lazy 30-day forced-change expiry checked at login | — | application | The 30-day expiry's audit reason confirms a correct password | C:09-T-x1 | ≡? D:11-T-1; B:11-T-1 |
| SD1-014 | AUD | deviation | ASVS 16.2.3; org separate-destination constraint | pass-with-note | shared | Rebinding runner stdout is a documented log destination | D:13-R-16 | ≡? D:13-A-7 (register half via ADR-056) |
| SG2-013 | AUD | deviation | ASVS 16.2.3 | pass-with-note | shared | The runner's stdout is a documented log destination | G:28-R-7 | ≡? D:13-R-16 |
| SG3-033 | AUD | fidelity | Logging Std §5:374 | partial | none | Logging under load is not exercised | G:32-R-9 |  |
| SD1-012 | AUD | n/a | Std §3.3 (security-header configuration changes; critical configuration changes; significa | n/a | none | Three required audit events with no operation to attach to | D:13-R-14 |  |
| SG3-030 | AUD | n/a | Logging Std §5:352; Logging Std §5:353 | n/a | none | No async boundary for trace or MDC propagation | G:32-R-6 |  |
| SB-012 | AUD | not-built | Std §3.3 | partial | none | Idle session expiry is not audited at the moment of expiry | B:13-R-1 | ≡? D:13-T-2 (scheduled job enters scope) |
| SD2-019 | AUD | not-built | Std §3.3; Logging Std §3.5 | conditional-pass | shared | Durable 90-day audit retention is not built | D:16-R-10 | ≡? D:13-R-3; A:03-R-12; G:16-R-2; G:17-R-5 |
| SG1-014 | AUD | not-built | Std §5:516; Std §3.3 (durable storage for the retention period) | partial | shared | Durable 90-day audit retention beyond the host not built | G:16-R-2; G:17-R-5 | ≡? D:16-R-10; E:13-R-E1 |
| SG3-032 | AUD | not-built | Logging Std §5:358 | partial | none | No general request log | G:32-R-8 | ≡? G:27-H-2 |
| SF1-022 | AUD | note | Std §3.4 | — | application | Jackson source inclusion is disabled by a mapper customiser, not the validator | F:24-R-19 |  |
| SG3-014 | AUD | note | Raw client address never logged (application rule; no standard clause) | — | none | Base details `toString()` content unverified, settled by test | G:31-R-11 |  |
| SA-021 | AUD | obligation | Log_Schema.md | — | deployer | Schema amendment request for three custom audit fields | A:11-R-a; A:11-R-b; A:13-R-a | ≡? D:13-R-5; D:13-R-13; D:13-A-4 |
| SA-022 | AUD | obligation | Std §3.3; Logging Std §3.5 | conditional-pass | shared | 90-day audit retention split between app appender and platform | A:03-R-12 | ≡? D:13-R-3; D:13-R-4 |
| SA-024 | AUD | obligation | Logging Std custom encoder recipe §1 | — | application | Retest the custom structured-log encoder on every Spring Boot upgrade | A:03-R-14 |  |
| SD1-003 | AUD | obligation | Std §3.3 (audit retention at least 90 days); Logging Std §3.5 (TTL enforced by the central | conditional-pass | shared | Platform-side 90-day audit retention | D:13-R-3 | ≡? D:15-H-6; D:15-R-8; 25:298–305 |
| SD1-004 | AUD | obligation | Std §3.4 (alert when audit logging fails) | conditional-pass | shared | Disk-full monitoring for the uncapped audit file | D:13-R-4 | ≡? D:15-H-2; 25:307–311; 21:569 |
| SD1-005 | AUD | obligation | Log_Schema.md closed field set | — | deployer | Schema amendment request for three custom audit fields | D:13-R-5 | ≡? A:03-R-10 |
| SD2-008 | AUD | obligation | ASVS 16.4.1; ASVS 16.2.3; IM8 lm-16; Logging Std §3.4 | — | deployer | Size the audit disk and alert on audit truncation | D:15-H-2 | ≡? F:26-H-2; F:26-H-3; D:13-R-4 |
| SF2-005 | AUD | obligation | ASVS 16.4.2; ASVS 16.4.3 | fail | deployer | Audit log is neither tamper-proof nor logically separate | F:25-R-5 | ≡? stage H (25:313–319); ≡? D:13-R-1; ≡? D:13-R-2; ≡? G:15-R-5; ≡? 25:548–551 (worked row 1) |
| SF2-031 | AUD | obligation | ASVS 16.1.1; ASVS 16.3.3 | pass-with-note | application | Log inventory records keyed and truncated forms | F:26-H-4 |  |
| SG2-006 | AUD | obligation | IM8 lm-4; W3C Trace Context §7 | satisfied-by-procedure | deployer | Keep trace headers out of the Tomcat access log | G:27-H-2 |  |
| SA-025 | AUD | residual | Std §3.4 (Privacy) | pass-with-note | none | user.id presence on failed-login rows reveals account existence | A:13-R-b | ≡? D:13-R-12 |
| SC-015 | AUD | residual | PRD Story 6 AC2; ASVS 6.4.3 | conditional-pass | shared | The stub email transport logs reset links in dev | C:10-R-15 | ≡? routing§6 Story 6 AC2; 25:329–340 |
| SC-036 | AUD | residual | Std §3.4 | partial | deployer | Unlock accountability record outlives neither its tombstone nor its TTL owner | C:11-R-17 | ≡? SC-041; 03 retention row |
| SD1-001 | AUD | residual | ASVS 16.4.2 | fail | deployer | Audit log is neither tamper-proof nor access-controlled | D:13-R-1 | ≡? 25:548 (worked row: audit-log read access); E:13-R-E1 |
| SD1-002 | AUD | residual | ASVS 16.4.3 | fail | deployer | Audit log is not shipped to a logically separate system | D:13-R-2 | ≡? 25:313–319; E:13-R-E1 |
| SD1-006 | AUD | residual | Std §3.4 (no passwords in logs) | pass-with-note | application | Jackson source-in-location pin lives outside the configuration validator | D:13-R-7 | ≡? F:24 item at 24:884–891 |
| SD1-009 | AUD | residual | Std §3.3 (audit events for state changes); Std §3.4 (alert when audit logging fails) | pass-with-note | none | Crash window between commit and audit write | D:13-R-11 |  |
| SD1-010 | AUD | residual | Std §3.4 (user.id on audit rows, carve-out for unresolved entry points) | pass-with-note | none | Presence of `user.id` on a failure row reveals the account exists | D:13-R-12 |  |
| SD1-011 | AUD | residual | ECS `source.*` field set | — | none | A future ECS field named `source.ip_hash` would collide | D:13-R-13 |  |
| SD1-013 | AUD | residual | ASVS 16.3.3 | partial | application | Logging attempts to bypass input validation and business logic | D:13-R-15 |  |
| SD1-015 | AUD | residual | Source correlation in audit rows (`source.ip_hash`) | — | none | No log can distinguish hosts inside one IPv6 /64 | D:13-R-17 | ≡? G:31 /64 residuals (ADR-020 register rows) |
| SD2-003 | AUD | residual | ASVS 16.4.2; ASVS 16.4.3 | fail | deployer | No durable, separate audit store | D:15-R-3 | ≡? D:13-R-1; D:13-R-2; 25:548–552; G:15-R-5; E:13-R-E1 |
| SE1-015 | AUD | residual | ASVS 16.4.2; ASVS 16.4.3 | fail | deployer | Audit logs neither tamper-proof nor held in a separate system | E:13-R-E1 | ≡? D:13-R-1; D:13-R-2; F:25-R-5; G:15-R-5 |
| SF2-020 | AUD | residual | ASVS 16.2.1 | pass-with-note | none | Keyed audit rows lose per-occurrence timestamps | F:26-R-2 |  |
| SF2-021 | AUD | residual | ASVS 16.2.1 | pass-with-note | none | Source identity beyond the distinct-source cap is lost | F:26-R-3 |  |
| SG1-013 | AUD | residual | ASVS 16.4.2; ASVS 16.4.3 | fail | deployer | No independent, tamper-resistant audit store | G:15-R-5 | ≡? 25:548 (worked row "Audit-log read access"); F:25-R-5; E:13-R-E1; D:15-R-3 |
| SG2-008 | AUD | residual | PDPA 2012 (Singapore) | — | none | Raw operator identifier kept in clear, deliberately unresolvable | G:28-R-2 |  |
| SG3-010 | AUD | residual | Raw client address never logged (application rule; no standard clause) | — | none | No host-level forensics inside one IPv6 /64 | G:31-R-7 | ≡? D:13-R-17 |
| SD1-016 | AUD | trigger | Audit-log protection and separation recorded as failed with deployer obligations; retentio | — | none | A log-management platform enters scope | D:13-T-1 |  |
| SD1-017 | AUD | trigger | Idle session expiry observed lazily at the next request, not at expiry | — | none | A scheduled job enters scope | D:13-T-2 | ≡? B:13-R-1 (idle-expiry decline) |
| SD1-018 | AUD | trigger | `url.path` carries the matched route pattern and the static message is the discriminator | — | none | The API base path becomes fixed rather than externalised | D:13-T-3 |  |
| SD1-019 | AUD | trigger | Jackson source inclusion pinned off as defence in depth | — | none | Jackson re-enables source inclusion by default | D:13-T-4 |  |
| SD1-021 | AUD | trigger | ASVS 16.3.3 anti-automation limb met by throttle and lockout rows | — | none | CAPTCHA, proof-of-work or bot scoring enters scope | D:13-T-6 |  |
| SD1-022 | AUD | trigger | Lazy 30-day forced-change expiry evaluated at login | — | application | Credential-expired audit reason confirms a correct password | D:11-T-1; D:11-T-2 | ≡? C:09-T-x1; B:11-T-1 |
| SF2-027 | AUD | trigger | The audit-volume formula and disk-space threshold | — | none | Measured bytes per row exceeds its bound, or a keyed row class is added | F:26-T-3 |  |
| SF2-028 | AUD | trigger | The two-tier emitter bound and the audit-volume formula | — | none | A new audit row is emitted from the security filter chain | F:26-T-4 |  |
| SG3-034 | AUD | trigger | Async trace and MDC propagation not applicable (no async boundary) | — | application | An executor or `@Async` method is added | G:32-T-1 |  |
| SC-008 | AUTH | deviation | ASVS 6.3.8; PRD Story 1 AC2 | fail | none | Username availability is disclosed at registration | C:10-R-8 | ≡? G:15-R-7; routing§6 Story 1 AC2 |
| SE2-033 | AUTH | deviation | RFC 9110 section 15.5.21 (422 Unprocessable Content) | fail | none | FACTOR_ENROLMENT_REQUIRED returned as 422, not 403 | E:23-R-14 | ≡? A:06-R-2 (same 422 fact); D:23-M-1 (423 as third off-label status) |
| SG1-005 | AUTH | deviation | PRD §Out of scope; PRD Stories 1–12 (scope) | pass-with-note | application | Controls built beyond the PRD because the standard requires them | G:17-R-10 | ≡? routing§6 (beyond-PRD additions) |
| SG1-028 | AUTH | fidelity | ASVS 6.3.8; Std §5:506 | pass-with-note | none | Timing uniformity verified by call count, not wall clock | G:16-R-9; G:16-R-11 | ≡? D:16-R-7; D:16-R-14 |
| SA-005 | AUTH | n/a | IM8 ac-12; IM8 ac-8; IM8 lm-18; IM8 st-3 | n/a | none | User-population declaration makes four IM8 controls N/A | A:01-R-6 | ≡? C:11-R-2; C:11-R-3; C:11-R-4; C:11-R-5; C:11-R-6; C:11-R-7 |
| SC-016 | AUTH | note | ASVS 6.3.3 | — | none | Declared ASVS target is L1 with named L2 and L3 controls | C:10-R-16 | ≡? E:23-R-3 |
| SC-018 | AUTH | note | ASVS 6.3.4 | pass | none | Authentication pathways are inventoried and consistently controlled | C:10-R-18 | ≡? E:23-R-4 |
| SG1-007 | AUTH | note | Std §5:509 | pass | none | The register points at the error contract instead of restating it | G:06-R-1 |  |
| SD2-005 | AUTH | residual | ASVS 6.3.8 | fail | none | Username availability at registration discloses existence | D:15-R-5 | ≡? G:15-R-7; routing§6 (Story 1 AC2) |
| SD2-016 | AUTH | residual | ASVS 6.3.8 | pass-with-note | none | Timing uniformity verified by mechanism, not by wall clock | D:16-R-14; D:16-R-7 | ≡? G:16-R-11; G:16-R-9 |
| SD2-021 | BLD | deviation | Std §5:521; ASVS 6.3.1 | — | deployer | Dependency-Check runs in Maven verify, not as a CI gate | D:16-R-12; D:16-H-2 | ≡? G:17-R-6; G:16-R-4 |
| SF1-020 | BLD | deviation | Build-once-deploy-many (deployment practice; no standard clause) | — | deployer | Frontend bundle is environment-specific; no artefact promotion | F:24-R-17 | ≡? 25:274–281 |
| SG1-023 | BLD | deviation | Std §5:521 | pass-with-note | application | Dependency-Check runs in Maven verify, not as a CI gate | G:16-R-4; G:17-R-6 | ≡? D:16-R-12; D:16-H-2 |
| SF2-024 | BLD | fidelity | Three-registry endpoint coverage (authorization matrix, budget table, audit catalogue) | — | none | Endpoint-coverage assertion reaches only annotated handlers | F:26-R-6 |  |
| SG1-027 | BLD | fidelity | Std §5 (the prescribed tests, under the deployed launch command) | — | deployer | The deployer's launch command is not exercised by the harness | G:16-R-8 | ≡? D:16-R-6 |
| SA-006 | BLD | n/a | ARC framework (agentic capabilities); ARC op CTRL-0068, CTRL-0070, CTRL-0071, CTRL-0074 | n/a | application | ARC N/A as a product requirement; op controls claimed for the workflow | A:01-R-7 |  |
| SA-001 | BLD | note | spec-compliance Step 2 completion criterion | — | application | Compliance view carries one verdict row per IM8 and ARC control | A:01-R-1 |  |
| SF1-026 | BLD | note | ASVS 13.4.1 | pass-with-note | application | No source-control metadata in the jar or the static bundle | F:24-R-26 | ≡? F:25-R-8; F:25-T-4; 25:664–666; 25:705–707 |
| SF2-010 | BLD | note | ASVS 13.2.4; ASVS 13.2.5; ASVS 13.1.1 | — | none | 13.2.4 and 13.2.5 are companions of 13.1.1, not drift-family members | F:25-R-10 | ≡? G:25-R-3 |
| SG1-010 | BLD | note | ASVS 6.3.1; ASVS 6.1.2; ASVS 6.2.11; ASVS 16.2.3; ASVS 16.3.3 | — | application | Documentation-conformance family and the verify-phase drift gate | G:25-R-3; G:25-R-4 | ≡? F:25-R-10; F:25-A-1 |
| SA-023 | BLD | obligation | Logging Std §4 (dependencies) | — | application | Version source for logging dependencies declared by the build | A:03-R-13 |  |
| SD2-017 | BLD | obligation | — | — | deployer | WebKit and Safari are not exercised by the browser suite | D:16-R-8; D:16-H-3 |  |
| SD1-025 | BLD | residual | Document CSP without inline importmap or preload polyfill | pass-with-note | application | `build.chunkImportMap` rationale evidenced, not confirmed | D:14-R-3 |  |
| SD2-022 | BLD | residual | ASVS 6.3.1 | — | shared | Surefire and Failsafe are pinned at 3.6.0, and two flags still bypass the gates | D:16-R-13; D:16-H-1 | ≡? G:16-R-12; F:25-M-1 |
| SE1-011 | BLD | residual | ARC CTRL-0074 | pass-with-note | deployer | Build-time dependency scan cannot see CVEs published later | E:21-R-3; E:21-H-5 | ≡? 25:373–375 |
| SF1-013 | BLD | residual | ASVS 13.3.1 (compensating control: secret scanning) | — | application | Secret scanning runs only when someone runs it | F:24-R-11 |  |
| SF2-008 | BLD | residual | ASVS 13.4.6; ASVS 13.4.5 | pass | none | No git or build-info properties are generated or served | F:25-R-8 | ≡? F:24-R-26 |
| SG1-030 | BLD | residual | ASVS 6.3.1 | pass-with-note | application | Two Maven skip flags bypass the drift and traceability gates | G:16-R-12 | ≡? D:16-R-13; D:16-H-1 |
| SD2-028 | BLD | trigger | Pinned Surefire and Failsafe 3.6.0 with two accepted bypass flags | — | application | Any Surefire or Failsafe version change | D:16-T-1 | ≡? G:16-T-1 |
| SF2-017 | BLD | trigger | The satisfied-by-absence verdict on ASVS 13.4.6 and 13.4.5; the health-only actuator expos | — | none | A git.properties or build-info plugin is added | F:25-T-4 | ≡? E:21-T-2 |
| SG1-031 | BLD | trigger | The pinned Surefire and Failsafe 3.6.0 bypass residual | — | none | Any Surefire or Failsafe version bump | G:16-T-1 | ≡? D:16-T-1 |
| SA-033 | CFG | deviation | Recipe configuration keys (`spring.user-management.*`, `spring.password.sso.*`, `spring.se | pass-with-note | application | All application properties live under one app.* prefix | A:04-R-7 |  |
| SE2-032 | CFG | deviation | MFA_Core Recipe 8 (spring.eds.mfa.totp properties) | fail | none | TOTP properties under app.mfa.totp, not spring.eds.mfa.totp | E:23-R-13 | ≡? B:07-A-7 property-prefix half (REJ-006); A:04-R-7 |
| SF1-001 | CFG | deviation | MCC_Shared_Auth_Recipes profile fallback (`${ENV_VAR:dev-default}` with a per-profile `.en | — | application | Corpus dev-default fallback for secrets rejected | F:24-R-1 |  |
| SF1-005 | CFG | deviation | Spring Boot 4 reference, Externalized Configuration: kebab-case recommended for property n | — | application | New secret properties use dots, not kebab-case leaves | F:24-R-5 |  |
| SF1-012 | CFG | deviation | IM8 as-8 | pass-with-note | application | Frontend `.env.development` and `.env.production` stay committed | F:24-R-10 |  |
| SF1-015 | CFG | deviation | ASVS 11.2.2; ASVS 13.3.4 | partial | shared | Tombstone HMAC key rotates forward only; old versions never retire | F:24-R-13; F:24-R-18 | ≡? C:12 tombstone-key rows; 25:223–237 |
| SE1-002 | CFG | fidelity | Std §5:498; Std §5:499; IM8 as-9; IM8 as-10 | pass-with-note | none | Production-only configuration asserted but never executed | E:20-R-2 | ≡? 25:563–567 (25 §4 worked row 4); F:25-T-1 |
| SF1-003 | CFG | fidelity | IM8 as-8 | pass-with-note | none | im8-review's as-8 check may miss `@ConfigurationProperties` secret binding | F:24-R-3 | ≡? A:01-R-8 (im8-review spelling mapping) |
| SF1-006 | CFG | n/a | ASVS 11.1.1 | n/a | none | Four secrets deliberately do not exist | F:24-R-6 | ≡? A:02-R-2 (pepper not taken); B:07-A-10 |
| SF1-010 | CFG | n/a | IM8 as-8 | n/a | none | H2 datasource credential is declared non-secret | F:24-R-8 | ≡? 25:264–265 |
| SF1-023 | CFG | n/a | Prohibited-configuration validator (candidate entry `spring.jackson.use-jackson2-defaults` | n/a | none | `spring.jackson.use-jackson2-defaults` is not a prohibited entry | F:24-R-20 |  |
| SF1-014 | CFG | not-built | ASVS 13.3.1; ASVS 13.3.3 | fail | shared | No vault, KMS or HSM for secrets or TOTP seeds | F:24-R-12 | ≡? F:25-R-4 |
| SA-007 | CFG | note | im8-review literal configuration checks (Spring Boot 3.4 / Spring Security 6.4 target) | — | application | Boot 4.1 to Boot 3.4 configuration-spelling mapping for im8-review | A:01-R-8 |  |
| SD1-007 | CFG | note | Prohibited-configuration list scope | — | none | `spring.jackson.use-jackson2-defaults` checked and excluded | D:13-R-8 |  |
| SF1-016 | CFG | note | ASVS 11.1.1; ASVS 11.1.2 | pass-with-note | application | Key-management policy and cryptographic inventory are documented | F:24-R-14 |  |
| SF1-017 | CFG | note | ASVS 11.3.2; ASVS 11.2.3; ASVS 11.4.1 | pass | application | Approved algorithms with at least 128-bit security | 24:699 |  |
| SF1-018 | CFG | note | ASVS 13.1.4 | conditional-pass | application | Secrets and their rotation are documented for operators | 24:696 |  |
| SF1-028 | CFG | note | Prohibited configuration: H2 TCP server | — | application | `h2.bindAddress` is invisible to the validator, and moot | F:24-R-24 |  |
| SE2-037 | CFG | obligation | MFA_Core §3.4 (encryption key rotated at least yearly); MFA_Core Questions Q14 | satisfied-by-procedure | deployer | Yearly TOTP encryption-key rotation procedure | E:23-H-4 | ≡? 25:193–198; 25:225–229 (three-key rotation procedures); part F rotation rows from 24 |
| SF1-004 | CFG | obligation | ASVS 11.5.1 | satisfied-by-procedure | deployer | Key randomness rests on the documented generation command | F:24-R-4 | ≡? F:25-R-7; 25:239–256 |
| SF1-025 | CFG | obligation | IM8 as-8 | — | shared | OTLP export headers are a conditional fourth secret | F:24-R-22 |  |
| SF2-007 | CFG | obligation | ASVS 11.5.1 | satisfied-by-procedure | deployer | Key randomness is proven by procedure, not by the application | F:25-R-7 | ≡? F:24-R-4; ≡? stage H (25:239–257) |
| SH-005 | CFG | obligation | Spring Boot 4 reference, Externalized Configuration (property-source order) | — | deployer | A leftover environment variable silently overrides a rotated key file | 25:214; 25:258 | ≡? F:24-R-12 (SF1-014) |
| SF1-024 | CFG | residual | Fail-fast refresh-phase validation of required configuration | — | application | `@ConfigurationProperties` accepts an unresolved `${VAR}` literal | F:24-R-21 |  |
| SF2-004 | CFG | residual | ASVS 13.3.1; ASVS 13.3.3 | fail | deployer | No vault, KMS or HSM for secrets and TOTP seeds | F:25-R-4 | ≡? F:24-R-12 |
| SC-053 | CFG | trigger | Tombstone HMAC key: forward-only versioning, old versions never retire | — | none | Bounded tombstone retention would allow HMAC key retirement | C:12-T-3 |  |
| SF1-021 | CFG | trigger | Tombstone HMAC key forward-only versioning, with old versions never retired | — | none | Bounded tombstone retention makes old key versions retirable | F:24-T-1 | ≡? C:12-T-3 |
| SF2-014 | CFG | trigger | The limitation that production-only configuration is asserted by test but never exercised | — | none | Any real deployment | F:25-T-1 | ≡? E:20-R-2; ≡? 25:563–567 (worked row 4) |
| SA-010 | CRED | deviation | NIST SP 800-63B-4 §3.1.1.2 | pass-with-note | application | No pepper or keyed pre-hash on password hashes | A:02-R-2 | ≡? B:07-R-3; B:07-A-10 |
| SB-002 | CRED | deviation | ASVS 6.2.9; NIST SP 800-63B-4 §3.1.1.2 | fail | none | 72-byte password ceiling rejects long non-Latin passphrases | B:07-R-2 | ≡? C:07-M-x1 (6.2.9 part) |
| SC-013 | CRED | deviation | Std §2:121; Std §4:401 | fail | none | A self-service password-change endpoint exists | C:10-R-13 | ≡? routing§6 beyond-PRD additions (self-service change) |
| SC-034 | CRED | deviation | RFC 5321 section 2.4 | pass-with-note | none | The whole email address is lowercased | C:11-R-15 |  |
| SF2-002 | CRED | deviation | NIST SP 800-63B-4 §4.6; NIST SP 800-63B-4 §4.2.1.2 | fail | shared | Two notification addresses per account not supported | F:25-R-2 |  |
| SG1-002 | CRED | deviation | Std §3.5:354 | pass-with-note | application | BCrypt instead of the standard's preferred Argon2id or scrypt | G:17-R-2 | ≡? routing§5 (ADR-001 register hand-off); A:02-A-3 |
| SG1-003 | CRED | deviation | PRD Story 1 AC2 | partial | application | Registration reports no email conflict; username conflict stays specific | G:17-R-8 | ≡? routing§6 (Story 1 AC2); A:06-A-3 |
| SH-009 | CRED | deviation | PRD Story 1 AC1 | pass-with-note | application | 15-character password minimum instead of the PRD's 12 | routing§6 |  |
| SH-010 | CRED | deviation | PRD Story 1 AC1 | partial | application | Registration creates a pending record; the account activates on redemption | routing§6 | ≡? G:17-R-10 (SG1-005, activation as a beyond-PRD addition) |
| SA-009 | CRED | n/a | NIST SP 800-63B-4 §3.1.1.2; Std §1 | n/a | none | Periodic credential expiry is not built, by prohibition | A:02-R-1 | ≡? B:07-R-1 |
| SB-001 | CRED | n/a | NIST SP 800-63B-4 §3.1.1.2; ASVS 6.2.10 | n/a | none | Periodic password expiry is not implemented, by prohibition | B:07-R-1 | ≡? A:02-R-1 |
| SD2-026 | CRED | n/a | Std §5:473 | n/a | none | Username change is N/A by construction | D:16-R-18 | ≡? A:16-R-a (06:481); C:11 §5:473 row (11:955–968) |
| SB-003 | CRED | not-built | NIST SP 800-63B-4 §3.1.1.2 | fail | none | No pepper or keyed second hash over password hashes | B:07-R-3 | ≡? A:02-R-2; ≡? F:24-R-6 |
| SB-004 | CRED | not-built | NIST SP 800-63B-4 §3.1.1.2; ASVS 6.2.8 | pass | none | Mistyping allowances (trim, case-fold, space-collapse) declined | B:07-R-4 |  |
| SB-005 | CRED | not-built | NIST SP 800-63B-4 §3.1.1.2; ASVS 6.2.12 | pass | none | Live breached-password API lookup not built | B:07-R-5 |  |
| SD2-020 | CRED | not-built | NIST SP 800-63B-4 §4.2.3 | fail | shared | Account-owner notifications are not built | D:16-R-11 | ≡? F:25-R-1; F:25-R-2 |
| SG1-022 | CRED | not-built | Std §5:517; NIST SP 800-63B-4 §4.2.3 | fail | application | Account-owner notifications not built | G:16-R-3 | ≡? D:16-R-11; F:25-R-1 |
| SC-002 | CRED | note | Std §4:401; Std Questions Q22:539 | — | none | Self-registration and user-initiated reset are absent from the Standard | C:10-R-2 |  |
| SF2-011 | CRED | note | NIST SP 800-63B-4 §4.2.1; NIST SP 800-63B-4 §4.2.2.1; NIST SP 800-63B-4 §4.2.2.2 | — | none | Adopted reading of NIST §4.2.1 application-specific recovery | F:25-R-11 | ≡? G:25-R-1 |
| SG1-008 | CRED | note | NIST SP 800-63B-4 §4.2.1; NIST SP 800-63B-4 §4.2.2.1 | — | none | Adopted reading of NIST §4.2.1 on application-specific recovery methods | G:25-R-1 | ≡? F:25-R-11 |
| SH-020 | CRED | note | Std §3.5 (password history, default 3); NIST SP 800-63B-4 §3.1.1.2 | pass-with-note | none | Password history of three kept despite NIST's silence on history | 07:64 | ≡? G:17-R-10 (SG1-005, password history) |
| SB-006 | CRED | obligation | ASVS 6.1.2; ASVS 6.2.11; ASVS 6.2.4; ASVS 6.2.12 | partial | shared | Documented context word list and refresh of both password lists | B:07-R-6 | ≡? 25:37–40; ≡? 25:664–676 |
| SC-007 | CRED | residual | PRD Story 1 | pass-with-note | none | Looping re-registration can keep invalidating a victim's activation link | C:10-R-7 |  |
| SC-010 | CRED | residual | ASVS 6.3.8 | pass-with-note | none | Reset against a never-activated account is a silent no-op | C:10-R-10 |  |
| SC-035 | CRED | residual | PRD Story 2 | pass-with-note | none | Users cannot log in with their email address | C:11-R-16 |  |
| SD2-002 | CRED | residual | PRD Story 6 AC2 | partial | shared | Stubbed email: log-read takeover in dev, no channel elsewhere | D:15-R-2 | ≡? routing§6 (Story 6 AC2); G:15-R-2; G:15-R-3 |
| SD2-024 | CRED | residual | — | — | none | Unauthenticated BCrypt cost survives on activation and reset redemption | D:16-R-16 | ≡? C:10 unauthenticated-BCrypt claim (15:193–195) |
| SF2-001 | CRED | residual | NIST SP 800-63B-4 §4.2.3 | fail | shared | Account-recovery notification is never sent outside dev | F:25-R-1 | ≡? 25:557–561 (worked row 3); ≡? B:09-R-15 |
| SF2-003 | CRED | residual | NIST SP 800-63B-4 §4.2.2.2; NIST SP 800-63B-4 §3.1.3.1 | pass-with-note | none | Issued recovery code and password can correlate through the mailbox | F:25-R-3 |  |
| SG1-011 | CRED | residual | PRD Story 6 AC2; PRD §Out of scope | pass-with-note | shared | In dev, anyone reading logs can take over any activated account | G:15-R-2 | ≡? routing§6 (Story 6 AC2); D:15-R-2 |
| SG1-012 | CRED | residual | PRD Story 6 AC2; PRD §Out of scope | partial | deployer | Outside dev, a reset request produces nothing deliverable | G:15-R-3 | ≡? routing§6 (Story 6 AC2); B:09-R-15; F:25-R-1 |
| SG1-016 | CRED | residual | ASVS 6.3.8 | fail | application | Username existence confirmable at registration | G:15-R-7 | ≡? C:10-R-8; D:15-R-5 |
| SC-019 | CRED | trigger | Enumeration-resistant two-step registration (credential set at activation) and admin-only  | — | none | Self-service TOTP enrolment reopens the pending-registration design | C:10-T-1 | ≡? D:10-T-1 |
| SD1-026 | CRED | trigger | Recovery codes deferred while the mail transport is a stub | — | none | Mail transport enters scope and recovery routes are built | D:25-T-1; D:25-T-2 | ≡? F:25-T-2; D:16-T-2; G:15-T-1 |
| SD1-029 | CRED | trigger | Enumeration-resistant two-step registration and admin-only TOTP enrolment | — | none | Self-service TOTP enrolment is added | D:10-T-1 | ≡? C:10-T-1 |
| SD2-029 | CRED | trigger | The mail-transport deferral: recovery-code route not built, notification SHALLs failed | — | application | A mail transport enters scope | D:16-T-2 | ≡? F:25-T-2; G:15-T-1; D:25-T-1; D:25-T-2 |
| SF2-015 | CRED | trigger | Break-glass conditional pass; the notification SHALLs; the dev-only reset-link confinement | — | none | A real mail transport enters scope | F:25-T-2; F:26-T-1 | ≡? G:15-T-1; ≡? G:28-T-5; ≡? G:31-T-5; ≡? stage H (25:848–874) |
| SG1-020 | CRED | trigger | Recovery codes deferred while mail is stubbed; recovery-code and recovery-address routes k | — | none | A real mail transport entering scope reopens recovery containment | G:15-T-1 | ≡? F:25-T-2; D:25-T-1; D:25-T-2; D:16-T-2; G:28-T-5; G:31-T-5 |
| SG3-019 | CRED | trigger | Mail transport out of scope, so outside dev every disabled password authenticator needs th | — | application | A mail transport lands | G:31-T-5 | ≡? G:15-T-1; G:28-T-5; F:25-T-2 |
| SB-011 | CSRF | residual | RFC 9110 section 9.2.1; Std §3.1:238 | pass-with-note | none | Unauthenticated GET of the CSRF token persists a session row | B:08-R-5 | ≡? G:29-R-1 |
| SF1-008 | DATA | deviation | ASVS 13.2.1; ASVS 13.2.2 | fail | none | Embedded H2 credential is unchanging and superuser | F:24-R-7 | ≡? F:25-R-6; 25:264–272 |
| SH-014 | DATA | deviation | PRD §Data model (`password_reset_tokens`) | pass-with-note | application | One typed `credential_tokens` table instead of `password_reset_tokens` | routing§6 |  |
| SH-015 | DATA | deviation | PRD §Data model (`users.role` enum) | pass-with-note | application | Role held as a foreign key to a seeded `roles` table, not an enum | routing§6 |  |
| SH-019 | DATA | deviation | Std Questions Q3 (bootstrap by Liquibase changeset) | pass-with-note | none | Flyway migrations, not Liquibase or schema generation | 04:241 |  |
| SC-044 | DATA | fidelity | Portability requirement (H2 DDL ports to PostgreSQL and MySQL 8) | conditional-pass | none | Seam 1: UUID column type has no MySQL equivalent | C:12-R-1 | ≡? D:16-R-3 |
| SC-045 | DATA | fidelity | Portability requirement (H2 DDL ports to PostgreSQL and MySQL 8) | conditional-pass | none | Seam 2: zoned timestamp type has no MySQL equivalent | C:12-R-2 | ≡? D:16-R-3 |
| SC-046 | DATA | fidelity | Portability requirement (H2 DDL ports to PostgreSQL and MySQL 8) | conditional-pass | none | Seam 3: binary column width is inexpressible on PostgreSQL | C:12-R-3 | ≡? D:16-R-3 |
| SC-047 | DATA | fidelity | Portability requirement (H2 DDL ports to PostgreSQL and MySQL 8) | conditional-pass | none | Seam 4: boolean columns break schema validation on MySQL | C:12-R-4 | ≡? D:16-R-3 |
| SC-048 | DATA | fidelity | Portability requirement (H2 DDL ports to PostgreSQL and MySQL 8) | conditional-pass | none | Seam 5: session attribute bytes use an undocumented H2 type | C:12-R-5 | ≡? D:16-R-3 |
| SC-049 | DATA | fidelity | Portability requirement (H2 DDL ports to PostgreSQL and MySQL 8) | conditional-pass | none | Seam 6: MySQL default collation folds case and accents | C:12-R-6 | ≡? D:16-R-2 |
| SD2-011 | DATA | fidelity | — | — | none | Production-database locking and types not exercised | D:16-R-1 | ≡? D:12-R-2; G:16-R-5 |
| SD2-012 | DATA | fidelity | — | — | none | MySQL accent-insensitive collation not exercised | D:16-R-2 |  |
| SD2-013 | DATA | fidelity | — | — | none | DDL portability beyond the recorded seams not exercised | D:16-R-3 | ≡? C:12 seam-register rows |
| SG1-024 | DATA | fidelity | Std §4 Data Persistence (relational database) | pass-with-note | none | Production-database locking and types not exercised | G:16-R-5 | ≡? D:16-R-1; D:16-R-2; D:16-R-3 |
| SF1-009 | DATA | note | ASVS 13.2.3 | pass | application | Embedded H2 user is non-default with a non-empty password | 24:611 | ≡? 25:268–269 |
| SF2-006 | DATA | residual | ASVS 13.2.1; ASVS 13.2.2 | fail | none | H2 datasource runs as its administrator with an unchanging password | F:25-R-6 | ≡? F:24-R-7; ≡? stage H (25:264–272) |
| SH-021 | DATA | residual | Concurrent admin edits (application decision; no standard clause) | — | none | No optimistic locking: non-security admin edits are last-write-wins | 12:942 | ≡? D:12-R-2 (SD1-028) |
| SC-051 | DATA | trigger | Admin-list sort allowlist left unindexed | — | none | Real user volume reopens the unindexed sort allowlist | C:12-T-1 |  |
| SC-052 | DATA | trigger | Cross-table reuse race accepted, reservations table declined | — | none | Concurrent or self-service deletion reopens the reservations table | C:12-T-2 |  |
| SC-054 | DATA | trigger | Seam register asserted by review, never executed | — | none | A real PostgreSQL or MySQL runtime makes the seam register executable | C:12-T-4 | ≡? D:16-R-1; D:16-R-3 |
| SC-024 | FE | deviation | IM8 dp-8 | fail | application | Classification labels are owed on every input field | C:11-R-5 | ≡? A:01-R-6 |
| SG1-029 | FE | fidelity | Std §5:499; Std §5:502 | pass-with-note | deployer | Browser tests run in Chromium and Firefox, not WebKit or Safari | G:16-R-10 | ≡? D:16-R-8; D:16-H-3 |
| SC-025 | FE | n/a | IM8 lm-18 | n/a | none | WOGAA analytics does not apply | C:11-R-6 | ≡? A:01-R-6 |
| SC-026 | FE | n/a | IM8 st-3 | n/a | none | st-3 does not apply | C:11-R-7 | ≡? A:01-R-6 |
| SD1-023 | FE | not-built | Accessibility conformance target (none adopted) | — | application | Accessibility conformance target and verification method | D:14-R-1 |  |
| SH-006 | FE | obligation | Std §3.4 (no credentials in logs) | — | shared | One-time tokens shown once and kept out of every client sink | 25:820 |  |
| SC-017 | FE | residual | ASVS 6.2.7 | pass-with-note | none | Paste and password managers must work on every password field | C:10-R-17 |  |
| SD2-014 | HDR | fidelity | Std §5:498; IM8 as-10 | — | none | Real TLS, HSTS and __Host- cookies over HTTPS not exercised | D:16-R-4 | ≡? 25:563–566; G:16-R-6 |
| SG1-025 | HDR | fidelity | Std §5:498; Std §5:499; IM8 as-10 | pass-with-note | none | TLS, HSTS and `__Host-` cookies never exercised over HTTPS | G:16-R-6 | ≡? 25:563 (worked row "Unexercised production config"); D:16-R-4; F:25-T-1 |
| SA-036 | HDR | note | IM8 as-9; Std §5 (security headers tests) | pass-with-note | application | API-side CSP is defence in depth, not the XSS control | A:04-R-10 | ≡? E:20-R-3 |
| SH-007 | HDR | note | Std §3.5:391 | pass-with-note | none | Logout clears the SPA's cookies but never its storage or cache | 25:826 | ≡? E:20-H-1 (SE1-005) |
| SH-023 | HDR | note | IM8 as-9 (CSP check); CSP Level 3 `style-src-elem` and `style-src-attr` | pass-with-note | none | `style-src` left unsplit | 14:467 |  |
| SE1-006 | HDR | obligation | Std §3.5; IM8 as-9 | conditional-pass | deployer | Static host must emit the production document header set | E:20-H-2 | ≡? 25:28–29 |
| SE1-008 | HDR | obligation | Std Q27 (no localhost origins in production); PRD line 119 | conditional-pass | deployer | Production CORS allow-list: real origin, never localhost | E:20-H-4 | ≡? 25:29–30 |
| SD1-024 | HDR | residual | Document CSP without inline script | pass-with-note | application | No mounted component emits an inline script | D:14-R-2 |  |
| SE1-003 | HDR | residual | Std §3.5 (infrastructure-level header injection is complementary, not a substitute) | pass-with-note | none | Static-host-delivered document CSP as an argument surface | E:20-R-3 | ≡? A:04-R-10 |
| SF1-007 | HDR | residual | CSP `connect-src` and CORS origin agreement (no standard clause) | — | deployer | Backend API origin and build-time SPA origin can drift apart | F:24-R-16 | ≡? 25:274–281 |
| SE1-004 | HDR | trigger | Two-origin topology with no CSP nonce; declined password re-entry on TOTP provisioning and | — | none | Inline script or relaxed script-src in the production document | E:20-T-1; E:20-T-2 |  |
| SG1-021 | HDR | trigger | The two-origin, no-nonce document topology; the decline of password re-entry on TOTP enrol | — | none | Any inline script in the production document reopens two decisions | G:15-T-2 | ≡? E:20-T-1 |
| SG1-004 | LCK | deviation | PRD Story 3 AC1 | pass-with-note | application | Escalating 20/40/60-minute lockout instead of the PRD's example 15 minutes | G:17-R-9 | ≡? routing§6 (Story 3 AC1); B:09-A-1 |
| SH-011 | LCK | deviation | PRD Story 3 AC3 | partial | none | One source can still keep a targeted user locked out | routing§6 | ≡? F:09-R-x1 (SF2-013); ≡? G:31-R-10 (SG3-013) |
| SH-012 | LCK | deviation | PRD Story 2 AC3 | pass-with-note | application | A capped password stays refused after the lockout expires | routing§6 | ≡? F:09-R-x1 (SF2-013) |
| SD1-008 | LCK | note | NIST SP 800-63B-4 §3.2.2 (additional techniques) | — | none | Progressive delay is a NIST addition to lockout, not an alternative | D:13-R-10 | ≡? B:09-A-6 (register halves); 09:874–877 |
| SE2-025 | LCK | note | ASVS 6.1.1 | pass | none | Factor-axis lockout and throttling are documented | E:23-R-5 | ≡? F:25-R-12 (6.1.1 overall, pass-with-note); REJ-014 (6.1.1 verdict) |
| SE2-028 | LCK | note | NIST SP 800-63B-4 §3.2.2 | pass | none | Factor axis meets the failed-attempt cap; tier 1 is additional | E:23-R-8 |  |
| SG3-023 | LCK | obligation | ASVS 6.1.1; IM8 lm-16 | pass-with-note | shared | Runbook for the lockout-cap alert's warning window | G:31-H-4 |  |
| SH-002 | LCK | obligation | Admin and support lockout runbook (application decision; no standard clause) | — | deployer | A locked account self-lifts on a timed ladder; do not edit the database | 25:92; 25:431 | ≡? G:30-H-4 (SG3-004) |
| SH-003 | LCK | obligation | Std §2:131; admin recipe `ResetPasswordCommand` | pass-with-note | shared | Admin-issued password reset does not lift a lock | 25:135; 25:433 |  |
| SB-017 | LCK | residual | Std §5:450 | pass-with-note | none | Correct password on an expired credential leaves a stale failure counter | B:09-R-16 |  |
| SB-018 | LCK | residual | ASVS 6.1.1 | conditional-pass | shared | Malicious account lockout: documented, bounded, conditionally passed | B:09-R-1 | ≡? F:25-R-12 |
| SB-022 | LCK | residual | NIST SP 800-63B-4 §3.2.2; ASVS 6.1.1 | pass-with-note | none | Mass permanent-lockout primitive, priced after every bound | B:09-R-7 | ≡? G:15-R-10; ≡? F:09-R-x1; ≡? G:28-R-4 |
| SF2-012 | LCK | residual | ASVS 6.1.1 | pass-with-note | shared | Malicious-lockout documentation verdict rests on an operable runner | F:25-R-12 | ≡? stage H (25:424–427, 25:916–917); ≡? B:09-R-1 |
| SF2-013 | LCK | residual | NIST SP 800-63B-4 §3.2.2; PRD Story 3 AC3 | pass-with-note | shared | About 100 unauthenticated requests permanently disable one password | F:09-R-x1 | ≡? B:09-R-7; ≡? B:09-R-2; ≡? G:15-R-10; ≡? stage H (25:420–423) |
| SG1-019 | LCK | residual | NIST SP 800-63B-4 §3.2.2; ASVS 6.1.1 | conditional-pass | shared | One source can drive many accounts into permanent password disable | G:15-R-10 | ≡? B:09-R-7; B:09-R-1; G:28-R-4 |
| SG3-006 | LCK | residual | NIST SP 800-63B-4 §3.2.2 | — | none | No global lockout-rate cap | G:31-R-2 |  |
| SG3-015 | LCK | trigger | Escalating lockout ladder and its startup floor (840 min to cap, 580 min warning) | — | application | Ladder change the startup floor would reject | G:31-T-1 |  |
| SA-037 | MFA | deviation | RFC 9110, §15.5.13 | fail | application | 412 used for a missing or expired second factor | A:06-R-1 | ≡? E:23-R-14 |
| SA-038 | MFA | deviation | RFC 9110, §15.5.21; RFC 9110, §15.5.4 | fail | application | 422 used for factor enrolment required | A:06-R-2 | ≡? E:23-R-14 |
| SD2-032 | MFA | deviation | ASVS 6.3.3 | fail | none | ASVS L2 is not claimable for the whole application | D:19-R-2 | ≡? E:23-R-3; routing§6 (Out of scope: MFA) |
| SD2-033 | MFA | deviation | NIST SP 800-63B-4 §4.1.2.1 | fail | shared | No independent notification when an authenticator is added | D:23-R-1 | ≡? E:23-R-9 |
| SE2-021 | MFA | deviation | ASVS 6.5.5 | fail | none | TOTP accepted across ±1 time step, beyond a 30 s lifetime | E:23-R-1 |  |
| SE2-023 | MFA | deviation | ASVS 6.3.3 | partial | none | MFA required on the admin surface only | E:23-R-3 | ≡? D:19-R-2 (L2 not claimable); routing§6 "Out of scope: MFA" PRD row |
| SE2-029 | MFA | deviation | NIST SP 800-63B-4 §4.1.2.1 (notify when an authenticator is added) | fail | shared | No independent notification when an authenticator is bound | E:23-R-9 | ≡? D:23-R-1 (19:426–431, same SHALL failure) |
| SE2-034 | MFA | deviation | MFA_Core §4.1 (verify every provider on each request) | fail | none | Factor verified once per session, not on each request | E:23-R-15 | ≡? E:22-A-1 (ADR-021, same decision) |
| SH-013 | MFA | deviation | PRD §Out of scope (multi-factor authentication) | pass-with-note | application | TOTP required for administrators although MFA is out of scope | routing§6 | ≡? E:23-R-3 (SE2-023); ≡? D:19-R-2 (SD2-032) |
| SH-022 | MFA | deviation | RFC 4918 section 11.3 (423 Locked) | pass-with-note | none | `423 FACTOR_DISABLED` used off-label for a tier-2 disabled factor | 14:314 | ≡? A:06-R-1 (SA-037, off-label statuses) |
| SH-024 | MFA | deviation | MFA_Core standard L38 (provisioning as GET); RFC 9110 section 9.2.1 | pass-with-note | none | TOTP provisioning is a POST, not the standard's GET | 23:926 | ≡? E:22-R-19 (SE2-019) |
| SH-025 | MFA | deviation | MFA_Core standard L48 and Recipe L705 (`X-TOTP` header) | pass-with-note | none | TOTP code sent as a JSON body, not an `X-TOTP` header | 23:926 | ≡? E:22-R-12 (SE2-012) |
| SC-050 | MFA | fidelity | IM8 ac-2 (im8-review MFA-state check) | pass-with-note | none | Automated review greps for an MFA flag that does not exist | C:12-R-7 | ≡? D:12-R-1 |
| SD1-027 | MFA | fidelity | IM8 ac-2 (MFA for privileged accounts), as checked by `im8-review` | pass-with-note | application | `im8-review` reports admin MFA absent: a documented false negative | D:12-R-1 | ≡? C:12-R-7 |
| SC-056 | MFA | n/a | MFA standard §4.2 (PIN user details) | n/a | none | PIN factor tables are not built | C:23-R-x3 | ≡? G:32-R-5; E:19-X-E1 |
| SG3-029 | MFA | n/a | MFA_Core §5:388 PIN verify success; MFA_Core §5:389 PIN verify failure; MFA_Core §5:390 PI | n/a | none | PIN factor tests not applicable | G:32-R-5 |  |
| SC-055 | MFA | not-built | MFA standard §4.2 (Pending TOTP table) | pass-with-note | none | Expired pending TOTP rows are never disposed of | C:23-R-x2 |  |
| SD2-031 | MFA | note | IM8 ac-2 | — | none | Privileged-action scope: the admin surface and MFA-settings changes | D:19-R-1 |  |
| SE2-022 | MFA | note | ASVS 6.4.4; ASVS 6.5.6 | pass-with-note | shared | Lost-factor recovery at parity with enrolment; factors revocable | E:23-R-2; E:23-R-12 | ≡? 25:557–560 (break-glass worked row, which carries 6.4.4 and 6.5.6) |
| SE2-024 | MFA | note | ASVS 6.1.3; ASVS 6.3.4 | pass-with-note | none | Authentication pathway inventory with consistent factor strength | E:23-R-4 |  |
| SE2-026 | MFA | note | ASVS 6.5.1 | pass | none | TOTP replay rejection is atomic under the row lock | E:23-R-6 |  |
| SE2-027 | MFA | note | ASVS 6.5.8 | pass | none | One server Clock drives TOTP counters and factor expiry | E:23-R-7 |  |
| SE2-031 | MFA | note | NIST SP 800-38D §5.2.1.1; NIST SP 800-38D §8.2.2; NIST SP 800-38D §8.3 | pass | none | 128-bit random GCM IV satisfies SP 800-38D | E:23-R-11 | ≡? D:19-R-3 (withdrawn there as "removes a register entry") |
| SH-004 | MFA | obligation | TOTP secret envelope context prefix (application design; no standard clause) | — | deployer | Alarm and response for a TOTP decrypt context-prefix mismatch | 25:200; 25:342 |  |
| SC-039 | MFA | residual | NIST SP 800-63B-4 §4.1.2.1 | pass | deployer | First enroller binds the seeded admin's authenticator | C:23-R-x1 | ≡? E:23-H-2; E:23-R-10; 25:182–185 |
| SC-042 | MFA | residual | IM8 ac-2 | pass-with-note | none | The factor gate has one layer while the role gate has two | C:15-R-x3 | ≡? E:23-M-2 |
| SD2-034 | MFA | trigger | The recovery-codes deferral (ADR-024) | — | application | Recovery codes are brought into scope | D:19-T-1 | ≡? F:25-T-3 |
| SF2-016 | MFA | trigger | The recovery-codes deferral (ADR-024) and the distinct-recovery-address mitigation (ADR-07 | — | none | Recovery-codes deferral is reversed | F:25-T-3 | ≡? D:19-T-1 |
| SE1-010 | OBS | deviation | IM8 as-13; IM8 ac-1 | partial | application | Unauthenticated /actuator/health fails the literal base-path check | E:21-R-2 |  |
| SE1-012 | OBS | fidelity | IM8 lm-16 | pass-with-note | none | OTLP metrics export enablement path never executed | E:21-R-5 | ≡? 25:563–567 (same limitation class as SE1-002) |
| SG3-031 | OBS | n/a | Logging Std §5:355 | n/a | none | No outbound calls to carry a correlation ID | G:32-R-7 |  |
| SE1-013 | OBS | not-built | IM8 lm-16 (frontend half) | partial | none | No client error-ingestion endpoint for frontend telemetry | E:21-R-6 |  |
| SF1-019 | OBS | note | ASVS 13.4.5 | pass-with-note | application | Actuator exposes only intended endpoints; value masking cannot be loosened | 24:701 | ≡? 25:682–684 |
| SE1-016 | OBS | obligation | ASVS 15.1.3 | satisfied-by-procedure | deployer | Recompute the diskspace free-space threshold | E:21-T-1; E:21-H-6 | ≡? F:26-H-2; 25:377–381 |
| SE1-018 | OBS | obligation | — | — | deployer | Re-enable health probes and db health together | E:21-H-3 | ≡? 25:365–367 |
| SE1-019 | OBS | obligation | IM8 lm-16 | — | deployer | Connect an OTLP collector and alert on authenticable admins | E:21-H-4 | ≡? 25:368–372 |
| SE1-020 | OBS | obligation | — | — | deployer | Collector-side correlation of runner intent rows without outcome | E:21-H-7 | ≡? G:28-R-5 |
| SF2-029 | OBS | obligation | ASVS 15.1.3 | satisfied-by-procedure | deployer | Recompute the disk-space threshold when its inputs change | F:26-H-2 | ≡? stage H (25:377–381); ≡? E:21-H |
| SF2-030 | OBS | obligation | ASVS 16.3.3; ASVS 2.4.1 | fail | deployer | Alert on truncation rows and keyed-row counts | F:26-H-3 | ≡? stage H (25:359–361); ≡? SF2-009 |
| SG2-005 | OBS | obligation | IM8 lm-4; W3C Trace Context §3.4; W3C Trace Context §7.2 | pass | deployer | Every inbound trace is restarted; continuation stays off | G:27-H-1 | ≡? D:15-H-7 |
| SG2-033 | OBS | obligation | IM8 lm-16 | pass | deployer | Keep the H2 data directory under the datasource URL | G:29-H-3 |  |
| SG3-003 | OBS | obligation | IM8 lm-16 | partial | deployer | Export the authenticable-admins gauge and alert below two | G:30-H-3 | ≡? E:21-H-4 (extended by 21:846–857); E:21-R-1 |
| SE1-009 | OBS | residual | IM8 lm-16 (alerting half) | partial | deployer | Alerting and rate computation live outside the application | E:21-R-1; E:21-H-1 | ≡? F:25-R-9; 25:359–361; 25:383–387 |
| SE1-014 | OBS | residual | Std §3.4 (alert when audit logging fails); Logging Std line 389 (sudden drops in log throu | partial | deployer | Total log-transport failure is undetectable in-process | E:21-R-7; E:21-H-2 | ≡? 25:362–364 |
| SF2-009 | OBS | residual | IM8 lm-16 | partial | deployer | Alerting and rate computation cannot be done in-process | F:25-R-9 | ≡? stage H (25:359–361); ≡? E:21-R-1 |
| SG2-026 | OBS | residual | IM8 lm-16 | pass-with-note | none | Shared-mount detection is informational only | G:29-R-5 |  |
| SA-026 | OBS | trigger | Inbound trace context restarted at the application boundary, with baggage off | — | application | A needed correlation field reopens trace restart and baggage-off | A:27-T-a | ≡? G:27-T-4 |
| SA-027 | OBS | trigger | Inbound trace context restarted at the application boundary | — | application | Adding trace headers to CORS allowed headers reopens trace restart | A:27-T-b | ≡? G:27-T-2 |
| SD1-020 | OBS | trigger | Inbound trace context restarted at the application boundary | — | none | Inbound trace continuation is re-enabled | D:13-T-5 |  |
| SE1-017 | OBS | trigger | Actuator info endpoint not exposed, on a no-benefit reason | — | application | Adding git or build-info metadata plugins | E:21-T-2 | ≡? F:25-T-4; F:25-R-8 |
| SG2-001 | OBS | trigger | Inbound trace context is restarted at the application boundary (ADR-063) | — | none | A deployer introduces an upstream tracing gateway | G:27-T-1 |  |
| SG2-002 | OBS | trigger | Inbound trace context is restarted at the application boundary (ADR-063) | — | none | The SPA starts sending traceparent | G:27-T-2 | ≡? A:27-T-b |
| SG2-003 | OBS | trigger | Inbound trace context is restarted at the application boundary (ADR-063) | — | none | The application makes an outbound call to a traced service | G:27-T-3 | ≡? G:32-T-2 |
| SG2-004 | OBS | trigger | Baggage off and inbound correlation ID dropped | — | none | Any change needs a correlation field | G:27-T-4 | ≡? A:27-T-a |
| SG3-035 | OBS | trigger | Outbound correlation-ID injection not applicable (no outbound calls) | — | application | An outbound call is added | G:32-T-2 | ≡? G:27-T-3 |
| SD2-015 | OPS | fidelity | — | — | deployer | The deployer's launch command is exercised only in rehearsal | D:16-R-6 | ≡? G:16-R-8 |
| SH-008 | OPS | note | Register and handover renderings (deployment-assumption header) | — | none | Deployment-assumption header on both renderings | 25:539 | ≡? F:25-T-1 (SF2-014) |
| SA-004 | OPS | obligation | im8-review Step 3 severity derivation | — | application | IM8 risk classification declared (Low Risk recommended) | A:01-R-5 |  |
| SD2-001 | OPS | obligation | IM8 as-10; IM8 dp-3; PRD L121 | conditional-pass | deployer | Nobody is nominated to terminate TLS | D:15-R-1 | ≡? 25:553–556; E:20-H-3; G:15-R-1; G:17-R-7 |
| SE1-005 | OPS | obligation | — | — | deployer | SPA and API must share one registrable domain | E:20-H-1 | ≡? 25:25–27 |
| SE1-007 | OPS | obligation | IM8 as-10; IM8 dp-3; PRD line 121 | partial | deployer | A named component must terminate TLS | E:20-H-3 | ≡? 25:553–555 (25 §4 worked row 2); 25:31–32 |
| SE2-036 | OPS | obligation | RFC 6238 section 6; MFA_Core §3.4 (±1 skew) | — | deployer | TOTP depends on synchronised server time | E:23-H-3 | ≡? 25:186–191 |
| SF2-019 | OPS | obligation | ASVS 15.1.3; ASVS 15.2.2; ASVS 15.3.4 | satisfied-by-procedure | deployer | Raw request rate on unlisted routes is bounded at the edge | F:26-R-1; F:26-H-1 | ≡? G:31-H-2; ≡? G:29-H-2 |
| SG1-006 | OPS | obligation | IM8 as-10; IM8 dp-3; PRD §Security (transport) | conditional-pass | deployer | Nobody is named to terminate TLS in front of the application | G:17-R-7; G:15-R-1 | ≡? 25:553 (worked row "Who terminates TLS"); D:15-R-1 |
| SG2-021 | OPS | obligation | ASVS 16.2.1 | satisfied-by-procedure | deployer | Host sudo or ssh trail attributes runner runs to a human | G:28-H-6 |  |
| SG3-020 | OPS | obligation | ASVS 15.3.4 | — | deployer | Declare whether the public hostname publishes AAAA | G:31-H-1 |  |
| SG3-021 | OPS | obligation | ASVS 2.4.1; ASVS 15.3.4 | — | deployer | Edge per-source limit aggregates IPv6 at a chosen prefix | G:31-H-2 | ≡? F:26-H-1; G:29-H-2 |
| SG3-022 | OPS | obligation | IM8 lm-16; PDPA retention limitation obligation | — | deployer | Edge access logs keep raw addresses for a bounded window | G:31-H-3 |  |
| SG3-024 | OPS | obligation | ASVS 15.3.4 | — | deployer | Trusted proxy writes IP literals in X-Forwarded-For | G:31-H-5 | ≡? B:09-A-7 handover (name trusted proxies; REJ-015) |
| SG3-025 | RL | deviation | Std §5:452; Std §3.5:379 | fail | none | Password-reset redemption is limited per source only | G:32-R-1 | ≡? B:09-R-13; C:10-R-5 |
| SG1-026 | RL | fidelity | Std §5:453 | n/a | none | Distributed limiter and session consistency not supported | G:16-R-7 | ≡? D:16-R-5; B:09-R-10; B:09-A-10 |
| SG2-024 | RL | fidelity | ASVS 15.1.3 | pass-with-note | none | Reserve constants k = 1.5 and b = 17 KB are planning values | G:29-R-3 |  |
| SB-014 | RL | not-built | Std §5:453 | n/a | shared | Multi-instance limiter consistency not built; single instance enforced | B:09-R-10 | ≡? D:16-R-5; ≡? G:15-R-6; ≡? D:15-R-4 |
| SD2-009 | RL | obligation | ASVS 6.1.1; NIST SP 800-63B-4 §3.2.2 | — | shared | Pin aggregate request capacity and keep saturation meters (TM-07) | D:15-H-3 | ≡? G:15-R-6 |
| SG2-031 | RL | obligation | ASVS 2.1.3; ASVS 15.1.3; ASVS 2.4.1 | satisfied-by-procedure | shared | Size the database volume to the sizing line | G:29-H-1 |  |
| SG2-032 | RL | obligation | IM8 lm-16; ASVS 2.4.1 | satisfied-by-procedure | deployer | Shedding denies new logins: know what clears it | G:29-H-2 | ≡? G:31-H-2; ≡? F:26-H-1 |
| SH-001 | RL | obligation | ASVS 15.3.4 | conditional-pass | shared | Trusted proxies named explicitly; framework forwarded-header strategy prohibited | 25:122 | ≡? G:31-H-5 (SG3-024) |
| SB-015 | RL | residual | Std §5:452 | pass-with-note | none | Password-change budget is per source, so one user can exhaust it | B:09-R-11 |  |
| SB-019 | RL | residual | ASVS 6.1.1 | pass-with-note | none | Source rotation defeats the per-source lockout-cardinality axis | B:09-R-2 | ≡? G:31-R-4; ≡? F:26-R-4 |
| SB-020 | RL | residual | ASVS 6.1.1 | pass-with-note | none | Shared NAT can trip the cardinality axis for legitimate users | B:09-R-3 | ≡? G:31-R-10 |
| SD2-004 | RL | residual | Std §5:453 | — | application | Rate limiters are in-memory and single-instance | D:15-R-4; D:16-R-5 | ≡? B:09-A-10; 25:140–146; G:16-R-7 |
| SD2-023 | RL | residual | Std §5:452 | — | none | Per-account 429 unreachable by pure failure traffic on the username axis | D:16-R-15 |  |
| SD2-025 | RL | residual | — | — | none | Limiter and cache clocks lose monotonicity on a clock step | D:16-R-17 |  |
| SF2-022 | RL | residual | ASVS 15.3.4 | pass-with-note | none | Source-key rotation defeats the session-miss budget | F:26-R-4 |  |
| SG1-015 | RL | residual | ASVS 6.1.1; NIST SP 800-63B-4 §3.2.2 | pass-with-note | shared | Single-instance, per-key limiters with no global bulkhead | G:15-R-6 | ≡? D:15-R-4; D:15-H-3 |
| SG2-023 | RL | residual | ASVS 2.4.1 | pass-with-note | none | Count cap denies new logins at about 196 source keys | G:29-R-2 |  |
| SG2-025 | RL | residual | ASVS 2.4.1 | pass-with-note | none | Row COUNT cost under concurrent writes is unverified | G:29-R-4 |  |
| SG3-005 | RL | residual | NIST SP 800-63B-4 §3.2.2 | — | none | No second /56 network key for the cardinality axis | G:31-R-1 |  |
| SG3-007 | RL | residual | NIST SP 800-63B-4 §3.2.2 | — | none | A /64 source key stops a host, not a subscriber | G:31-R-4 |  |
| SG3-008 | RL | residual | NIST SP 800-63B-4 §3.2.2 | — | none | Teredo and 6to4 addresses key at the wrong granularity | G:31-R-5 |  |
| SG3-009 | RL | residual | NIST SP 800-63B-4 §3.2.2 | — | none | A dual-stack host gets two budgets | G:31-R-6 |  |
| SG3-011 | RL | residual | ASVS 15.3.4 | — | none | Unparseable forwarded tokens share one bucket | G:31-R-8 |  |
| SG3-012 | RL | residual | NIST SP 800-63B-4 §3.2.2 | — | none | Singapore ISP prefix delegation sizes unverified | G:31-R-9 |  |
| SG3-013 | RL | residual | PRD Story 3 AC3; ASVS 6.1.1 | — | none | A full cardinality set refuses non-members for about an hour | G:31-R-10 | ≡? B:09-R-3 |
| SD2-030 | RL | trigger | Lockout-cardinality k = 5 and the session cleanup cron pinned at every minute | — | application | Any change to k or to the cleanup cron | D:16-T-3 |  |
| SG2-027 | RL | trigger | The N_max count cap, set by the measured range (ADR-041) | — | none | A measurement extends per-row cost beyond 100,000 rows | G:29-T-1 |  |
| SG2-029 | RL | trigger | Free-space reserve scaled with row count, not file size (ADR-041) | — | none | An H2 upgrade changes MVStore retention or compaction | G:29-T-3 |  |
| SG3-016 | RL | trigger | Per-source lockout-cardinality axis pricing (k = 5, planning population of 100 accounts) | — | application | Change to k, or the account population well past 100 | G:31-T-2 |  |
| SG3-017 | RL | trigger | Declined /56 network key for the cardinality axis | — | deployer | User population known to sit on /56s | G:31-T-3 |  |
| SG3-018 | RL | trigger | IPv6 /64 source-key prefix choice | — | application | APNIC prefix-length data or an RFC 9977 feed appears | G:31-T-4 |  |
| SB-023 | RUN | deviation | NIST SP 800-63B-4 §4.2.2.1; NIST SP 800-63B-4 §4.2.2.2 | fail | shared | Shell rebinding path meets no NIST account-recovery option | B:09-R-15 | ≡? 25:557–561 (worked row: break-glass access restoration); ≡? 25:651–656 |
| SG2-007 | RUN | deviation | ASVS 6.4.6 | partial | none | Operator chooses the password on the break-glass runner path | G:28-R-1 |  |
| SB-021 | RUN | obligation | NIST SP 800-63B-4 §3.2.2; ASVS 6.1.1 | conditional-pass | deployer | Password-cap recovery needs a named operator with deploy-level access | B:09-R-4 | ≡? G:28-H-1; ≡? G:28-H-2; ≡? G:30-H-4; ≡? 25:470–475 |
| SE2-035 | RUN | obligation | ASVS 6.1.1; NIST SP 800-63B-4 §3.2.2 (disabled authenticators rebind) | satisfied-by-procedure | shared | Break-glass clears a factor when no enrolled admin can act | E:23-H-1 | ≡? 25:159–177 (break-glass second trigger); 25:557–560 (break-glass worked row) |
| SG2-014 | RUN | obligation | ASVS 6.1.1 | conditional-pass | deployer | Re-rehearse runner recovery whenever a recurrence trigger fires | G:28-H-7; G:28-T-1; G:28-T-2; G:28-T-3 | ≡? F:25 step-7 recurrence (25:913–915) |
| SG2-017 | RUN | obligation | ASVS 6.1.1 | conditional-pass | deployer | A named, reachable runner operator with deploy-level access | G:28-H-1 | ≡? 25:412–414 (runner ownership statement, "Name who.") |
| SG2-018 | RUN | obligation | ASVS 6.1.1 | pass-with-note | shared | Runner recovery is a planned outage | G:28-H-2 |  |
| SG2-019 | RUN | obligation | ASVS 6.1.1 | pass-with-note | shared | Run recovery from the jar matching the deployed version | G:28-H-3 |  |
| SG2-020 | RUN | obligation | ASVS 6.4.1 | pass | shared | Hand the operator-set password to the user out of band | G:28-H-5 | ≡? 25:901–904 (TM-12 row edited in place: "no secret is emitted") |
| SG2-009 | RUN | residual | Runner plan/apply digest binds a change to the previewed database state (ADR-074) | satisfied-by-procedure | deployer | A stale database copy passes every runner check | G:28-R-3; G:28-H-4 |  |
| SG2-010 | RUN | residual | Recovery time for a mass-lockout event | satisfied-by-procedure | deployer | Mass-lockout recovery time is uncosted | G:28-R-4; G:28-H-8 | ≡? B:09-R-7 |
| SG2-011 | RUN | residual | Std §3.3 | pass-with-note | none | A runner that dies after commit loses its outcome row | G:28-R-5 | ≡? E:21 absence-detection amendment (21:807–814) |
| SG2-012 | RUN | residual | Runner interactive-mode input handling on JDK 22 and later | — | application | Null-console refusal stops refusing on JDK 22 | G:28-R-6 |  |
| SB-024 | RUN | trigger | The conditional ASVS 6.1.1 pass, which rests on a recovery runner that can execute | — | deployer | A failed first recovery rehearsal grades ASVS 6.1.1 as fail | B:09-T-2 | ≡? G:28-T-4; ≡? F:28-T-x1 |
| SF2-018 | RUN | trigger | The ASVS 6.1.1 (L1) pass-with-note verdict; the offline recovery-runner decision (ADR-072) | — | none | A recovery rehearsal fails | F:28-T-x1 | ≡? G:28-T-4; ≡? B:09-T-2; ≡? stage H (25:916–917) |
| SG2-015 | RUN | trigger | The ASVS 6.1.1 (L1) pass-with-note resting on the offline recovery runner (ADR-072) | — | deployer | A red recovery rehearsal | G:28-T-4 | ≡? B:09-T-2; ≡? F:28-T-x1 |
| SG2-016 | RUN | trigger | No mail transport outside dev; runner recovery designed around it (ADR-073) | — | none | Mail transport enters scope | G:28-T-5 | ≡? G:15-T-1; ≡? G:31-T-5; ≡? D:25-T-1 |
| SA-011 | SES | deviation | Std §3.5; Std §5:499 | fail | application | Session cookie is SameSite=Strict, not the standard's Lax | A:02-R-3 | ≡? E:20-A-1 |
| SE1-001 | SES | deviation | Std §3 (SameSite=Lax mandate); Std §5:499 | fail | application | Session and CSRF cookies set SameSite=Strict, not the mandated Lax | E:20-R-1 |  |
| SG2-022 | SES | deviation | Pre-login sessions share the 15-minute idle timeout with no second timer (session design) | pass-with-note | none | Anonymous session expiry pinned at creation plus the idle window | G:29-R-1 | ≡? B:08-R-5 |
| SA-012 | SES | fidelity | Std §3.5 | conditional-pass | none | Secure and __Host- cookie posture unexercised outside dev | A:02-R-4 | ≡? 25:563 |
| SC-011 | SES | note | Std §5:466; OWASP Forgot Password Cheat Sheet | pass | none | Reset redemption terminates all sessions automatically | C:10-R-11 |  |
| SF2-025 | SES | note | draft-ietf-httpbis-rfc6265bis §5.7; draft-ietf-httpapi-ratelimit-headers | — | none | Cookie prefix adopted from an Internet-Draft; RateLimit headers declined for one | F:08-R-x1 |  |
| SD2-010 | SES | obligation | ASVS 13.2.2; ASVS 16.4.2 | fail | shared | Session attributes are JDK-deserialised; an allowlist filter is required | D:15-H-4 |  |
| SB-010 | SES | residual | Std §3.5:343 | pass-with-note | none | Evicted user gets a generic 401 with no signed-in-elsewhere reason | B:08-R-4 |  |
| SF1-011 | SES | residual | Session token confidentiality at rest (no standard clause graded) | — | none | Read access to the dev H2 file allows live session hijack | F:24-R-9 |  |
| SF2-023 | SES | residual | RFC 6265 section 4.2.2; draft-ietf-httpbis-rfc6265bis §5.7 | partial | none | Duplicate session cookies: first-honoured id is attacker-influenced | F:26-R-5 |  |
| SF2-026 | SES | trigger | The `__Host-SESSION` cookie contract | — | none | A non-root servlet context path is configured | F:26-T-2 |  |
| SG2-028 | SES | trigger | Anonymous means no AUTH_INSTANT, and the row gauge's null principal agrees (REJ-091, ADR-0 | — | none | AUTH_INSTANT or PRINCIPAL_NAME set anywhere but password login | G:29-T-2 |  |
| SG2-030 | SES | trigger | Session cleanup cron pinned at 0 * * * * * (per-source row figure and disk sizing) | — | none | Any change to the session cleanup cron | G:29-T-4 |  |
| SA-008 | STD | defect | im8-review st-3 required frontend snippet | — | none | im8-review st-3 footer snippet misspells href | A:01-R-9 |  |
| SA-013 | STD | defect | Log_Schema.md; Logging AuthN recipe §4, §6.1, §8.1 | — | none | Recipes use three fields Log_Schema.md does not define | A:03-R-1; A:03-R-2; A:03-R-3 |  |
| SA-014 | STD | defect | Log_Schema.md (User table, `session.hash`) | — | none | session.hash example is MD5-length though prose says SHA-256 | A:03-R-4 |  |
| SA-015 | STD | defect | Logging Std §3.1; Log_Schema.md | — | none | Thread and logger field names differ between standard and schema | A:03-R-5 |  |
| SA-016 | STD | defect | Log_Schema.md (`event.action` enum) | — | none | No event.action value separates role change, enable/disable and delete | A:03-R-6 |  |
| SA-017 | STD | defect | Log_Schema.md (`event.category` enum) | — | none | No identity or authentication event.category | A:03-R-7 |  |
| SA-018 | STD | defect | Log_Schema.md (title and scope) | — | none | Log_Schema.md scoped to batch apps yet binds interactive apps | A:03-R-8 |  |
| SA-019 | STD | defect | Std §3.4; Logging AuthN recipe §4, §11 | — | none | AuthN recipe logs lockout at ERROR with 423; standard says WARN | A:03-R-9 |  |
| SA-020 | STD | defect | Log_Schema.md (User table) | — | none | Log_Schema.md lags ECS on user.target.id and user.target.roles | A:03-R-10; A:03-R-11 | ≡? D:13-R-5; D:13-A-4 |
| SA-028 | STD | defect | Headers recipe dev filter chain (`PathRequest.toH2Console()`) | — | none | Headers recipe H2 console matcher needs a separate Boot 4 module | A:04-R-2 |  |
| SA-029 | STD | defect | Session-login recipe rate limiter | — | none | Recipe Bucket4j API does not exist; library unnamed and unpinned | A:04-R-3 |  |
| SA-030 | STD | defect | Admin, self-service and RBAC recipes (code listings) | — | none | Recipes present starter-internal types as public API | A:04-R-4 |  |
| SA-031 | STD | defect | Admin and self-service recipes (`hasRole`); RBAC recipe (`hasAuthority`); Std Q18 | — | none | Recipes mix hasRole and hasAuthority for one authority set | A:04-R-5 | ≡? C:11-R-12; C:11-R-13 |
| SA-032 | STD | defect | Self-service recipe (`ChangeCurrentUserPasswordCommand`) | — | none | Recipe injects fields into an object built with new | A:04-R-6 |  |
| SA-034 | STD | defect | Admin recipe `PasswordChangeFilter`; self-service recipe `PasswordChangeFilter` | — | none | Two prescribed forced-change allowlists are incompatible | A:04-R-8 | ≡? C:11-R-9 |
| SA-035 | STD | defect | RBAC recipe §3 Step 3 filter-chain order | — | none | RBAC recipe registers url-guards before the whitelist | A:04-R-9 | ≡? C:11-R-12 |
| SB-007 | STD | defect | Std §2:82 | — | none | Failure Path 1 lets any caller end a user's live session | B:08-R-1 |  |
| SB-008 | STD | defect | Std §5:446 | — | none | Prescribed redeemed-CSRF-token test cannot pass under the mandated repository | B:08-R-2 |  |
| SB-009 | STD | defect | Std §5:499; Std §3.5:346–350 | — | none | CSRF-cookie attribute clauses are dead text under the synchroniser pattern | B:08-R-3 | ≡? E:20-R-1 (SameSite half) |
| SB-013 | STD | defect | Std §3.5:378; Std §5:452 | — | none | Per-account login throttle is unreachable by pure-failure attack traffic | B:09-R-9; B:02-R-1 | ≡? D:16-R-15 |
| SB-016 | STD | defect | Std §3.5:379; Std §5:452 | — | none | Per-account limit mandated where the account is not yet knowable | B:09-R-13 | ≡? C:10-R-5; ≡? G:32-R-1 |
| SC-001 | STD | defect | Std §2:62–64; Std §3.5:357; Std §6:619; Std Questions Q22:565 | — | none | Standard mandates two mutually exclusive admin-reset models | C:10-R-1 |  |
| SC-003 | STD | defect | Std §2:66; Std Questions Q22:543 | — | none | SHA-256 token hashing is non-normative in the Standard | C:10-R-3 |  |
| SC-004 | STD | defect | Std §2:62; Std §2:126; Std §5:459; Std §6:618 | — | none | Reset expiry stated in four incompatible modalities | C:10-R-4 |  |
| SC-005 | STD | defect | Std §3.5:379; Std §5:452 | — | none | Per-account limit on token redemption is unknowable before lookup | C:10-R-5 |  |
| SC-006 | STD | defect | Self-service change recipe (`ChangeCurrentUserPasswordCommand`, `revokeOtherSessions`) | — | none | Self-service change recipe skips policy, current password and session revocation | C:10-R-6 |  |
| SC-009 | STD | defect | Std §2:131 | — | none | Redemption versus account state is undefined in the Standard | C:10-R-9 |  |
| SC-012 | STD | defect | Std §2:166–167; Std §3.1:240 | — | none | Forced-change completion omits current password and history | C:10-R-12 |  |
| SC-014 | STD | defect | Std §5:517 | — | none | Notification requirement exists only in the test section | C:10-R-14 |  |
| SC-027 | STD | defect | Admin recipe `createUser` tombstone check | — | none | Admin create checks tombstones for username but not email | C:11-R-8 |  |
| SC-028 | STD | defect | Recipes' forced-change allowlists | — | none | Two prescribed forced-change allowlists are mutually incompatible | C:11-R-9 |  |
| SC-029 | STD | defect | Admin recipe `updateUser`; Std Decision Logic §120; PRD Story 9 | — | none | Admin update recipe permits self-disable | C:11-R-10 |  |
| SC-030 | STD | defect | Std §3.5:384; Admin recipe `setRoles` | — | none | Role-change clause contradicts the admin recipe | C:11-R-11 |  |
| SC-031 | STD | defect | RBAC recipe `url-guards` | — | none | Prescribed RBAC guard configuration neither binds nor compiles | C:11-R-12 |  |
| SC-032 | STD | defect | RBAC and admin recipes (authority naming) | — | none | Three authority conventions for one resource | C:11-R-13 | ≡? 04-R-5 (routing REJ-023) |
| SC-033 | STD | defect | Admin, RBAC and CSRF recipes (compile-level) | — | none | Compile-level defects in the prescribed recipes | C:11-R-14 |  |
| SE2-001 | STD | defect | MFA_Core §7 Negative Requirements Summary | — | none | MFA_Core negative-requirement list skips NEG-REQ-03 | E:22-R-1 |  |
| SE2-002 | STD | defect | MFA_Core §7 NEG-REQ-04 | — | none | NEG-REQ-04 cites a section that holds no such rule | E:22-R-2 |  |
| SE2-003 | STD | defect | MFA_Core §7 Negative Requirements Summary | — | none | Negative summary omits the standard's own highest-severity prohibitions | E:22-R-3 |  |
| SE2-004 | STD | defect | MFA_Core §8 | — | none | CON-06 is referenced but never defined | E:22-R-4 |  |
| SE2-005 | STD | defect | MFA_Core §3.1; MFA_Core §3.4; MFA_Core §6.2; MFA_Core §8 | — | none | TOTP digit length stated three inconsistent ways | E:22-R-5 |  |
| SE2-006 | STD | defect | MFA_Core recipes, TotpUtilities class | — | none | TotpUtilities declared twice with disjoint members | E:22-R-6 |  |
| SE2-007 | STD | defect | MFA_Core §3.4 factor lockout (10 failures, 1-hour window, locked until administrative revi | — | none | The prescribed 1-hour sliding window does not slide | E:22-R-7 |  |
| SE2-008 | STD | defect | MFA_Core Recipe 12 missing-code check | — | none | Empty-string TOTP code counts as a failed attempt | E:22-R-8 |  |
| SE2-009 | STD | defect | MFA_Core §3.3; MFA_Core §3.4; MFA_Core §4.2; MFA_Core Recipe 12 | — | none | Three incompatible readings of the factor failure counter | E:22-R-9 |  |
| SE2-010 | STD | defect | MFA_Core §4.2 (lockedAt cleared by admin); MFA_Core Recipe 11 | — | none | No TOTP unlock path exists anywhere in the corpus | E:22-R-10 |  |
| SE2-011 | STD | defect | MFA_Core Recipe 7 exception handler and its Validation Rules | — | none | 412 prescribed in prose but produced by no recipe code | E:22-R-11 |  |
| SE2-012 | STD | defect | MFA_Core §3.2 (mandated detail string for incomplete enrolment) | — | none | Mandated "User Details not found." detail is unproducible | E:22-R-12 | ≡? A:06-A-4 (REJ-092 deviation row: detail prose-matching dropped) |
| SE2-013 | STD | defect | MFA_Core §3.2 | — | none | Claim that HTTP status alone distinguishes MFA failures is false | E:22-R-13 |  |
| SE2-014 | STD | defect | MFA_Frontend/Standalone §3.2; MFA_Frontend/Standalone §5 | — | none | Generate-QR button enablement contradicts itself | E:22-R-14 |  |
| SE2-015 | STD | defect | MFA_Core §3.4 (±1 period gives ±30 s tolerance) | — | none | Claimed ±30 s skew tolerance is one forward window after first success | E:22-R-15 |  |
| SE2-016 | STD | defect | MFA_Core §3.4 (encryption key rotated at least yearly) | — | none | Yearly key-rotation MUST is unimplementable without a key-version column | E:22-R-16 |  |
| SE2-017 | STD | defect | MFA_Core §3.4 enforced-constraint tags; MFA_Core Questions Q13, Q15, Q16 | — | none | Enforced constraints presented elsewhere as integrator choices | E:22-R-17 |  |
| SE2-018 | STD | defect | MFA_Core Recipe 5 (OTPUserDetails entity); MFA_Core Recipe 12 | — | none | Recipe 12 cannot compile against Recipe 5's entity | E:22-R-18 |  |
| SE2-019 | STD | defect | MFA_Core Recipe 5 (provisioning deferred to cloud recipes) | — | none | No provisioning recipe exists in the followed corpus | E:22-R-19 |  |
| SE2-020 | STD | defect | MFA_Core Recipe 13 (MFATypeContainer startup validation) | — | none | Recipe 13 requires a PIN provider and an undeclared method | E:22-R-20 |  |
| SF1-002 | STD | defect | Mcc_Project_Bootstrap_Application_Standard §3.6 profile-configuration contract, as cited b | — | none | Cited profile-configuration contract and its loader do not exist | F:24-R-2 |  |
| SF1-027 | STD | note | ASVS 5.0 (all requirements); ASVS 6.5.2; NIST SP 800-63B-4 §3.1.2.2 | — | none | Citation hygiene for ASVS rows | F:24-R-25 | ≡? G:25-R-2 (17:225–227) |
| SG1-009 | STD | note | OWASP ASVS 5.0 (every requirement cited); ASVS V6.5 prose | — | none | ASVS citation hygiene applied to every row | G:25-R-2 | ≡? F:24-R-25 |
| SH-026 | STD | note | ASVS 2.4.1; ASVS 2.1.3; ASVS 2.3.2; ASVS 15.1.3; ASVS 15.2.2; ASVS 15.3.4; ASVS 16.1.1; AS | pass-with-note | none | Anti-automation and availability hooks adopted because cheap at L2 | 26:582 | ≡? SC-016 (declared L1 target) |
