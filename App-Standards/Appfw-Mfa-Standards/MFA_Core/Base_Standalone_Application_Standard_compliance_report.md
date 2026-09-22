# App Standards Compliance Report

**Document reviewed:** Appfw-Mfa-Standards/MFA_Core/Base_Standalone_Application_Standard.md

**Reviewed against:** .claude/skills/app-standards-review/references/Standard Template.md

**Date:** 2026-04-15

**Document type:** App Standard (Base / Standalone — third review cycle)

---

> **Third review note:** This is the third review cycle. Significant improvements from the previous review:
> - Violation 2 (absence language in Section 7.1 and NEG-REQ-06) — **RESOLVED** ✅
> - Violation 3 (Argon2 optional algorithm reference) — **RESOLVED** ✅
> - Section 3.1 Org Standard split — **ADDED** ✅
> - Section 3.2 Org Standard split — **ADDED** (with `412` for missing/invalid factor clarification) ✅
> - Section 3.3 Audit triggers — **ADDED** to Org Standard ✅
>
> New findings and regressions introduced in this update:
> - `TotpUtilities` class name added to Section 4.1 — extends Violation 1 (was already open)
> - **REGRESSION**: Actor UUID-hash requirement removed from Section 3.4 Logging Org Standard — was present in the previous version, now absent
> - Changelog not updated — no entry for this revision cycle

---

## Overall Compliance Summary

| Section | Status | Issues |
|---|---|---|
| Do / Do Not Rules | VIOLATIONS FOUND | 1 violation |
| 1. Overview | SATISFIED | 0 |
| 2. Standard Flow | SATISFIED | 0 |
| 3.1 Inputs / Outputs | SATISFIED | 0 |
| 3.2 Error Contract | SATISFIED | 0 |
| 3.3 Audit Contract | PARTIAL | 1 |
| 3.4 Logging Contract | PARTIAL | 2 |
| 3.5 Security Contract | SATISFIED | 0 |
| 4. Architectural Design | PARTIAL | 1 |
| 5. Test & Validation Standard | SATISFIED | 0 |
| 6. Operational Runbook | PARTIAL | 2 |
| 7. Appendix | PARTIAL | 2 |

**Total issues: 9 (Violations: 1 | Partial: 5 | Missing: 0)**

---

## Do / Do Not Rules

### VIOLATION 1 — Deprecated library class names throughout Section 4.1 (expanded in this revision)

**Rule broken:** "Do not describe the internals, classes, or file layout of the deprecated library being replaced."

**Location in document:** Section 4.1 Factor Provider Model — Provider Class Hierarchy, TOTP Utilities, Request-Scoped Context, Provider Summary table

**What was found (this revision adds `TotpUtilities` to the previously identified class names):**
- `MultiFactorAuthenticationProvider` — named as the abstract base class for all factor providers; its `authenticate()` method signature and shared dependencies (`PasswordEncoder`, `MFARequestContext`) are documented
- `PINAuthenticationProvider` — named concrete provider with its verification logic documented step-by-step
- `TOTPAuthenticationProvider` — named concrete provider with its verification logic; references `TotpUtilities` by name
- `TotpUtilities` — **new in this revision**: a named utility class described with three static method signatures (`generateSecretKey()`, `generateTotpFromSecretKey(byte[] secret, int period, int digits)`, `generateQRCode(...)`) and design constraints
- `MFARequestContext` — base context class, referenced with its `getUsername()` method
- `PINRequestContext`, `OTPRequestContext` — context subtypes named in the table with their methods and header bindings

All of these are internal class names from the deprecated MFA dependency library. The `TotpUtilities` class and its method signatures are particularly detailed — the document prescribes a specific static API that future implementations are not obligated to follow.

**What must change:**
- `MultiFactorAuthenticationProvider` → "the abstract base provider" or "the base factor provider pattern"
- `PINAuthenticationProvider`, `TOTPAuthenticationProvider` → "the PIN provider", "the TOTP provider"
- `TotpUtilities` → "a stateless TOTP utility component" — retain the behavioral description of its three responsibilities (secret generation, TOTP computation per RFC 6238, QR code generation) but remove the class name and static method signatures
- `MFARequestContext`, `PINRequestContext`, `OTPRequestContext` → describe the context pattern behaviorally: "a request-scoped context bean that exposes the principal's username and factor-specific header values"

---

## Section 1 — Overview

**Status:** SATISFIED

