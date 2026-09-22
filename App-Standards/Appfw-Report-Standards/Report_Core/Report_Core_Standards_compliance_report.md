# App Standards Compliance Report

**Document reviewed:** Appfw-Report-Standards/Report_Core/Report_Core_Standards.md

**Reviewed against:** .claude/skills/app-standards-review/references/Standard Template.md

**Date:** 2026-04-14

**Document type:** App Standard (v1.2 — third review cycle)

---

> **Reviewer note:** This is the third review of this document. v1.1 addressed the major structural gaps (Sections 3.1 and 3.4 added, flow diagrams added, Glossary added). v1.2 addressed the remaining structural gaps: Section 2.1 correctly split into startup and per-request diagrams; Section 3.2 Base/Org split added; Section 3.3 inline event fields added; Section 3.4 log levels table added; Section 3.5 absence language reduced; Section 4.3 Runtime Context — Actor Identity added. What remains are two rule violations (deprecated exception and enum type names used throughout) and two minor partial issues (one phrasing issue in Section 3.5, one missing changelog entry in Section 7).

---

## Overall Compliance Summary

| Section | Status | Issues |
|---|---|---|
| Do / Do Not Rules | VIOLATIONS FOUND | 2 violations |
| 1. Overview | SATISFIED | 0 |
| 2. Standard Flow | SATISFIED | 0 |
| 3.1 Inputs / Outputs | SATISFIED | 0 |
| 3.2 Error Contract | SATISFIED | 0 |
| 3.3 Audit Contract | SATISFIED | 0 |
| 3.4 Logging Contract | SATISFIED | 0 |
| 3.5 Security Contract | PARTIAL | 1 |
| 4. Architectural Design | SATISFIED | 0 |
| 5. Test & Validation Standard | SATISFIED | 0 |
| 6. Operational Runbook | SATISFIED | 0 |
| 7. Appendix | PARTIAL | 1 |

**Total issues: 4 (Violations: 2 | Partial: 2 | Missing: 0)**

---

## Do / Do Not Rules

### VIOLATION 1 — Deprecated library enum type names in Section 3.1 inputs table

**Rule broken:** "Do not describe the internals, classes, or file layout of the deprecated library being replaced."

**Location in document:** Section 3.1, Base Standard, Required inputs table — Type column, rows 2 and 3

**What was found:**
- Row 2: `ReportSecurityClassification` listed as the type for "Security classification"
- Row 3: `ReportSensitivity` listed as the type for "Sensitivity"

These are enum types from the deprecated report generation dependency library. The permitted values are listed in the Org Standard subsection, confirming that these are Org-defined enumerations — but the type names themselves originate from the deprecated library, not from the new implementation.

**What must change:** Replace the type names with inline behavioral descriptions:
- `ReportSecurityClassification` → `String` with note: "One of the supported classification values listed in the Org Standard"
- `ReportSensitivity` → `String` with note: "One of the supported sensitivity values listed in the Org Standard"

Alternatively, if the new implementation defines its own enum types with new names, those names may be used — but `ReportSecurityClassification` and `ReportSensitivity` must not appear.

---

### VIOLATION 2 — Deprecated library exception class names used throughout

**Rule broken:** "Do not describe the internals, classes, or file layout of the deprecated library being replaced."

**Location in document:**
- Section 2.2 failure flowchart — three flowchart node labels: two `ReportValidationException` nodes (lines C and H) and one `InvalidFilePathException` node (line D)
- Section 3.2 Base Standard table — `ReportValidationException` in the Error Type column (row 1)
- Section 3.2 Org Standard table — `ReportValidationException` (rows 1 and 2) and `InvalidFilePathException` (row 3)
- Section 5.1 HTTP controller unit test list — `` `ReportValidationException` → `400 Bad Request` response ``

**What was found:** `ReportValidationException` and `InvalidFilePathException` appear as exception type names throughout the document. These are the deprecated library exception class names that have appeared in every review cycle of this document.

**What must change:** Replace with behavioral descriptions in every location:
- `ReportValidationException` → "a validation exception raised when required inputs are absent or invalid"
- `InvalidFilePathException` → "a path validation exception raised when the output path is null, contains a traversal sequence, or contains illegal characters"

In the flowchart node labels, shorten for legibility while preserving behavior: e.g., `Validation exception → 400 Bad Request` and `Path validation exception → 400 Bad Request`.

