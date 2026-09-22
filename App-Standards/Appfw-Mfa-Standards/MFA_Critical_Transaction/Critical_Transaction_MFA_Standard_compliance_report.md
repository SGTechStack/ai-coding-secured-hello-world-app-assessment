# App Standards Compliance Report

**Document reviewed:** Appfw-Mfa-Standards/MFA_Critical_Transaction/Critical_Transaction_MFA_Standard.md

**Reviewed against:** .claude/skills/app-standards-review/references/Standard Template.md

**Date:** 2026-04-13

**Document type:** App Standard (Addendum — extends Unified MFA Application Standard)

---

## Overall Compliance Summary

| Section | Status | Issues |
|---|---|---|
| Do / Do Not Rules | VIOLATIONS FOUND | 1 violation |
| 1. Overview | PARTIAL | 1 |
| 2. Standard Flow | PARTIAL | 2 |
| 3.1 Inputs / Outputs | PARTIAL | 1 |
| 3.2 Error Contract | SATISFIED | 0 |
| 3.3 Audit Contract | PARTIAL | 1 |
| 3.4 Logging Contract | MISSING | 1 |
| 3.5 Security Contract | PARTIAL | 1 |
| 4. Architectural Design | PARTIAL | 2 |
| 5. Test & Validation Standard | PARTIAL | 2 |
| 6. Operational Runbook | MISSING | 1 |
| 7. Appendix | PARTIAL | 2 |

**Total issues: 14 (Violations: 1 | Partial: 8 | Missing: 2)**

---

## Do / Do Not Rules

The document is saved as a `.md` file — output format check satisfied.

### VIOLATION 1 — Flow diagrams absent from Section 2

**Rule broken:** "Include flow diagrams covering both the happy path and all failure paths using either Mermaid or PlantUML."

**Location in document:** Section 2 Standard Flow Addendum and Section 4.1 Interceptor Execution Model

**What was found:** Section 4.1 contains a text-based ASCII/Unicode execution order diagram rendered inside a plain code block. This is not a Mermaid or PlantUML diagram. No sequence diagram for the happy path and no flowchart for the failure paths exist in any valid format.

**What must change:** Replace or supplement the plain-text code block in Section 4.1 with a Mermaid sequence diagram covering the full MFA enforcement happy path (pre-hook → role check → factor verification → post-hook → log → proceed), and add a flowchart for the failure paths (insufficient privilege, missing factor, invalid factor).

---

## Section 1 — Overview

**Status:** PARTIAL

### What is present
- **Purpose:** Clearly stated — AOP-based enforcement of PIN/OTP/TOTP verification on critical transaction operations, scoped to `ROLE_CRITICAL_TRANSACTION`.
- **Scope:** Present — Spring Boot services using Spring AOP and Spring Security, requiring a pre-authenticated principal.
- **Definitions:** 2 definitions present (MFA Enforcer, Setup Prompt), plus 2 System Constraints (CON-CT-01, CON-CT-02).

### What is missing or needs enhancement
- **Definitions count is below the required 5–7.** Only MFA Enforcer and Setup Prompt are defined. Additional behavioral concepts introduced by this standard that should be defined include: Role Authorizer (the component performing privilege checks), MFA Handler (the pre/post lifecycle hook), MFA Type Container (the factor provider registry), and Critical Transaction Role (the role constant itself). Add at least 3 more definitions to meet the minimum.

---

## Section 2 — Standard Flow

**Status:** PARTIAL

### What is present
- **Happy path (2.4):** Full 8-step enforcement sequence documented — pre-hook, role check, provider lookup, factor verification, post-hook, critical transaction log, method execution.
- **Failure path (2.5):** Insufficient-privilege failure path documented.
- **Decision logic (2.6):** `ROLE_CRITICAL_TRANSACTION` always-required rule stated and its evaluation order specified.

### What is missing or needs enhancement
- **Flow diagrams are absent** (see Violation 1). A Mermaid/PlantUML sequence diagram and failure path flowchart are required.
- **Setup prompt failure path is not documented.** The standard defines a Setup Prompt concept (Section 1 Definitions) indicating when a user must complete enrollment before accessing protected operations. The flow for what happens when the setup prompt is active — does the enforcement chain abort? is the user redirected? — is not described as a failure path in Section 2.

---

## Section 3.1 — Inputs / Outputs

**Status:** PARTIAL

