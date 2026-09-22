# App Standards Compliance Report

**Document reviewed:** Appfw-Report-Standards/Report_Programmatic/Report_Programmatic_Standard.md

**Reviewed against:** .claude/skills/app-standards-review/references/Standard Template.md

**Date:** 2026-04-14

**Document type:** App Standard (augmenting — extends `Report_Core_Standards.md`)

---

> **Reviewer note:** This document is explicitly designed as an augmenting standard. It states: "All base requirements (format support, security classification, output path validation, audit, error handling, and operational guidance) apply and are not repeated here." Where sections are absent, this report distinguishes between deliberate deferral to the core standard and gaps that should be addressed even in an augmenting document.

---

## Overall Compliance Summary

| Section | Status | Issues |
|---|---|---|
| Do / Do Not Rules | VIOLATIONS FOUND | 2 violations |
| 1. Overview | PARTIAL | 2 |
| 2. Standard Flow | PARTIAL | 1 |
| 3.1 Inputs / Outputs | PARTIAL | 1 |
| 3.2 Error Contract | PARTIAL | 1 |
| 3.3 Audit Contract | MISSING | 1 |
| 3.4 Logging Contract | MISSING | 1 |
| 3.5 Security Contract | PARTIAL | 1 |
| 4. Architectural Design | PARTIAL | 1 |
| 5. Test & Validation Standard | PARTIAL | 1 |
| 6. Operational Runbook | MISSING | 1 |
| 7. Appendix | PARTIAL | 1 |

**Total issues: 13 (Violations: 2 | Partial: 7 | Missing: 3)**

> The 3 MISSING sections (Audit Contract, Logging Contract, Operational Runbook) are deliberately deferred to the core standard, not oversights. Each should be resolved with a brief stub section or a clear deferral statement — not by duplicating the core content.

---

## Do / Do Not Rules

### VIOLATION 1 — Deprecated library exception class names in flow diagram and error contract
**Rule broken:** "Do not describe the internals, classes, or file layout of the deprecated library being replaced."
**Location in document:** Section 2.2 (failure flowchart node labels) and Section 3.2 (Error Contract table, Error Type column)
**What was found:** `ReportValidationException` and `InvalidFilePathException` appear as exception type names in the Mermaid failure flowchart and in the Error Contract table. These are the same deprecated library exception class names flagged in the `Report_Core_Standards.md` review (Violation 1 of that report). They originate from the deprecated report generation dependency library.
**What must change:** Replace with behavioral descriptions, consistent with the fix required in the core standard:
- `ReportValidationException` → "a validation exception raised before any file I/O when required inputs are absent or invalid"
- `InvalidFilePathException` → "a path validation exception raised when the output path is null, contains a traversal sequence, or contains illegal characters"

---

### VIOLATION 2 — Deprecated library component and type names used throughout
**Rule broken:** "Do not describe the internals, classes, or file layout of the deprecated library being replaced."
**Location in document:** Section 1 Definitions, Section 2.1 happy path diagram, Section 3.1 Inputs table, Section 6.1 unit test table
**What was found:**
- `ReportColumn` is defined in Section 1 as "The column metadata type that drives both `JasperDesign` field declarations and pre-fill validation." It carries the `Report` prefix consistent with the deprecated library's naming conventions (`ReportValidationException`, `ReportSecurityClassification`, etc.). It is used throughout as a type name in the inputs table (`List<ReportColumn>`) and unit tests.
- `JasperDesignBuilder` appears as a named participant in the Section 2.1 sequence diagram (`B as JasperDesignBuilder`). This is not a JasperReports API class — it is a builder utility whose name and role suggest it originates from the deprecated library.
**What must change:**
- Replace `ReportColumn` with a behavioral description of the column metadata contract: "a column descriptor carrying the column name (binding key for the datasource field), header label, value class, and rendered width." Refer to it as "column descriptor" or "column definition" in prose and table types.
- Replace `JasperDesignBuilder` in the sequence diagram with a behavioral participant label — e.g., "Report Design Builder" or incorporate its steps directly into the caller sequence.

---

## Section 1 — Overview

**Status:** PARTIAL

### What is present
- **Purpose paragraph** — Clear and well-scoped: "To define requirements specific to building a `JasperReport` entirely in code, without a `.jrxml` template file."
- **Scope** — Explicitly stated: "Integrations that choose the programmatic design approach described in `Report_Core_Standards.md` Section 4.2."
- **When to choose / when to prefer JRXML** — The decision guidance (last two blocks in Section 1) is a useful addition not present in the template requirement, and is grounded in observable constraints.

