# AppStandards Testing Schedule (MCC)

This project tracks MCC AppStandards implementations only. Standalone profile is tracked in a separate test project.

---

### Schedule

> ```mermaid
> %%{init: {'themeVariables': {'excludeBkgColor': 'rgba(255,235,59,0.1)', 'activeTaskBkgColor': '#b8860b', 'activeTaskBorderColor': '#8a6508', 'doneTaskBkgColor': '#2e7d32', 'doneTaskBorderColor': '#1b5e20', 'taskTextDarkColor': '#ffffff', 'taskTextOutsideColor': '#ffffff', 'gridColor': 'transparent'}, 'themeCSS': '.tick line, .grid .tick line, .grid path { stroke: none !important; opacity: 0 !important; }'}}%%
> gantt
>     dateFormat  YYYY-MM-DD
>     axisFormat  %b %d
>     todayMarker stroke:#2563eb,stroke-width:3px
>     excludes 2026-07-06,2026-07-07,2026-07-08,2026-07-09,2026-07-10,2026-07-11,2026-07-12,2026-07-13,2026-07-14,2026-07-15,2026-07-16,2026-07-17,2026-07-18,2026-07-19,2026-07-20,2026-07-21,2026-07-22,2026-07-23,2026-07-24,2026-08-03,2026-08-04,2026-08-05,2026-08-06,2026-08-07
>
>     Shared Auth - fixed-key       :done, sa1, 2026-06-03, 2026-06-13
>     File Standards                :done, file1, 2026-06-13, 2026-06-23
>     MCNS Core                     :done, mcns1, 2026-06-23, 2026-06-29
>     MPDS Retrieval                :done, mpds1, 2026-06-29, 2026-07-03
>     Shared Auth - generated-key   :crit, sa2, 2026-09-11, 2026-09-18
>     MCNS Batch                    :crit, mcns2, 2026-09-18, 2026-09-25
>     Mfa Standards                 :done, mfa1, 2026-07-24, 2026-07-30
>     User Standards (1)            :done, user1, 2026-07-30, 2026-08-03
>     User Standards (2)            :crit, user2, 2026-09-04, 2026-09-11
>     Report Standards              :done, rep1, 2026-06-15, 2026-06-30
>     Interface Standards           :crit, iface1, 2026-08-15, 2026-09-04
>     Logging Standards (1)         :done, log1, 2026-06-03, 2026-08-03
>     Logging Standards (2)         :crit, log2, 2026-08-15, 2026-09-25
>     MCC First-Cut Test            :crit, mcc1, 2026-08-11, 2026-08-14
> ```

Remarks:
- On course from 6 to 27 Jul and 3 to 7 Aug (blocked out).
- Interface Standards — MCC Claude trial budget exceeded, only 50% generated.
- Report Standards — assigned to Jie Hui.

