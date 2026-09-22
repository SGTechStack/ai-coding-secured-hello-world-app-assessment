# App Standards Compliance Report

**Document reviewed:** Appfw-Mfa-Standards/MFA_MCC/MCC_MFA_Application_Standard.md

**Reviewed against:** .claude/skills/app-standards-review/references/Standard Template.md

**Date:** 2026-04-15

**Document type:** App Standard (augmenting — extends `Base_Standalone_Application_Standard.md`)

---

> **Reviewer note:** This document is explicitly designed as an augmenting standard. It states: "All requirements, contracts, and flows from the Unified MFA Application Standard apply." Where sections are absent, this report distinguishes between deliberate deferral to the base standard and gaps that should be addressed even in an augmenting document.
>
> **Critical finding:** The document references `Base_MFA_Application_Standard.md` as its base standard (updated from `Unified_MFA_Application_Standard.md` in the previous revision), but that file does not exist in the repository. The actual base standard file is `Base_Standalone_Application_Standard.md` (in `Appfw-Mfa-Standards/MFA_Core/`). This broken cross-reference affects every deferral statement in the document ("see Unified §…").
>
> **Second review note:** This is the second review cycle. Between the first and second review: the inaccurate OTP generation formula (`rng.nextInt(10^N)`) was removed from Section 4.1 (Violation 2 resolved). The cross-reference was updated but remains broken — it now targets `Base_MFA_Application_Standard.md` instead of `Unified_MFA_Application_Standard.md`, but neither file exists. All other findings from the first review remain open.
>
> Section numbering follows the base standard's non-standard convention: 1–8 then 10 (skipping 9). Mapping used in this report: document Section 6 → Standard Template Section 5 (Test); document Section 7 → Standard Template Section 6 (Operational Runbook); document Section 10 → Standard Template Section 7 (Appendix). Document Sections 5 (Exception Handling) and 8 (Negative Requirements) are extra. Document Section 3.3 ("Audit & Logging") combines Standard Template 3.3 (Audit) and 3.4 (Logging); document Section 3.4 maps to Standard Template 3.5 (Security Contract).

---

## Overall Compliance Summary

| Section | Status | Issues |
|---|---|---|
| Do / Do Not Rules | VIOLATIONS FOUND | 1 violation |
| 1. Overview Addendum | PARTIAL | 2 |
| 2. Standard Flow Addendum | PARTIAL | 1 |
| 3.1 Inputs / Outputs | SATISFIED | 0 |
| 3.2 Error Contract | SATISFIED | 0 |
| 3.3 Audit Contract | PARTIAL | 2 |
| 3.4 Logging Contract | SATISFIED | 0 |
| 3.5 Security Contract | PARTIAL | 1 |
| 4. Architectural Design | PARTIAL | 1 |
| 5. Test & Validation Standard | PARTIAL | 2 |
| 6. Operational Runbook | PARTIAL | 2 |
| 7. Appendix | PARTIAL | 1 |

**Total issues: 13 (Violations: 1 | Partial: 12 | Missing: 0)**

> The 0 MISSING sections reflects the augmenting pattern — all Standard Template sections are addressed either directly or by deferral. The broken base cross-reference (`Base_MFA_Application_Standard.md`) is the most critical structural issue: every deferral in this document refers to a file that does not exist.

---

## Do / Do Not Rules

### VIOLATION 1 — Deprecated library class names throughout Section 4.1 and Section 4.2

**Rule broken:** "Do not describe the internals, classes, or file layout of the deprecated library being replaced."

**Location in document:** Section 4.1 (OTP Provider Model — Provider Class Hierarchy, Request-Scoped Context, Provider Summary table); Section 4.2 (Entity Schema heading)

**What was found:**
- `OTPAuthenticationProvider` — named as the OTP factor's provider class, described with a 7-step method body
- `MultiFactorAuthenticationProvider` — described as the base class that `OTPAuthenticationProvider` extends; stated to originate "from the Unified standard"
- `PINAuthenticationProvider`, `TOTPAuthenticationProvider` — referenced by name as existing providers the OTP provider follows the same pattern as
- `OTPRequestContext` — named context subtype with `getRequestOtp()` and `getRequestTotp()` methods documented
- `OTPUserDetails` — named entity type loaded during OTP verification
- `OTP_USER_DETAILS` — database table name used in Section 4.2 ("The physical table (`OTP_USER_DETAILS`) is shared between OTP and TOTP")