In the error tables, the Error Type column can use the behavioral description quoted above. In the unit test list, replace the class name with the behavioral exception category.

---

## Section 1 — Overview

**Status:** SATISFIED

### What is present
- **Purpose paragraph** — Clear and precisely scoped: "To define the minimum requirements for integrating report generation into an application service using JasperReports." ✅
- **Scope** — "Server-side Java applications that generate reports as file artifacts in PDF, XLSX, or CSV format." ✅
- **Definitions** — Five behavioral concepts defined: Report, Template, Datasource, Security Classification and Sensitivity, Export Format. All are described in terms of observable behavior, not class names. The five-definition minimum is met. ✅

### What is missing or needs enhancement
Nothing. Section 1 is fully compliant.

---

## Section 2 — Standard Flow

**Status:** SATISFIED

### What is present
- **Happy path — startup diagram** — Mermaid `sequenceDiagram` showing the one-time startup sequence: template validation, compilation, post-compilation validation, and cache. ✅
- **Happy path — per-request diagram** — Separate `sequenceDiagram` showing the per-call export sequence: pre-fill validation, directory creation, fill, write, audit log. ✅
- **Failure path diagram** — Mermaid `flowchart` covering all failure branches: missing inputs, invalid path, compilation failure, post-compilation validation failure, fill failure, I/O failure. ✅
- **Decision logic** — All branching described: pre-compilation validation split, valid/invalid path, JRException vs success at compilation, post-compilation null check, fill vs I/O error. ✅
- **Step ordering** — "Steps must not be reordered" explicitly stated. ✅

> Note: `ReportValidationException` and `InvalidFilePathException` appear as node labels in the failure flowchart. These are deprecated library class names and are flagged in Violation 2 above. The structural completeness of the diagrams is not affected.

### What is missing or needs enhancement
Nothing structural. Fix the deprecated class names per Violation 2.

---

## Section 3.1 — Inputs / Outputs

**Status:** SATISFIED

### What is present
- **Base Standard / Org Standard split** — Both subsections present and correctly scoped. ✅
- **Required inputs table** — Five required inputs listed with type and validation rules. ✅
- **Optional inputs table** — Image files listed with default and validation. ✅
- **Outputs and side effects** — Output artifact, caching side effect, and no-return-value contract all documented. ✅
- **Datasource handling table** — All three supported types described with behavioral notes. CSV compliance with RFC 4180 noted. ✅
- **Org Standard classifications and sensitivities** — Supported enum values listed in plain text. ✅
- **CSV-specific classification exemption** — "CSV exports must still validate their datasource and output path, but do not require a classification label." ✅

> Note: `ReportSecurityClassification` and `ReportSensitivity` appear as type names in the required inputs table. These are deprecated library enum type names and are flagged in Violation 1 above. The structural completeness of the section is not affected.

### What is missing or needs enhancement
Nothing structural. Fix the deprecated type names per Violation 1.

---

## Section 3.2 — Error Contract

**Status:** SATISFIED

### What is present
- **Base Standard / Org Standard split** — Both subsections present. ✅
- **Failure categories** — Named and described across both tables. ✅
- **Retryability** — Stated for all entries; includes nuanced guidance (e.g., "Investigate — check datasource" for rendering failure; exponential backoff for transient DB timeout). ✅
- **HTTP status codes** — Present for all entries. ✅
- **Integrator mapping responsibility** — "The calling layer (e.g., a REST controller) must map these to HTTP responses." ✅

> Note: `ReportValidationException` and `InvalidFilePathException` appear as Error Type values in both tables. These are deprecated library class names and are flagged in Violation 2 above. The structural completeness of the section is not affected.

### What is missing or needs enhancement
Nothing structural. Fix the deprecated class names per Violation 2.

---

## Section 3.3 — Audit Contract

**Status:** SATISFIED

### What is present
- **Audit event triggers** — Five triggers defined in a table: successful export, validation failure, compilation failure, rendering failure, non-fatal datasource issue. ✅
- **Required event fields** — "Each event must include: actor identity, report name, export format, outcome, and timestamp (ISO 8601)." Stated inline with the appropriate deferral to the AppFW Logging Guide for the full field list. ✅
- **Org Standard** — Actor identity hashing rule stated: "User identifier must be stored as a UUID hash (derived from the MD5 hash of the UTF-8-encoded user string). Raw user names, emails, or IDs must never appear in audit events." ✅
- **No-silent-failure enforcement** — "No export operation may complete or fail silently." ✅