| Standard | Sub-standard | Expected Start | Expected End | Status | Generated Code |
|---|---|---|---|---|---|
| **Appfw-Mcc-Standards** | First-cut MCC test | 2026-08-11 | 2026-08-14 | ✅ Completed | [d2-test-project-1/mcc-standards-test](https://github.com/SGTechStack/d2-test-project-1/tree/mcc-standards-test), [d2-test-project-1/test-mcns](https://github.com/SGTechStack/d2-test-project-1/tree/test-mcns), [d2-test-project-1/test-mpds-2](https://github.com/SGTechStack/d2-test-project-1/tree/test-mpds-2) |
| **Appfw-Mcc-Standards** | Shared Auth — fixed-key mode (M2M client credentials, JWKS, private_key_jwt) | 2026-06-03 | 2026-06-13 | ✅ Completed | [d2-test-project-1/mcc-standards-test](https://github.com/SGTechStack/d2-test-project-1/tree/mcc-standards-test), [d2-test-project-1/test-mcns](https://github.com/SGTechStack/d2-test-project-1/tree/test-mcns), [d2-test-project-1/test-mpds-2](https://github.com/SGTechStack/d2-test-project-1/tree/test-mpds-2)  |
| **Appfw-File-Standards** | | 2026-06-13 | 2026-06-23 | ✅ Completed | [d2-test-project-1/main](https://github.com/SGTechStack/d2-test-project-1/tree/main), [d2-test-project-1/test-file-upload](https://github.com/SGTechStack/d2-test-project-1/tree/test-file-upload) |
| **Appfw-Mcc-Standards** | Shared Auth — generated-key mode (key rotation, protected key history, overlap rollover) | 2026-09-11 | 2026-09-18 | ❌ Not done | - |
| | MCNS Core (single notification, rate limit, PII masking) | 2026-06-23 | 2026-06-29 | ✅ Completed | [d2-test-project-1/test-mcns](https://github.com/SGTechStack/d2-test-project-1/tree/test-mcns) |
| | MCNS Batch (retry pipeline) | 2026-09-18 | 2026-09-25 | ❌ Not done | - |
| **Appfw-Mcc-Standards** | MPDS Retrieval (backend, frontend) | 2026-06-29 | 2026-07-03 | ✅ Completed | [d2-test-project-1/test-mpds-2](https://github.com/SGTechStack/d2-test-project-1/tree/test-mpds-2) |
| **Appfw-Mfa-Standards** | | 2026-07-24 | 2026-07-30 |  ✅ Completed | [d2-test-project-1/mcc-standards-test](https://github.com/SGTechStack/d2-test-project-1/tree/mcc-standards-test) |
| **Appfw-User-Standards** | All other user sso | 2026-07-30 | 2026-08-03 | ✅ Completed | [d2-test-project-1/fixed-user-sso-login](https://github.com/SGTechStack/d2-test-project-1/tree/fixed-user-sso-login) |
| | MPDS integration (role sync), SSO Scheduled Account Inactivity and Role Management Jobs | 2026-09-04 | 2026-09-11 | ❌ Not done | [d2-test-project-1/fixed-user-sso-login](https://github.com/SGTechStack/d2-test-project-1/tree/fixed-user-sso-login) |
| **Appfw-Interface-Standards** | | 2026-08-15 | 2026-09-04 | ❌ Not done | [d2-test-project-1/fixed-user-sso-login](https://github.com/SGTechStack/d2-test-project-1/tree/fixed-user-sso-login) |
| **Appfw-Logging-Standards** | All other logging | 2026-06-03 | 2026-08-03 | ✅ Completed | [d2-test-project-1/fixed-user-sso-login](https://github.com/SGTechStack/d2-test-project-1/tree/fixed-user-sso-login) |
| | Logging Batch and Scheduled Jobs (basic scheduler logging, structured job-start/end events, per-job MDC context) | 2026-08-15 | 2026-09-25 | ❌ Not done | [d2-test-project-1/fixed-user-sso-login](https://github.com/SGTechStack/d2-test-project-1/tree/fixed-user-sso-login) |
| **Appfw-Report-Standards** | | 2026-06-15 | 2026-06-30 | ✅ Completed | [d2-test-project-1/main](https://github.com/SGTechStack/d2-test-project-1/tree/main) |

### Testing Status

| Standard | Sub-standard | Status |
|---|---|---|
| **Appfw-Project-Bootstrap** | Initial project generation with compose.yml (mock-m-sso integration, Spring profile hierarchy, TLS/signing key bootstrap, shared/features project structure split, .gitignore, README) | ✅ Done |
| **Appfw-User-Standards** | SSO OAuth2/OIDC Session Translation (PKCE + token validation, session creation, logout endpoint, concurrent session limits, idle/absolute timeout, session cookie attributes, callback rate limiting, back-channel logout) | ✅ Done |
| | SSO MCC Provider Hint and private_key_jwt | ✅ Done |
| | SSO User Auto-Provisioning and Authority Mapping | ✅ Done |
| | SSO Scheduled Account Inactivity and Role Management Jobs | ❌ Not done |
| | Shared Security Controls (RBAC, request security, self-read access) | ✅ Done |
| | MPDS Integration (**role synchronization**) | ❌ Not done |
| **Appfw-Mcc-Standards** | Shared Auth — fixed-key mode (M2M client credentials, JWKS, private_key_jwt) | ✅ Done |
| | Shared Auth — generated-key mode (key rotation, protected key history, overlap rollover) | ❌ Not done |
| | MCNS Core (single notification, rate limit, PII masking) | ✅ Done |
| | MCNS Batch (**retry pipeline**) | ❌ Not done |
| | MPDS Retrieval (**backend**, **frontend**) | ✅ Done |
| **Appfw-Mfa-Standards** | MFA Core (TOTP factor) | ✅ Done |
| | MFA MCC (OTP via MCNS, KMS-encrypted TOTP) | ✅ Done |
| | MFA Critical Transaction (AOP enforcement) | ✅ Done |
| | MFA Frontend MCC | ✅ Done |
| **Appfw-File-Standards** | Common File Upload Controls (validation, state machine, lifecycle, RFC 9457) | ✅ Done |
| | SFS Scanner and Cloud Storage (SFS processing, scheduled phases, ShedLock, zombie cleanup) | ✅ Done |
| **Appfw-Interface-Standards** | Shared: Batch Job Orchestration and Monitoring (custom job runner, **Spring Batch infrastructure**, **job monitoring**) | ❌ Not done |
| | Shared: Entity Modeling with @MAGEntity and @Position | ❌ Not done |
| | Shared: Interface Transaction State Machine | ❌ Not done |
| | Shared: Security — Sender Authorization and Integrity Verification | ❌ Not done |
| | Shared: Structured Logging and Transaction Correlation (basic logging, **per-transaction correlation IDs**) | ❌ Not done |
| | Inbound: Batch Reception and Validation (file reception + XLSX parse, **hash integrity**, **RSA signature**, **sender authorization**) | ❌ Not done |
| | Inbound: Error Handling, Retry, and Recovery (terminal error states, **retry mechanism**, **recovery flow**) | ❌ Not done |
| | Inbound: Idempotency and Duplicate Detection | ❌ Not done |
| | Outbound: Batch Generation and Transmission | ❌ Not done |
| | Outbound: Job Wiring — ItemReader and JobFactory | ❌ Not done |
| **Appfw-Logging-Standards** | Centralising Audit Logging With a Typed Module | ✅ Done |
| | Custom Structured Log Encoder | ✅ Done |
| | Enriching Logs With MDC | ✅ Done |
| | Sensitive Data Masking For Logs | ✅ Done |
| | Logging AuthN and AuthZ Events (M2M auth events, **SSO login/logout events**, **token validation failures**, **callback validation failures**, **CSRF failures**, **authorization denials**, **session lifecycle events**) | ✅ Done |
| | Logging Batch and Scheduled Jobs (basic scheduler logging, **structured job-start/end events**, **per-job MDC context**) | ❌ Not done |
| | Structured Logging Trace Correlation and Context Propagation (MDC trace-id, **OpenTelemetry context propagation**, **W3C trace headers**) | ✅ Done |
| | Logging Application Lifecycle Events | ✅ Done |
| | Logging Exceptions With Enhanced Details | ✅ Done |
| **Appfw-Report-Standards** | Fixed-Schema Report (JasperReports / JRXML) — Report Generation Standard infra + JRXML template (#102), Daily Clinic Activity PDF/XLSX/CSV download endpoint (#103), frontend Reports page (#104) — all closed + committed | ✅ Done |
| | Programmatic Report (dynamic columns, JasperDesign) — Sessions-by-Doctor Matrix (PRD #92): design builder (#107), layout cache (#110), matrix aggregation (#108), NRIC masking (#109), rendering (#111), endpoint (#112), too-many-doctors notice (#113), frontend (#114) — all closed + committed | ✅ Done |


---

## Estimated Credits Used

Based on code scan of complexity indicators: concurrency primitives, retry/resilience patterns, self-healing/scheduling, crypto operations, state machines, external integrations, and distributed coordination. 

> **Total: 4,490 credits (Done items only).** Fixed anchors: Shared Auth 1,000 (confirmed) · Report Standards 400 (150 Fixed-Schema + 250 Programmatic). Remaining items scaled by complexity verdict relative to anchors.


| Est. Credits | Sub-standard | LOC (src+test) | Concurrency | Retry/Resilience | Self-Healing | Crypto/Security | State Machine | External Integrations | Test Infra | Complexity Verdict |
|---|---|---|---|---|---|---|---|---|---|---|
| 1,000 | **Shared Auth** (M2M, JWKS, key rotation) | 9,305 | High (AtomicRef, volatile, ReentrantLock, SingleFlight) | High (Reacquire401Filter, consecutive-failure threshold) | High (JWKS refresh, stale invalidation, key rotation) | Very High (780 crypto refs — JWK, RSA, EC, private_key_jwt, signing) | Yes (active→overlap→retirement lifecycle) | Moderate (token endpoint, WebClient) | IT/Docker (WireMock JWKS + token endpoint) | **Highest in project** |
| 600 | **SFS Scanner + Cloud Storage** | 4,989 (combined fileupload module) | High (ShedLock + scheduled phases) | Low | High (zombie cleanup, polling with timeout, self-healing sweeps) | Low | Yes (shared state machine) | High (S3, SFS scanner, WebClient) | IT/Docker (S3 + SFS scanner mock) | **High** — async multi-phase scheduling + self-heal |
| 500 | **MFA Core** (TOTP factor) | 3,546 (core+OTP) | None (simple counters) | None | Low (lockout auto-expires after duration) | Moderate (265 refs — KMS encrypt/decrypt, HMAC, Base32) | No | Moderate (233 refs — KMS + MCNS OTP delivery) | Unit + IT/Docker (KMS mock) | **Moderate** — wide surface, KMS crypto, multiple auth providers, lockout lifecycle |
| 500 | **File Upload Controls** (validation, state machine, lifecycle) | ↑ same module | Moderate (ShedLock distributed lock) | Low (no retry) | Moderate (zombie sweep, stale timeout transitions) | Low | Yes (166 state refs — PENDING→DOWNLOADED→BAD_RESULT) | High (S3, SFS scanner, WebClient) | IT/Docker (S3 + state machine integration) | **High** — distributed state machine + hexagonal architecture |
| 450 | **MCNS Core** (notification, rate limit, PII) | 2,787 | Moderate (DB row-level locking for rate window) | Low (store-unavailable fallback only) | Low (rate window auto-resets) | Low (PII masking only) | No | High (231 refs — WebClient dispatch to MCNS) | IT/Docker (WireMock MCNS dispatch) | **High** — DB atomic rate limit + WebClient + PII masking + IT/Docker infra |
| 350 | **SSO MCC Provider Hint + private_key_jwt** | ~846 | None explicit | None | None | Moderate (private_key_jwt signing) | No | Moderate (OIDC provider) | E2E/SIT (full OIDC provider stack) | **Moderate** — dense security config, high consequence per line |
| 250 | **Report: Programmatic** (dynamic columns) | ~2,000 | Low (Caffeine cache) | None | None | None | No | None | Unit | **Moderate** — dynamic design builder, 8 issues, masking, validation |
| 250 | **MFA MCC** (OTP via MCNS, KMS-encrypted TOTP) | 1,193 | None | None | None | Moderate (KMS encrypt/decrypt for TOTP secrets) | No | Moderate (KMS + MCNS orchestration) | IT/Docker (KMS + WireMock MCNS) | **Moderate** — orchestrates two external services, provisioning end-to-end |
| 170 | **MFA Frontend MCC** | 903 | None | None | None | None | No | None | Unit | **Low-Moderate** — UI flows (challenge, verify, provision) |
| 150 | **Report: Fixed-Schema** (JRXML) | ~2,000 | None | None | None | None | No | None (JasperReports library) | Unit | **Low-Moderate** — template compile + 3-format export + audit |
| 110 | **MFA Critical Transaction** (AOP enforcement) | 382 | None | None | None | None | No | None | Unit | **Low** — single AOP aspect + startup validator |
| 90 | **SSO User Auto-Provisioning** | (included above) | None | None | None | None | No | Low | Unit | **Low** — user creation on first OIDC login + role mapping |
| 40 | **Audit Logging Module** | ~17 refs | None | None | None | None | No | None | Unit | **Trivial** — typed logger pattern, logback config |
| 10 | **Structured Log Encoder** | ~28 lines | None | None | None | None | No | None | Unit | **Trivial** — logstash-logback-encoder config |
| 10 | **MDC Enrichment** | ~17 refs | None | None | None | None | No | None | Unit | **Trivial** — MDC.put in filters |
| 10 | **Sensitive Data Masking** | ~17 refs | None | None | None | None | No | None | Unit | **Trivial** — pattern-based log masking |
| **4,490** | | | | | | | | | | **Done Total** |