All of these are internal class and schema names from the deprecated MFA dependency library, following the same naming conventions as the violations identified in `Base_Standalone_Application_Standard.md` (`MFARequestContext`, `PINRequestContext`, `OTPRequestContext`).

**What must change:** Replace all class and table name references with behavioral descriptions:
- `OTPAuthenticationProvider` → "the OTP factor provider" or "the OTP verification component"
- `MultiFactorAuthenticationProvider` → "the base provider pattern defined in the base standard"
- `PINAuthenticationProvider`, `TOTPAuthenticationProvider` → "the PIN provider" and "the TOTP provider" (behavioral references)
- `OTPRequestContext` → "the request-scoped OTP context" (behavioral description)
- `OTPUserDetails` → "the user's OTP factor record"
- `OTP_USER_DETAILS` → "the shared OTP/TOTP user details table"

---

## Section 1 — Overview Addendum

**Status:** PARTIAL

### What is present
- **Purpose** — "Define the cloud-specific extensions to the Unified MFA Application Standard for services deployed on AWS. This addendum covers AWS KMS integration for TOTP secret encryption and the OTP factor..." ✅
- **Scope** — "Applies to all services covered by the Unified standard that are deployed on AWS, use AWS KMS for TOTP secret encryption, and/or implement the OTP factor via a cloud messaging service." ✅
- **Definitions** — Three new concepts: OTP, OTP TTL, MCNS. ✅

### What is missing or needs enhancement
1. **Definition count below threshold** — Only 3 definitions are provided. The Standard Template requires 5–7 key behavioral concepts per document. Even as an augmenting standard, this document introduces additional concepts not defined here or in the base standard: **AWS KMS** (referenced throughout as the encryption authority — its role, ARN-based identity, and key rotation behavior are all prescribed) and **Auto-Regeneration** (the side-effect behavior when verification is attempted on an absent or expired OTP — this appears in the Glossary in Section 10 but not in Section 1). Add at minimum these two definitions.

2. **Broken cross-reference to base standard** — The document header states "**Extends**: `Base_MFA_Application_Standard.md`" but no file named `Base_MFA_Application_Standard.md` exists in the repository. The base standard file is `Base_Standalone_Application_Standard.md` in `Appfw-Mfa-Standards/MFA_Core/`. Every deferral statement in this document (e.g., "in addition to the events in Unified §3.3", "Unified §4.2") references this broken link. Fix the cross-reference to point to the correct file.

---

## Section 2 — Standard Flow Addendum

**Status:** PARTIAL

### What is present
- **Happy path — OTP Generation (2.2)** — Five numbered steps: random OTP generation, bcrypt-hash, persist hash + expiry, deliver via MCNS, 200 OK. ✅
- **Happy path — TOTP Provisioning KMS detail (2.3)** — Explicit replacement of the abstract encryption step with the AWS KMS call. ✅
- **Factor Verification Addendum (2.4)** — OTP: TTL check, auto-regeneration side effect, missing-code/invalid-OTP exception paths; TOTP: KMS decrypt step. ✅
- **Failure paths (2.5)** — Three cloud-specific paths: missing/expired OTP (auto-regen), invalid OTP (bcrypt mismatch), KMS failure. ✅
- **Happy path sequence diagram (2.6)** — OTP Generation, TOTP Provisioning (KMS), OTP Verification all shown with KMS and MCNS participants. ✅
- **Failure path flowchart (2.6)** — OTP verification branches (absent, expired, mismatch), KMS failure path, MCNS failure path all shown. ✅

### What is missing or needs enhancement
- **TOTP verification with KMS decryption absent from sequence diagram** — The happy path sequence diagram shows TOTP Provisioning (including a KMS encrypt call) but does not show TOTP Verification, which also involves a KMS call (decrypt). The failure flowchart covers "TOTP Provisioning or Verification → KMS operation succeeds?" but the happy path for TOTP verification — specifically the `S->>KMS: Decrypt secret` and `KMS-->>S: Plaintext` steps followed by TOTP computation — is absent. Add a TOTP Verification sequence to the happy path diagram showing the KMS decrypt step, consistent with how TOTP Provisioning shows the KMS encrypt step.

---

## Section 3.1 — Inputs / Outputs

**Status:** SATISFIED

### What is present
- **Base Standard / Org Standard split** — Both present. ✅
- **Operations table** — Four cloud-specific operations covered: OTP Generation, OTP Verification (success), OTP Auto-Regeneration, TOTP Provisioning (KMS). Each row has input, HTTP response, database side effect, and external side effect. ✅
- **Single-use clearance documented** — "otp and otpTtl set to null (single-use — cleared on success)." ✅
- **OTP auto-regeneration modeled as a distinct operation** — With its trigger, response, and side effects. ✅
- **Org Standard** — "The plaintext OTP MUST NOT be returned in the response body. Delivery is exclusively via MCNS." ✅