### What is missing or needs enhancement
1. **Definitions count below the 5–7 threshold** — Only 3 definitions are provided: `JasperDesign`, `JasperReport`, `ReportColumn`. The Standard Template requires 5–7 key behavioral concepts per document. Even as an augmenting standard, this document introduces several concepts that are not defined in the core: column descriptor (the column metadata contract), pre-compilation validation (distinct from the core standard's pre-fill validation), and the compile-and-cache lifecycle. Add at least 2 more definitions.
2. **Definitions use class names rather than behavioral concepts** — `JasperDesign` and `JasperReport` are JasperReports API class names (permitted as external third-party library). `ReportColumn` is a deprecated library class name (see Violation 2). Rewrite all three as behavioral concept definitions using plain language, citing the class name only parenthetically where it adds precision.

---

## Section 2 — Standard Flow

**Status:** PARTIAL

### What is present
- **Happy path diagram** — Section 2.1 provides a Mermaid `sequenceDiagram` covering the full programmatic export sequence: input validation, path validation, design construction, compilation, post-compilation validation, directory creation, fill, write, and audit log. ✅
- **Failure path diagram** — Section 2.2 provides a Mermaid flowchart covering all failure branches specific to programmatic design: pre-compilation column validation failures, path validation failure, compilation failure, post-compilation validation failure, and fill/export failure. ✅
- **Decision logic** — All programmatic-specific branching is described: column list checks, column field checks, width constraint, Map datasource key validation. ✅

### What is missing or needs enhancement
- **`JasperDesignBuilder` in happy path diagram is a potentially deprecated library component** — See Violation 2. The sequence diagram models `B as JasperDesignBuilder` as a named participant, and calls `B: build(name, classification, sensitivity, columns, title)`. If `JasperDesignBuilder` is from the deprecated library, its class name must be replaced with a behavioral participant label. This also affects the accuracy of the diagram as a prescription for future implementations — future implementations may not use a builder pattern at all.

---

## Section 3.1 — Inputs / Outputs

**Status:** PARTIAL

### What is present
- **Additional required inputs** — Full table listing column list validation rules (6 constraints), each column's field validations (name, header, valueClass, width), and the column width sum constraint. ✅
- **Optional inputs** — Title text listed with default and rendering note. ✅
- **Outputs and side effects** — Output artifact, caching behavior (fixed vs dynamic column sets), and explicit no-return-value contract. ✅
- **Datasource field matching** — Important programmatic-specific concern: Map key pre-validation vs ResultSet/CSV fill-time mismatch handling. ✅

### What is missing or needs enhancement
- **Base Standard / Org Standard split absent** — The Standard Template requires each contract subsection to be split into Base Standard and Org Standard. No such split is present. Base Standard would cover: format-agnostic column validation rules, datasource binding contract, output path as the contract boundary. Org Standard would cover: Org-specific column type constraints or naming conventions if any, and the `ReportColumn`-derived validation rules (once behaviorally described per Violation 2 fix).

---

## Section 3.2 — Error Contract

**Status:** PARTIAL

### What is present
- **Additional failure modes** — 6 programmatic-specific error categories listed with exception types, HTTP status codes, and retryability. ✅
- **Explicit extension of base contract** — The section opens with "Extends the base error contract in `Report_Core_Standards.md` Section 3.2." ✅
- **Retryability** — Present for all entries; all are terminal (No). ✅

### What is missing or needs enhancement
- **Base Standard / Org Standard split absent** — No split is present. As a minimum, an Org Standard block should list the behaviorally-described exception names (per Violation 1 fix) that are Org-specific.

---

## Section 3.3 — Audit Contract

**Status:** MISSING

### What is present
Nothing. No Audit Contract section exists. The document's opening states that base audit requirements are not repeated.

### What is missing or needs enhancement
There are no programmatic-design-specific audit triggers beyond those defined in the core standard. However, the section should be present to be explicit about this. Add a minimal stub:

```
### 3.3 Audit Contract

No additional audit requirements beyond those defined in `Report_Core_Standards.md` Section 3.3.
All audit triggers (successful export, validation failure, compilation failure, rendering failure) and
required event fields apply without modification to the programmatic design approach.
```

This prevents ambiguity and confirms the omission is deliberate, not an oversight.

---

## Section 3.4 — Logging Contract

**Status:** MISSING

### What is present
Nothing. No Logging Contract section exists. Base logging requirements are not repeated.

### What is missing or needs enhancement
There are no programmatic-design-specific logging fields or conventions beyond those defined in the core standard. Add a minimal stub analogous to the recommendation for 3.3:

```
### 3.4 Logging Contract

No additional logging requirements beyond those defined in `Report_Core_Standards.md` Section 3.4.
The operation step field should include `PRE_COMPILATION` and `COMPILATION` as valid values for this approach,
in addition to the steps defined in the core standard.
```

The operation step values are worth stating here since `PRE_COMPILATION` and `COMPILATION` are steps that exist in the programmatic flow but not in JRXML (where compilation is startup-only and not a per-request logged step).

---

## Section 3.5 — Security Contract

**Status:** PARTIAL

### What is present
- **Classification in JasperDesign before compilation** — Enforced: classification and sensitivity as static text in page header and footer bands before compilation. ✅
- **Expression injection prevention** — `$F{}` expressions must not be constructed from raw caller-supplied column names without validation through the column metadata contract. Aligned with OWASP A03:2021. ✅
- **Explicit extension of base contract** — "Extends the base security contract in `Report_Core_Standards.md` Section 3.5." ✅

### What is missing or needs enhancement
- **Base Standard / Org Standard split absent** — No split is present. Base Standard should cover: expression injection prevention (OWASP A03:2021). Org Standard should cover: the classification-before-compilation enforcement and the static-text-only requirement for classification tags (Org-specific classification values and rendering policy).

---

## Section 4 — Architectural Design

**Status:** PARTIAL

### What is present
- **Enforced Constraint / Design Choice / Assumption labels** — All observations across Sections 4 and 5 are labeled correctly. ✅
- **Section 4.1 Page Layout** — Page dimensions enforced explicitly; A4 portrait as Design Choice. ✅
- **Section 4.2 Required Bands** — Band requirements per format with explicit format scoping (PDF-only vs all formats). ✅
- **Section 4.3 Field Declarations** — One `JRDesignField` per column as Enforced Constraint; valueClass mismatch as Assumption. ✅
- **Section 4.4 Classification Rendering** — Static text (not dynamic expression) as Enforced Constraint; tag format as Design Choice. ✅
- **Section 4.5 Detail Band Text Fields** — Expression syntax constraints and overlap prohibition. ✅
- **Section 5 Compile and Cache Strategy** — Per-request compilation prohibited as Enforced Constraint; fixed vs dynamic cache as Design Choices; thread safety as Enforced Constraint; cache invalidation on restart as Assumption. ✅

### What is missing or needs enhancement
- **Runtime Context not described** — The same gap identified in the core standard persists here. The programmatic design approach requires actor identity for audit logging (same as the core), but where identity is resolved and passed into the construction/export operation is not described in either standard. Since this document extends the core, add an explicit note here or confirm it is addressed by the core.

---

## Section 5 — Test & Validation Standard

**Status:** PARTIAL

### What is present
- **Unit tests** — Section 6.1 provides a comprehensive table of 9 unit test scenarios covering all column validation paths. ✅
- **Integration tests** — Section 6.2 provides 5 integration test scenarios covering PDF export at boundary conditions, concurrency, and dynamic column caching. ✅
- **Classification tag verification** — Section 6.3 adds specific assertion guidance for PDF classification tag presence, including a fallback approach for frameworks that cannot inspect PDF content. ✅
- **Integrator responsibility stated** — "All tests listed below are the integrator's responsibility to author and maintain." ✅

### What is missing or needs enhancement
- **Test data guidelines absent from this document** — The base test data guidelines are in `Report_Core_Standards.md` Section 5.3. This document does not provide a cross-reference to them, nor does it add programmatic-design-specific test data guidance. Add a brief note referencing the core guidelines and extending with any programmatic-specific requirements — for example: use deterministic column definitions (fixed names, types, and widths) for reproducible layout tests; include column-width boundary cases (exactly at limit, one unit over) in test column sets.

---

## Section 6 — Operational Runbook

**Status:** MISSING

### What is present
Nothing. No Operational Runbook section exists. The document defers entirely to the core standard.

### What is missing or needs enhancement
The programmatic approach introduces one operational concern not present in JRXML: the dynamic column configuration cache. If this cache grows unbounded, it will cause memory pressure. Add a minimal stub acknowledging this, even if all other operational guidance is inherited from the core:

```
## 6. Operational Guidance

All operational guidance from `Report_Core_Standards.md` Sections 6.1–6.4 applies.

**Additional consideration — Dynamic column cache**:
Monitor JVM heap usage if the programmatic design path is used with dynamic (caller-variable) column sets.
A bounded cache must be configured (see Section 5); alert if heap growth correlates with unique column configurations.
```

---

## Section 7 — Appendix

**Status:** PARTIAL

### What is present
- **Standards Referenced** — Lists the core standard, recipes document, JasperReports API, and OWASP A03:2021. All references are behaviorally grounded. ✅
- **Changelog** — Present in a dedicated `### Changelog` subsection with 2 entries (v1.0 and v1.1). ✅

### What is missing or needs enhancement
- **Glossary absent** — No `### Glossary` section exists. The document uses terms that are not defined in the core standard's Glossary and are not defined as behavioral concepts in Section 1: "pre-compilation validation" (distinct from the core's "pre-fill validation"), "column descriptor / column metadata", "usable page width", "detail band", "title band", "column header band", "static text element." Add a `### Glossary` subsection defining the terms introduced by this document.