### What is present
- **Purpose paragraph** — "To define a secure, consistent standard for implementing the PIN and TOTP authentication factors." ✅
- **Scope** — "Server-side services that implement PIN or TOTP factor verification; that can read HTTP request headers; and that persist per-user factor state in a relational store." ✅
- **Definitions** — Five behavioral concepts: Principal, Challenge Header, Factor Provider, PIN, TOTP. All behavioral; none are library class names. ✅

### What is missing or needs enhancement
Nothing. Section 1 is fully compliant.

---

## Section 2 — Standard Flow

**Status:** SATISFIED

### What is present
- **Happy path — PIN Setup, TOTP Provisioning, Factor Verification** — numbered steps present for each. ✅
- **Failure paths** — Section 2.4 lists all failure cases: missing header, invalid PIN, PIN not configured, missing TOTP secret/header, invalid TOTP, encryption/decryption failure, PIN setup rejected, PIN removal. ✅
- **Decision logic** — Section 2.5 Enforced Decision Logic: RFC 6238 MUST requirements, clock-skew tolerance (±1 window). ✅
- **Happy path sequence diagram** — Section 2.6: Mermaid `sequenceDiagram` covering all four happy path operations. ✅
- **Failure path flowchart** — Section 2.6: Mermaid `flowchart` covering all failure branches. ✅

### What is missing or needs enhancement
Nothing. Section 2 is fully compliant.

---

## Section 3.1 — Inputs / Outputs

**Status:** SATISFIED

### What is present
- **Base Standard** — Inputs/outputs table covering all four operations (PIN Setup, TOTP Provisioning, Factor Verification, PIN Removal). Input, HTTP response, and database side effect documented for each. ✅
- **Org Standard** — Added in this revision: TOTP re-provisioning rule (new secret overwrites previous; old secret immediately invalidated); PIN self-service guard (blocked only when PIN is already set). ✅

### What is missing or needs enhancement
Nothing. Section 3.1 is fully compliant.

---

## Section 3.2 — Error Contract

**Status:** SATISFIED

### What is present
- **Base Standard** — Four failure categories with HTTP status codes, error types, and retryability. ✅
- **Org Standard** — Added in this revision: error responses must not expose internals; structured error object conforming to org API error schema; `412` used for missing-header and invalid-factor errors; `422` reserved for business constraint violations. ✅

### What is missing or needs enhancement
Nothing. Section 3.2 is fully compliant.

---

## Section 3.3 — Audit Contract

**Status:** PARTIAL

> **Reviewer note:** Document Section 3.3 is titled "Audit & Logging Contract" and combines audit and logging content. Standard Template 3.3 (Audit) is evaluated here; Standard Template 3.4 (Logging) is in the next section.

### What is present
- **Audit triggers** — Org Standard now includes: "Emit an audit event on: successful factor verification, invalid factor attempt, PIN setup, and TOTP provisioning." ✅ (added in this revision)

### What is missing or needs enhancement
1. **Required audit event fields not defined** — The Standard Template requires "what fields each event MUST include (actor, timestamp, target, etc.)." The triggers are present but the required fields are fully deferred: "For the full list of required audit fields, refer to the Appfw Logging Guide." This deferral does not satisfy the requirement — the required fields must be stated inline, at minimum for the MFA-specific fields. Add: actor (principal, as UUID hash — see Section 3.4 issue below), timestamp (ISO 8601), factor type (`PIN` / `TOTP`), operation (verification / setup / provisioning), outcome (success / failure), target resource (the protected operation). Standard fields may then be delegated to the AppFW Logging Guide.

---

## Section 3.4 — Logging Contract

**Status:** PARTIAL

> **Reviewer note:** This is the logging content from document Section 3.3 "Audit & Logging Contract."

### What is present
- **Base Standard / Org Standard split** — Both present. ✅
- **MFA-specific log field** — `factorType` required in every log entry. ✅
- **Log levels** — INFO (success), WARN (invalid attempt), ERROR (encryption/persistence failure). ✅
- **Prohibited log content** — Raw PIN values, plaintext TOTP secrets, decrypted key material, encryption keys. ✅
- **Delegation to AppFW Logging Guide** — Standard fields delegated. ✅

### What is missing or needs enhancement
1. **Standard log fields not listed inline** — The Standard Template requires "structured log fields listed (correlation ID, principal ID, source IP, etc.)." Only `factorType` is listed; all standard fields are deferred to the AppFW Logging Guide without enumeration. At minimum, any standard field with MFA-specific behavior should be stated here. This connects to item 2 below.