### What is present
- Authentication prerequisite stated: fully authenticated principal with granted authorities must be present in the security context.

### What is missing or needs enhancement
- **The section is too thin for the operations introduced.** The critical transaction layer adds inputs (the `mfaType` annotation attribute, the `privileges` annotation attribute) and observable outputs (the critical transaction log line, method execution or exception thrown). These should be explicitly listed as inputs and outputs — what the enforcement intercept reads and what it produces — rather than a single prerequisite sentence.

---

## Section 3.2 — Error Contract

**Status:** SATISFIED

### What is present
- Insufficient privilege → insufficient-privilege exception → mapped to `403 Forbidden`.
- Retryability is implicit (rejection, no user-level retry) and stated in the flow description.

### What is missing or needs enhancement
Nothing. For the new failure category introduced by this standard, the contract is complete.

---

## Section 3.3 — Audit Contract

**Status:** PARTIAL

### What is present
- Trigger defined: the interceptor unconditionally logs `INFO "Critical Transaction - {method} [{username}] at {timestamp}"` after successful verification.
- Org Standard label is present.

### What is missing or needs enhancement
- **Required audit event fields are not separated from the log line.** The Standard Template requires audit events to define actor, timestamp, target resource, and outcome. The document conflates the structured audit event with the plain log line. Clarify whether the `log.info` line constitutes the audit event, or whether a separate audit event must also be emitted. If it is the audit event, document the complete required field set explicitly.

---

## Section 3.4 — Logging Contract

**Status:** MISSING

### What is present
Nothing. No Logging Contract addendum is present. The critical transaction log line is documented in Section 3.3 (Audit Contract) but structured log fields, log levels for failure paths, and prohibited content are not addressed for this layer.

### What is missing or needs enhancement
Add a Section 3.4 Logging Contract Addendum covering:
- Structured log fields emitted by the AOP aspect: method name, username, timestamp, factor type, outcome.
- Log level on enforcement failure: WARN on insufficient privilege; ERROR on factor provider not found in registry.
- Prohibited log content: factor values (PIN, TOTP, OTP) must not appear in any log line emitted by the aspect.

---

## Section 3.5 — Security Contract

**Status:** PARTIAL

### What is present
- Rate limiting requirement: integrators must implement rate limiting at the API gateway or application layer.
- Section 3.5 Rate Limiting & Backoff Strategy (Org Standard): detailed — per-user failure threshold (10 in 1-hour window), exponential backoff, global IP throttling, 429 response with `Retry-After`, alerting on threshold breach.

### What is missing or needs enhancement
- **The security contract for the enforcement layer itself is not addressed.** Beyond rate limiting (which is an integrator responsibility), the document does not document the security properties enforced by the AOP aspect itself: that role checking always precedes factor verification, that the enforcement chain cannot be bypassed by exception in the pre-hook, and that the critical transaction log is unconditional and cannot be suppressed. These are security-relevant enforced constraints that should appear in the Security Contract.

---

## Section 4 — Architectural Design

**Status:** PARTIAL

### What is present
- **Interceptor Execution Model (4.1):** Fixed execution order documented — pre-hook, role check, provider lookup, factor verification, post-hook, log, proceed. Each step labeled.
- **Design Choice:** MFA Handler as abstract type with no-op default is labeled as a Design Choice.

### What is missing or needs enhancement
- **Only one architectural group is covered.** The standard introduces several other observable design decisions that are not addressed: the MFA Type Container registry (how providers are registered and looked up), the relationship between the aspect and the Spring Security context (External Assumption: principal must be pre-established), and the fact that the aspect is synchronous and executes in the HTTP request thread (Synchronous vs Async). These should be added as labeled observations.
- **The plain-text code block diagram in 4.1 must be replaced with a Mermaid/PlantUML diagram** (see Violation 1). It is currently unlabeled as either Enforced Constraint or Design Choice — the fixed order is an Enforced Constraint and should be labeled as such.

---

## Section 5 — Test & Validation Standard

**Status:** PARTIAL

### What is present
- **Unit tests (6.1):** Role authorization tests (Critical Transaction Role always required, rejection on missing role, success on all roles present) and interceptor order tests listed.
- **Integration tests (6.2):** Role privilege blocking, setup-required endpoint behavior, and rate limiting threshold tests listed.