---

## Action List

A prioritised list of changes the document author must make:

### Must Fix (rule VIOLATIONS)

1. **Fix Violation 1 — Replace deprecated exception class names** — Replace `ReportValidationException` and `InvalidFilePathException` with behavioral descriptions in the Section 2.2 failure flowchart and the Section 3.2 error table. Apply the same fix as required for `Report_Core_Standards.md`.
2. **Fix Violation 2 — Replace `ReportColumn` and `JasperDesignBuilder`** — Replace `ReportColumn` with a behavioral description of the column descriptor contract throughout (Section 1 definitions, Section 3.1 inputs table, Section 6.1 test table). Replace `JasperDesignBuilder` in the Section 2.1 sequence diagram with a behavioral participant label.

### Should Fix (PARTIAL / MISSING sections)

3. **Add minimal stub for Section 3.3 Audit Contract** — State that no additional audit requirements apply beyond the core standard; note that `PRE_COMPILATION` and `COMPILATION` are valid operation step values for this approach.
4. **Add minimal stub for Section 3.4 Logging Contract** — State that no additional logging requirements apply; extend the operation step values from the core to include `PRE_COMPILATION` and `COMPILATION`.
5. **Add minimal stub for Section 6 Operational Runbook** — Defer to core; add a note about monitoring JVM heap when dynamic column caches are in use.
6. **Add Base Standard / Org Standard split to Sections 3.1, 3.2, and 3.5** — Each contract subsection requires this split. For 3.1: Base = column validation rules, datasource binding; Org = Org-specific column naming or type constraints. For 3.2: Base = exception semantics + HTTP codes; Org = behaviorally-described exception names. For 3.5: Base = injection prevention; Org = classification-before-compilation enforcement.
7. **Expand Section 1 definitions to 5–7 behavioral concepts** — Add at minimum: "Pre-compilation validation" (validation of inputs before any JasperDesign is constructed), "Column descriptor" (the column metadata contract — name, header label, value class, rendered width), and "Usable page width" (PAGE_WIDTH minus left and right margins; the maximum sum of column widths). Rewrite existing definitions as behavioral concepts rather than class-name definitions.
8. **Add Runtime Context note to Section 4** — Explicitly state how actor identity is resolved for audit purposes in the programmatic design context, or cross-reference the core standard if it addresses this.
9. **Add test data guidelines cross-reference and extension to Section 5 (Section 6 in document)** — Reference `Report_Core_Standards.md` Section 5.3 and add programmatic-specific guidance: deterministic column definitions, column-width boundary cases.
10. **Add Glossary to Section 7** — Define terms introduced by this document: pre-compilation validation, column descriptor/column metadata, usable page width, detail band, title band, column header band, static text element.

### Consider (minor gaps or enhancements)

11. **Clarify `JasperPrint` reference in Section 6.3** — Section 6.3 mentions asserting against the `JasperPrint` page header and footer bands as a fallback for test frameworks that cannot inspect PDF content. `JasperPrint` is a JasperReports class (permitted), but the assertion instruction assumes knowledge of the JasperReports test API. Consider linking to or referencing the Recipes document for a concrete test pattern.
12. **Add XLSX/CSV band scoping note to Section 4.2** — The band requirements table already notes "PDF only" for page header/footer. Consider adding a sentence confirming that XLSX and CSV exports are not affected by page layout constraints, to make the programmatic approach safe to use without a classification band for non-PDF formats, given that classification is handled differently for those formats.