### What is missing or needs enhancement
Nothing. Section 3.1 is fully compliant for an augmenting standard.

---

## Section 3.2 — Error Contract Addendum

**Status:** SATISFIED

### What is present
- **Base Standard / Org Standard split** — Both present. ✅
- **Four failure categories** — Missing/expired OTP (retryable — auto-regen), expired OTP header-present (retryable — auto-regen), invalid OTP bcrypt mismatch (terminal), KMS failure (terminal). ✅
- **HTTP status codes** — Present for all entries. ✅
- **Retryability** — Nuanced: auto-regeneration cases correctly labeled retryable; mismatch terminal. ✅
- **Org Standard** — Error responses must not expose internal exception messages; must conform to org API error schema. ✅

### What is missing or needs enhancement
Nothing. Section 3.2 is fully compliant for an augmenting standard.

---

## Section 3.3 — Audit Contract

**Status:** PARTIAL

> **Reviewer note:** Document Section 3.3 is titled "Audit & Logging Contract" and combines both audit and logging content. Standard Template 3.3 (Audit) is evaluated here; Standard Template 3.4 (Logging) is evaluated in the next section.

### What is present
- **Audit triggers** — Org Standard states: "Emit an audit event on: OTP generation, OTP verification (success and failure), and KMS access errors (in addition to the events in Unified §3.3)." ✅

### What is missing or needs enhancement
1. **Required audit event fields not defined** — The Standard Template requires "what fields each event MUST include (actor, timestamp, target, etc.)." The audit triggers are stated but no required fields are listed here. Add the required fields for OTP/KMS events: actor (principal, as UUID hash), timestamp (ISO 8601), factor type (`OTP`), operation (generation / verification / KMS-error), outcome (success / failure), target resource (the protected operation).

2. **Broken base audit reference** — "In addition to the events in Unified §3.3" refers to a file that does not exist (`Base_MFA_Application_Standard.md`). Furthermore, the base standard (`Base_Standalone_Application_Standard.md`) has no Audit Contract section — it was identified as MISSING in that document's compliance review. The deferral has no valid foundation. Until the base standard's audit contract is written, state the full set of required audit fields here directly rather than relying on a base-standard deferral.

---

## Section 3.4 — Logging Contract

**Status:** SATISFIED

> **Reviewer note:** This is the logging content from document Section 3.3 "Audit & Logging Contract."

### What is present
- **Base Standard / Org Standard split** — Both present. ✅
- **MFA OTP/KMS-specific log fields** — `factorType` (`OTP` or `TOTP`) required in every log entry. ✅
- **Log levels** — INFO (OTP delivery success, TOTP KMS provisioning/verification), WARN (OTP expiry detected and auto-regen triggered), ERROR (KMS failure). ✅
- **Prohibited log content** — Plaintext OTP values and TOTP key material explicitly prohibited. ✅
- **Delegation to AppFW Logging Guide** — Standard fields delegated; cloud-specific fields stated as additions. ✅

### What is missing or needs enhancement
Nothing. Section 3.4 is fully compliant for an augmenting standard.

---

## Section 3.5 — Security Contract

**Status:** PARTIAL

> **Reviewer note:** Document Section 3.4 maps to Standard Template Section 3.5 (Security Contract).

### What is present
- **Base Standard / Org Standard split** — Both present. ✅
- **Hash before persist (Base)** — "OTPs are bcrypt-hashed before storage. Raw values are never written to the database." ✅
- **KMS-backed TOTP secrets (Base)** — Encrypted on provisioning, decrypted only at verification, cleartext never stored. ✅
- **KMS key rotation (Org)** — Yearly rotation; done automatically by KMS. ✅

### What is missing or needs enhancement
1. **OTP single-use and TTL enforcement not in security contract** — The Standard Template requires "TTL or expiry logic documented" in the security contract. The OTP factor has two security-critical properties that belong here but are only described in Section 4.1 (architectural design): (a) OTPs MUST be single-use — cleared (`otp` and `otpTtl` set to null) immediately after successful verification; (b) Expired OTPs MUST be rejected regardless of hash match — the TTL check is an enforced security constraint. Add both as Enforced Constraints in the Base Standard block.

---

## Section 4 — Architectural Design Addendum