2. **REGRESSION — Actor UUID-hash requirement removed** — The previous version of this document included in the Logging Org Standard: "Actor identity must be logged as a UUID hash (MD5 of UTF-8-encoded user string → `UUID.nameUUIDFromBytes`). The hash must appear in every log entry where an actor is identifiable." This requirement is absent from the current revision. This is a meaningful regression — the UUID-hash requirement is an org-specific logging contract that constrains how the principal identifier appears in logs and audit events, and it is not covered by the AppFW Logging Guide delegation. Restore it to the Org Standard block.

---

## Section 3.5 — Security Contract

**Status:** SATISFIED

> **Reviewer note:** Document Section 3.4 maps to Standard Template Section 3.5 (Security Contract). The Argon2 optional reference from Section 3.5 (Framework) has been removed — Violation 3 resolved.

### What is present
- **Base Standard / Org Standard split** — Both present. ✅
- **Hash before persist** — bcrypt for PINs, no raw values stored. ✅
- **Encrypted TOTP secrets** — Encrypted on provisioning, decrypted only at verification, cleartext never stored. ✅
- **RFC 6238 conformance** — Full algorithm specification: HMAC-SHA1, counter computation, dynamic truncation, modulo, zero-pad. ✅
- **TOTP minimum digit length + NIST SP 800-63B** — 6-digit minimum tied to entropy requirement. ✅
- **Clock-skew tolerance** — `counter ± 1` accepted; wider windows prohibited. ✅
- **Constant-time comparison** — MUST requirement with timing-attack rationale. ✅
- **Org Standard** — Key rotation (yearly); PIN self-service guard. ✅

### What is missing or needs enhancement
Nothing. Section 3.5 is fully compliant.

---

## Section 4 — Architectural Design

**Status:** PARTIAL

### What is present
- **Factor Provider Model (4.1)** — Provider pattern described with behavioral verification sequences for PIN and TOTP providers. ✅
- **TOTP Utilities (4.1)** — Three responsibilities: secret generation, RFC 6238 computation, QR code generation. Design Choice and Design Constraint labels applied correctly. ✅ (content; class name is violation)
- **Request-Scoped Context (4.1)** — Context pattern described behaviorally; context hierarchy and header bindings tabulated. ✅ (content; class names are violation)
- **Provider Summary table** — Factor, provider, storage table, storage fields. ✅
- **Synchronous vs Async** — CON-01 Enforced Constraint: all verification synchronous. ✅
- **External Assumptions** — Database via JPA; bcrypt `PasswordEncoder` bean. ✅
- **Entity Schema (4.2)** — PIN and TOTP entity schemas with field-level labels. ✅
- **Enforced Constraint / Design Choice / Assumption labels** — Applied correctly throughout. ✅

### What is missing or needs enhancement
1. **Deprecated library class names in Section 4.1** — `MultiFactorAuthenticationProvider`, `PINAuthenticationProvider`, `TOTPAuthenticationProvider`, `TotpUtilities` (new in this revision), `MFARequestContext`, `PINRequestContext`, `OTPRequestContext` are deprecated library internal class names. See Violation 1. The architectural content is complete and well-described — only the class name prescriptions must be replaced with behavioral descriptions.

---

## Section 5 — Test & Validation Standard

**Status:** SATISFIED

### What is present
- **Unit tests** — Nine scenarios covering all PIN and TOTP validation paths. ✅
- **Integration tests** — Full request flow (PIN and TOTP) with deterministic encryption stubs. ✅
- **Test data guidelines** — Fixed-byte secrets, fixed timestamps, no PII, stub encryption for CI. ✅
- **Integrator test gaps** — HTTP exception mapping, audit event emission (with required fields listed), rate limiting. ✅
- **Integrator responsibility stated** — "The following test scenarios are the integrator's responsibility." ✅

### What is missing or needs enhancement
Nothing. Section 5 is fully compliant.

---

## Section 6 — Operational Runbook

**Status:** PARTIAL

### What is present
- **Health indicators (7.1)** — "The following are recommended at the application level:" with database connectivity check. Absence language from previous revision **removed** ✅
- **Default TOTP configuration (7.2)** — `issuer`, `period`, `digits` with defaults, types, descriptions. Startup failure on invalid digits noted. ✅
- **Error mode classification (7.3)** — Three failure sources (database unavailable, encryption key misconfigured, bcrypt encoder not configured) with self-healing vs manual distinction. ✅