### What is missing or needs enhancement
Nothing. Section 3.3 is fully compliant.

---

## Section 3.4 — Logging Contract

**Status:** SATISFIED

### What is present
- **Log levels table** — Five trigger conditions mapped to `INFO`, `WARN`, or `ERROR`. ✅
- **Report-specific log fields table** — Four fields defined: operation step (with permitted values listed), outcome (with permitted values listed), report name, export format. ✅
- **Prohibited log content** — "raw datasource content, PII, database credentials, connection strings, query results, raw user identifiers." ✅
- **Standard fields delegation** — AppFW Logging Guide referenced for correlation ID, principal ID, and other standard fields. ✅
- **Org Standard** — Actor identity hashing rule consistent with Section 3.3. ✅

### What is missing or needs enhancement
Nothing. Section 3.4 is fully compliant.

---

## Section 3.5 — Security Contract

**Status:** PARTIAL

### What is present
- **Path traversal prevention (OWASP A03:2021)** — Validation rules listed with specific character set constraint and URL-decode requirement. ✅
- **Injection prevention (OWASP A03:2021)** — `$P{}`, `$V{}`, `$F{}` rejection rule stated. ✅
- **Transport** — "Generated report files must not be served over unencrypted HTTP." ✅
- **Access control boundary** — "Authorization checks... are the responsibility of the integrating application." Consistent with OWASP A01:2021. ✅
- **Org Standard** — Classification and sensitivity guards, template deployment constraints, storage and retention rules. All described prescriptively. ✅
- **OWASP citations** — A03:2021 tied to path traversal and injection enforcement; A01:2021 tied to access control boundary statement. Both are behaviorally grounded. ✅

### What is missing or needs enhancement
- **Residual absence language** — "This integration enforces no access control of its own." The first sentence of the access control boundary block is prescriptive and correct: "Authorization checks — who may export which report — are the responsibility of the integrating application." The second sentence ("This integration enforces no access control of its own") describes what the integration does not do, which is the form of absence language the Standard Template prohibits. The second sentence is redundant — the first sentence already covers the scope boundary. Remove it.

---

## Section 4 — Architectural Design

**Status:** SATISFIED

### What is present
- **4.1 JRXML Template** — Two Enforced Constraints (template deployed with code; validated before compilation), two Design Choices (startup compile-and-cache; classification injection vs static text), one Assumption (JRException handling). All labeled correctly. ✅
- **4.2 Programmatic Design** — Design Choice label; scope-delegated to `Report_Programmatic_Standard.md`. ✅
- **4.3 Runtime Context — Actor Identity** — Two Enforced Constraints: actor identity from Spring Security context (not caller-supplied); raw identity must be hashed before use. Includes a concrete Java code snippet. ✅
- **4.4 Format Support** — Confirms the three supported formats share the same validation, audit, and path security requirements. ✅
- **Enforced Constraint / Design Choice / Assumption labels** — Correctly applied throughout. ✅

### What is missing or needs enhancement
Nothing. Section 4 is fully compliant.

---

## Section 5 — Test & Validation Standard

**Status:** SATISFIED

### What is present
- **Integrator responsibility** — "All tests listed below are the integrator's responsibility to author and maintain. No test coverage is provided by the report library itself." ✅
- **5.1 Required Unit Tests — JRXML** — Pre-fill validation failures (6 scenarios), post-compilation validation failures (2 scenarios), HTTP controller mapping (3 entries). All required branches covered. ✅
- **5.2 Required Integration Tests — JRXML** — Six scenarios: full PDF, full XLSX, full CSV, static text classification, multi-sheet XLSX (if supported), concurrent export. User UUID hash assertion specified for all applicable scenarios. ✅
- **5.3 Test Data Guidelines** — Dataset size, PII avoidance, synthetic user identifiers, image placeholder guidance, CSV edge cases, H2 database recommendation. ✅
- **Programmatic design cross-reference** — Unit tests for programmatic design delegated to `Report_Programmatic_Standard.md` Section 6. ✅

> Note: `` `ReportValidationException` `` appears in the Section 5.1 HTTP controller test list. This is a deprecated library class name and is flagged in Violation 2 above. The structural completeness of the section is not affected.