**Status:** PARTIAL

### What is present
- **OTP provider verification sequence (4.1)** — Seven steps: load record, check expiry, auto-regenerate (side effect), reject if absent, reject if expired, verify hash, clear on success. ✅
- **OTP generation sequence (4.1)** — Five steps: generate random OTP via `SecureRandom`, bcrypt-hash, compute expiry, persist, deliver via MCNS. ✅ (note: the previously flagged inaccurate formula has been removed)
- **Request-Scoped Context (4.1)** — Table documenting the OTP context method and header. ✅
- **Provider Summary table (4.1)** — Factor, storage table, and storage fields. ✅
- **Entity Schema (4.2)** — Two additional columns (`otp`, `otpTtl`) with type, constraint, description, and `Enforced Constraint` label. Null-after-success documented. ✅
- **Synchronous vs Async (4.3)** — MCNS delivery synchronous in HTTP request thread; OTP record persisted before delivery; no automatic retry. `Enforced Constraint` and `Design Choice` labels applied. ✅

### What is missing or needs enhancement
1. **Deprecated library class names throughout Section 4.1** — `OTPAuthenticationProvider`, `MultiFactorAuthenticationProvider`, `PINAuthenticationProvider`, `TOTPAuthenticationProvider`, `OTPRequestContext`, `OTPUserDetails`, and `OTP_USER_DETAILS` (table name) are deprecated library internal names and are flagged as Violation 1. The architectural pattern and verification sequence should be retained — only the class/table name references must be replaced with behavioral descriptions.

---

## Section 5 — Test & Validation Standard

**Status:** PARTIAL

> **Reviewer note:** Test content is in document Section 6.

### What is present
- **Unit tests (6.1)** — Five scenarios: OTP verify success + deletion (null check), OTP verify expired (exception + auto-regen), OTP missing header (exception + auto-regen), TOTP provisioning with KMS (encrypt call, PNG, KMS exception), KMS failure (encrypt and decrypt). ✅
- **Integration tests (6.2)** — Three scenarios: full OTP request flow, database state verification post-success, simulated KMS with deterministic test doubles. ✅
- **Test data guidance (6.3)** — KMS mock/stub guidance for CI. ✅
- **Integrator test gaps (6.4)** — MCNS end-to-end delivery, audit event emission (with required fields listed). ✅
- **Integrator responsibility** — Delegated test gaps clearly attributed. ✅

### What is missing or needs enhancement
1. **Test data guidance is thin** — Section 6.3 has one bullet covering only the KMS mock approach. Add OTP-specific guidance: use a fixed `SecureRandom` stub to produce deterministic OTP values for unit tests; use fixed timestamps to test TTL boundary conditions (OTP exactly at expiry, one second before, one second after); never use real user identifiers in test fixtures.

2. **Integration tests do not cover failure paths** — The integration test list covers only the success path and the database post-condition. Missing scenarios: OTP verification on an expired record (confirms auto-regeneration occurs and exception is thrown); OTP header absent (confirms auto-regeneration + missing-code exception); KMS failure in TOTP verification (confirms 500 and no partial state). The auto-regeneration side effect and clear-on-success behavior must be verified at the integration level.

---

## Section 6 — Operational Runbook Addendum

**Status:** PARTIAL

> **Reviewer note:** Operational content is in document Section 7.

### What is present
- **Health indicators (7.1)** — `kms` (key reachability via DescribeKey) and `mcns` (connectivity). Failure consequence stated for each. ✅
- **OTP configuration (7.2)** — `digits` and `ttl` with defaults and descriptions. ✅
- **KMS / AWS configuration (7.2)** — `region` and `keyArn` documented. ✅
- **Error mode classification (7.3)** — Three failure sources: KMS transient (potentially self-healing), KMS permanent (manual), MCNS unavailable (manual). Self-healing vs manual distinction made. ✅

### What is missing or needs enhancement
1. **No metrics** — The Standard Template requires "observable logs and metrics described." No metrics table is present. Add: OTP delivery success/failure rate (sustained failures → alert; indicates MCNS outage); KMS operation error rate (any → alert; indicates key policy problem or network issue); OTP auto-regeneration rate (unusual spike → alert; may indicate brute-force or clock misconfiguration).

2. **KMS key ARN supply mechanism not documented** — The `keyArn` property is listed in the configuration table but the supply mechanism (environment variable, AWS Parameter Store, Secrets Manager) is not specified. For a security-sensitive property referencing a KMS key, the supply mechanism must be explicit. Add a note specifying how `keyArn` is supplied at runtime and whether a key ARN change requires a restart.