### What is missing or needs enhancement
1. **No metrics table** — The Standard Template requires "observable logs and metrics described." No metrics are defined. Add at minimum: invalid MFA attempt rate (sustained spike → alert; may indicate brute-force attack or misconfiguration), encryption failure rate (any non-zero → alert; indicates key availability problem), factor verification success rate.

2. **Encryption key runtime configuration not documented** — The error mode table references "Verify the encryption key is present in the database and the application is configured with the correct key reference," but the configuration table (Section 7.2) only shows TOTP properties. The encryption key is the critical secret required for TOTP operations. The Section 7.2 configuration table should include: the encryption key reference (database-stored; sensitive), how the application is configured to locate it (property name or environment variable), and whether a change requires a restart.

---

## Section 7 — Appendix

**Status:** PARTIAL

### What is present
- **Glossary** — Nine terms defined: Principal, Challenge Header, Factor Provider, PIN, TOTP, bcrypt, Encryption Key, Clock-skew, HMAC-SHA1, Dynamic Truncation, otpauth URI. Behaviorally grounded. ✅
- **Changelog** — Entry for v1.0 (2026-03-12). ✅
- **Standards Referenced** — RFC 6238, RFC 4226, NIST SP 800-63B, Google Authenticator Key URI Format. ✅ (see note on RFC 4226)

### What is missing or needs enhancement
1. **RFC 4226 citation not tied to observable behavior** — RFC 4226 is cited as "Foundation for RFC 6238's HMAC + dynamic truncation." This is a background reference — the behavior it describes is fully subsumed by RFC 6238, which is already cited and tied to observable behavior. No RFC 4226-specific behavior is separately enforced. Either remove it or add a note explaining what observable behavior it adds beyond RFC 6238.

2. **Changelog not updated for this revision** — The document has been meaningfully updated since v1.0 (Org Standards added to Sections 3.1, 3.2, 3.3; Section 3.5 Framework Org Standard added; Section 7.1 phrasing updated; NEG-REQ-06 removed; `TotpUtilities` added to Section 4.1). No changelog entry exists for these changes. Add a v1.1 entry dated 2026-04-15 (or the correct update date) summarising the additions.

---

## Action List

### Must Fix (rule VIOLATIONS)

1. **Fix Violation 1 — Replace deprecated library class names throughout Section 4.1** — Replace `MultiFactorAuthenticationProvider`, `PINAuthenticationProvider`, `TOTPAuthenticationProvider`, `TotpUtilities` (and its static method signatures), `MFARequestContext`, `PINRequestContext`, `OTPRequestContext` with behavioral descriptions. Retain all behavioral content (verification sequences, context pattern, utility responsibilities, entity schema) — only remove the proprietary class name and API prescriptions.

### Should Fix (PARTIAL sections)

2. **Restore actor UUID-hash requirement to Section 3.4 Logging Org Standard** — The requirement "Actor identity must be logged as a UUID hash (MD5 of UTF-8-encoded user string → `UUID.nameUUIDFromBytes`). The hash must appear in every log entry where an actor is identifiable" was present in the previous version and has been removed in this revision. This is an org-specific logging contract that must be stated here — it is not covered by the AppFW Logging Guide delegation.

3. **Add required audit event fields to Section 3.3** — State inline: actor (principal as UUID hash), timestamp (ISO 8601), factor type (`PIN` / `TOTP`), operation (verification / setup / provisioning), outcome (success / failure), target resource. Standard fields may then be delegated to the AppFW Logging Guide.

4. **Add metrics table to Section 7** — Add: invalid MFA attempt rate (with alert guidance), encryption failure rate (any non-zero → alert), factor verification success rate.

5. **Add encryption key runtime configuration to Section 7.2** — Document the encryption key reference as a configuration entry: how the application locates it, whether it is sensitive, and whether a change requires a restart.

6. **Add v1.1 changelog entry to Section 7** — Document the changes made in this revision cycle (Org Standards added to 3.1, 3.2, 3.3; Section 3.5 Framework Org Standard added; Section 7.1 updated; NEG-REQ-06 removed; TotpUtilities section added).

### Consider (minor gaps or enhancements)

7. **Remove RFC 4226 from Standards Referenced** — RFC 6238 already covers all observable behavior. RFC 4226 is a background reference that adds no separately enforceable constraint.

8. **Add integration test scenarios for failure paths** — Section 6.2 covers only the success path. Consider adding: invalid PIN/TOTP verification (confirms correct exception type), missing header (confirms missing-code exception + HTTP code), TOTP provisioning followed by verification (confirms ciphertext round-trip).