### What is missing or needs enhancement
Nothing structural. Fix the deprecated class name per Violation 2.

---

## Section 6 — Operational Runbook

**Status:** SATISFIED

### What is present
- **6.1 Runtime Configuration and Secrets** — Table of four configuration properties with sensitivity, supply mechanism, and description. YAML configuration snippet for output directory path. ✅
- **6.2 Health Indicators** — Three health checks: filesystem, database, templates. Each with failure consequence. ✅
- **6.3 Metrics** — Three metrics: export duration (p99), success rate, disk space. Each with alert threshold and action. ✅
- **6.4 Error Recovery** — Four error types with recovery action, self-healing vs operator-required distinction. ✅

### What is missing or needs enhancement
Nothing. Section 6 is fully compliant.

---

## Section 7 — Appendix

**Status:** PARTIAL

### What is present
- **Standards Referenced** — Five references: RFC 4180 (CSV), ISO/IEC 29500 (XLSX compatibility), JDBC 4.0+ (ResultSet), OWASP A03:2021 (injection + path traversal), OWASP A01:2021 (access control boundary). All tied to observable behavior or design decisions in the document. ✅
- **Glossary** — Six terms defined: Band, Classification label, Fill, Post-compilation validation, Pre-compilation validation, Template. All terms are behaviorally described. ✅
- **Changelog** — Two entries present (v1.0 and v1.1). ✅

### What is missing or needs enhancement
- **Missing v1.2 changelog entry** — The document has been updated since v1.1 (Section 2.1 split into two diagrams; Section 3.2 Base/Org split; Section 3.3 inline fields; Section 3.4 log levels table; Section 3.5 absence language reduction; Section 4.3 Runtime Context added). These changes constitute a version increment. Add a v1.2 changelog entry dated 2026-04-14 summarising these additions.

---

## Action List

### Must Fix (rule VIOLATIONS)

1. **Fix Violation 1 — Replace `ReportSecurityClassification` and `ReportSensitivity` type names in Section 3.1** — Replace with behavioral type descriptions (e.g., `String` with a note referencing the Org Standard permitted values). Apply to the Required inputs table, Type column, rows 2 and 3.

2. **Fix Violation 2 — Replace `ReportValidationException` and `InvalidFilePathException` class names throughout** — Apply to:
   - Section 2.2 flowchart node labels (three nodes)
   - Section 3.2 Base Standard table, Error Type column (row 1)
   - Section 3.2 Org Standard table, Error Type column (rows 1, 2, and 3)
   - Section 5.1 HTTP controller unit test list

   Use behavioral descriptions: "a validation exception raised when required inputs are absent or invalid" and "a path validation exception raised when the output path is null, contains a traversal sequence, or contains illegal characters." Shorten for flowchart node labels as needed.

### Should Fix (PARTIAL sections)

3. **Remove residual absence language from Section 3.5** — Remove the sentence "This integration enforces no access control of its own." The preceding sentence ("Authorization checks — who may export which report — are the responsibility of the integrating application.") already states the boundary prescriptively. The second sentence is redundant and describes absence.

4. **Add v1.2 changelog entry to Section 7** — Add a dated entry summarising the changes made in this revision cycle: Section 2.1 startup/per-request diagram split; Section 3.2 Base/Org split; Section 3.3 inline event fields; Section 3.4 log levels table; Section 3.5 absence language removal; Section 4.3 Runtime Context addition.

### Consider (minor enhancements)

5. **Clarify "usable page width" definition gap** — Section 3.1 does not define "usable page width" (referenced in `Report_Programmatic_Standard.md`), but this document is the base standard. Consider adding a brief note in Section 3.1 or the Glossary clarifying that page-width constraints are specific to the programmatic design approach and are defined in `Report_Programmatic_Standard.md`.

6. **Clarify ISO/IEC 29500 citation scope** — The Standards Referenced entry for ISO/IEC 29500 states "XLSX exports must be compatible with Microsoft Excel and standard spreadsheet tools." This is a design intent statement rather than an enforcement requirement. Either tie it to an observable constraint (e.g., "JasperReports' XLSX exporter produces OOXML-compliant output compatible with ISO/IEC 29500") or remove the reference and retain the compatibility requirement as a plain design statement.