### What is missing or needs enhancement
- **Test data guidelines are absent.** The Standard Template requires test data guidance. For this standard that means: how to construct a mock security context with/without `ROLE_CRITICAL_TRANSACTION`, how to stub the MFA Type Container, and how to simulate hook behavior in unit tests. None of this is present.
- **Integrator gaps are not explicitly identified.** Tests for audit emission, end-to-end rate limiting enforcement at the gateway, and MFA Handler custom implementation are the integrator's responsibility. These should be listed explicitly as gaps.

---

## Section 6 — Operational Runbook

**Status:** MISSING

### What is present
Nothing. The document does not include an Operational Runbook addendum.

### What is missing or needs enhancement
Add a Section 6 Operational Runbook Addendum covering:
- **Observable logs:** The critical transaction log line format (`INFO "Critical Transaction - {method} [{username}] at {timestamp}"`) and how operators can query it to audit critical operation access.
- **Manual vs self-healing error modes:** If the MFA Type Container has no provider registered for a given `mfaType`, the aspect throws `NullPointerException` — this is a configuration error requiring manual intervention (not self-healing). State this explicitly.
- **Config required at runtime:** The `@MultiFactorAuthentication` annotation values (`mfaType`, `privileges`) must be valid at startup. Document any startup validation mechanism or the risk if none exists.

---

## Section 7 — Appendix

**Status:** PARTIAL

### What is present
- **Glossary:** One term defined — Setup Prompt (with a complete behavioral definition).

### What is missing or needs enhancement
- **Changelog is absent.** No version or date for this addendum standard is recorded. Add a changelog entry with the date and version.
- **Standards Referenced is absent.** If no additional standards beyond the base standard apply, state "No additional standards referenced; see Unified MFA Application Standard." If OWASP or NIST references apply to the rate limiting requirements, cite them here.

---

## Action List

### Must Fix (MISSING sections or rule VIOLATIONS)

1. **Add flow diagrams to Section 2** — Replace the plain-text code block in Section 4.1 with a Mermaid/PlantUML sequence diagram for the enforcement happy path and add a flowchart for failure paths (insufficient privilege, missing factor header, invalid factor). Both are mandatory.
2. **Add Section 3.4 Logging Contract Addendum** — Document structured log fields emitted by the AOP aspect, log levels for failure paths, and prohibited log content (factor values must not be logged).
3. **Add Section 6 Operational Runbook Addendum** — Document the critical transaction log line format for operators, identify configuration error failure mode (unregistered `mfaType`) as requiring manual intervention, and list runtime config requirements.

### Should Fix (PARTIAL sections)

4. **Section 1 — Expand definitions to 5–7** — Add definitions for Role Authorizer, MFA Handler, MFA Type Container, and Critical Transaction Role to meet the minimum definition count.
5. **Section 2 — Document setup prompt failure path** — Describe what happens during enforcement when the user has not yet completed MFA enrollment (setup prompt active): does the enforcement chain abort? What exception is thrown?
6. **Section 3.1 — Expand inputs and outputs** — List the `mfaType` and `privileges` annotation attributes as inputs; list method execution and the critical transaction log line as outputs of the enforcement chain.
7. **Section 3.3 — Clarify audit event vs log line** — Separate the structured audit event fields (actor, timestamp, method, outcome) from the plain log line. If they are the same, say so explicitly and list all required fields.
8. **Section 3.5 — Add security contract for the enforcement layer** — Document that role checking always precedes factor verification, that the enforcement chain cannot be partially bypassed, and that the critical transaction log is unconditional.
9. **Section 4 — Add MFA Type Container and External Assumptions** — Document the provider registry lookup as a Design Choice and the requirement for a pre-established principal as an External Assumption with the Enforced Constraint label.
10. **Section 5 — Add test data guidelines and integrator gaps** — Document how to stub the security context and provider registry in unit tests; explicitly list audit, gateway rate-limiting, and MFA Handler tests as integrator gaps.
11. **Section 7 — Add changelog and standards referenced** — Record the version and date of this addendum; add standards referenced for rate limiting guidance (e.g., OWASP Testing Guide for brute-force) if applicable.

### Consider (minor gaps or enhancements)

12. **Section numbering consistency** — The document skips section 3.6 (jumps from 3.5 to 3.7) and uses section 5 for Exception Handling instead of Test & Validation (misaligned with the Standard Template structure). Aligning numbering to the template would reduce reader confusion.