---

## Section 7 — Appendix

**Status:** PARTIAL

> **Reviewer note:** Appendix is in document Section 10.

### What is present
- **Glossary** — Four terms: OTP, OTP TTL, MCNS, Auto-Regeneration. Behaviorally defined. ✅
- **Changelog** — One entry: v1.0, 2026-04-13. ✅
- **Standards Referenced** — "All standards from the Unified MFA Application Standard apply." No new standards introduced. ✅

### What is missing or needs enhancement
1. **AWS KMS absent from Glossary** — AWS KMS is referenced throughout but not defined. Add: "**AWS KMS** — AWS Key Management Service; a managed cloud service providing cryptographic encrypt and decrypt operations keyed by an ARN-referenced key. TOTP secrets are encrypted and decrypted exclusively via KMS in the cloud deployment." Also add to Section 1 Definitions (see Section 1 finding above).

---

## Action List

### Must Fix (rule VIOLATIONS)

1. **Fix Violation 1 — Replace deprecated library class names throughout Section 4.1 and Section 4.2** — Replace `OTPAuthenticationProvider`, `MultiFactorAuthenticationProvider`, `PINAuthenticationProvider`, `TOTPAuthenticationProvider`, `OTPRequestContext`, `OTPUserDetails`, and the `OTP_USER_DETAILS` table name reference with behavioral descriptions. Retain the verification sequence and architectural pattern in full — only remove the proprietary class and table name prescriptions. See Violation 1 for replacement language.

### Should Fix (PARTIAL sections)

2. **Fix broken cross-reference to base standard** — Update the "Extends" link and all "Unified §..." references to point to the actual file: `Base_Standalone_Application_Standard.md`. This affects: the document header, Section 3.3 Org Standard ("in addition to the events in Unified §3.3"), Section 4.1 ("from the Unified standard"), Section 4.2 ("Unified §4.2"), and Section 10 ("All standards from the Unified MFA Application Standard apply").

3. **Add required audit event fields to Section 3.3** — The audit triggers are defined but the required fields per event are not. Add: actor (principal as UUID hash), timestamp (ISO 8601), factor type (`OTP`), operation (generation / verification / KMS-error), outcome (success / failure), target resource. Do not rely on the base standard deferral — the base standard's audit contract is currently MISSING.

4. **Add OTP single-use and TTL security constraints to Section 3.5** — Add two Enforced Constraints to the Base Standard block: (a) OTPs MUST be single-use — `otp` and `otpTtl` MUST be set to null immediately after successful verification; (b) OTP TTL MUST be validated before hash comparison — an expired OTP MUST be rejected regardless of hash match.

5. **Add TOTP verification with KMS decryption to Section 2 happy path sequence diagram** — Add a TOTP Verification sequence showing: load TOTP record from DB → call KMS to decrypt → KMS returns plaintext → compute TOTP windows → verification success. This mirrors the KMS encrypt step shown for TOTP Provisioning.

6. **Expand Section 6.3 test data guidance** — Add: fixed `SecureRandom` stub for deterministic OTP values; fixed timestamps for TTL boundary testing; no real user identifiers in fixtures.

7. **Add failure-path integration tests to Section 6.2** — Add: OTP verification on expired record (auto-regeneration + exception); OTP header absent (auto-regeneration + missing-code exception); KMS failure in TOTP verification (500, no partial state).

8. **Add metrics table to Section 7** — OTP delivery success/failure rate, KMS error rate, OTP auto-regeneration rate.

9. **Document KMS key ARN supply mechanism in Section 7.2** — Specify how `keyArn` is supplied at runtime and whether a change requires a restart.

### Consider (minor gaps or enhancements)

10. **Add AWS KMS definition to Section 1 and Glossary** — Define behaviorally: a managed key service providing encrypt/decrypt via ARN-referenced key.

11. **Add Auto-Regeneration definition to Section 1** — Currently only in Glossary; should also be in Section 1 Definitions alongside OTP and OTP TTL given its operational significance.

12. **Add v1.1 changelog entry** — The document has been updated (formula removed, cross-reference revised) but the changelog still shows only v1.0. Add a dated entry for the changes made.

13. **Clarify `NEG-REQ-05` in Section 8** — "OTP record lookup without null/absent guard" is vague. Clarify: "If the OTP user record is absent, a naive lookup will throw an unhandled null pointer exception. The lookup MUST produce a typed missing-code exception on absent record."
