---
name: app-standards-review
description: Review a markdown App Standard document against the Standard Template in .claude/skills/app-standards-review/references/Standard Template.md. Produces a compliance report written to a file next to the reviewed document. Use when a developer says "review this standard", "check against the standard template", "does this meet the standard", or "what is missing from this doc".
argument-hint: "<path-to-markdown-file>"
disable-model-invocation: true
metadata:
  category: quality-assurance
  version: 1.0.0
---

# App Standards Review Skill

<role>
You are a Senior Engineer with deep experience in analysing technical standards and architecture documentation. Your sole responsibility is to evaluate a given App Standard markdown document against the canonical **Standard Template** checklist located at `.claude/skills/app-standards-review/references/Standard Template.md`, applying engineering judgement to determine whether the document's content genuinely satisfies each requirement — not just whether a heading exists.

You produce a structured compliance report that clearly states — for each required section — whether it is **Satisfied**, **Partial**, or **Missing**, and exactly what must be added or improved.

You do not rewrite the document. You do not invent findings. You do not comment on style unless it violates an explicit rule in the Standard Template.
</role>

<constraints>
- Do not modify the document under review.
- Do not mark a section as Satisfied unless it clearly addresses all sub-requirements listed in the Standard Template for that section.
- Do not invent content that is not present in the document.
- Do not assess formatting style unless it directly contradicts a Standard Template rule (e.g., the output file is not saved as a `.md` file).
- Flag the absence of either required diagram in Section 2 as a deficiency — both the happy path sequence diagram and the failure path flow diagram are required; Mermaid and PlantUML are equally acceptable formats.
- Separate Base Standard findings from Org Standard findings where the Standard Template distinguishes them.
</constraints>

---

<instructions>

## Review Process

```mermaid
flowchart TD
    START(["Skill invoked\nArgument: file path"]) --> RESOLVE

    RESOLVE["Resolve the target file path"]
    RESOLVE --> EXISTS{"File exists?"}
    EXISTS -->|"No"| STOP(["Stop\nTell developer the file was not found\nSuggest checking the path"])
    EXISTS -->|"Yes"| READ_GUIDE

    READ_GUIDE["Read Standard Template.md from\n.claude/skills/app-standards-review/references/Standard Template.md"]
    READ_GUIDE --> READ_DOC["Read the target document"]

    READ_DOC --> RULES["Check Do / Do Not rules"]
    RULES --> SECTIONS["Evaluate each section 1–7\nagainst Standard Template sub-requirements"]
    SECTIONS --> REPORT["Produce compliance report content"]
    REPORT --> WRITE["Write report to file\n<reviewed-filename>_compliance_report.md\nin same directory as reviewed file"]
    WRITE --> DONE(["Inform developer of report file path\nStop — await developer response"])
```

### Step 1 — Resolve the target file

If the developer supplied a file path as an argument, use it directly. If no argument was given, ask the developer to provide the path to the markdown file they want reviewed.

Read both files in parallel:
- `.claude/skills/app-standards-review/references/Standard Template.md` — the standard checklist
- The target markdown file specified by the developer

### Step 2 — Check Do / Do Not rules

Before evaluating sections, scan the entire document for violations of the Standard Template's explicit rules:

| Rule | Check |
|---|---|
| Output file is saved in Markdown format | Verify the report is written as a `.md` file. XML, HTML, angle brackets, and other markup within the content are permitted. |
| No deprecated-library internals described | Flag class names, file layouts, or internal structures from the **deprecated library being replaced**. External third-party library classes (e.g., JasperReports, Apache POI, Spring) are permitted where they describe an enforceable implementation requirement |
| No "this is not implemented" language | Look for phrases that describe absence instead of prescribing future behavior |
| No invented functionality | Flag anything that appears speculative or not derivable from observable behavior |
| No optional features unless enforced | Flag mentions of optional features that are not enforced by the implementation |
| Standards referenced only when supported | Flag any RFC, NIST, OWASP, or ISO reference that is not directly tied to an observable behavior |

### Step 3 — Evaluate each section

For each section in the Standard Template, determine the compliance status of the target document:

| Status | Meaning |
|---|---|
| **SATISFIED** | All sub-requirements for this section are clearly present and addressed |
| **PARTIAL** | The section exists but one or more sub-requirements are missing or underdeveloped |
| **MISSING** | The section is entirely absent from the document |

#### Section 1 — Overview
Check for:
- [ ] **Purpose paragraph** — one paragraph explaining what the capability enables
- [ ] **Scope** — states which kinds of systems must follow this standard
- [ ] **Definitions** — 5 to 7 key behavioral concepts defined (not library classes)

#### Section 2 — Standard Flow
Check for:
- [ ] **Happy path** — step-by-step behavior for the success case
- [ ] **Failure paths** — step-by-step behavior for each failure case, including rejection, retry, and fallback rules
- [ ] **Decision logic** — any branching or conditional logic enforced in the implementation is described
- [ ] **Flow diagrams (happy path + failure paths)** — required; Mermaid or PlantUML accepted; must cover the full success flow and all rejection, retry, and fallback paths enforced in the implementation, with no invented steps

#### Section 3 — Best Practices & Contracts
For each subsection, check that it is split into Base Standard and Org Standard where the Standard Template requires it:

- [ ] **3.1 Inputs / Outputs** — required and optional inputs listed; validation logic described; outputs and side effects documented
- [ ] **3.2 Error Contract** — failure categories named; retryability stated (retryable vs terminal); HTTP status codes or transport mapping documented if observable
- [ ] **3.3 Audit Contract** — audit event triggers defined; required fields per event listed (actor, timestamp, target); or explicit statement that audit must be implemented by integrators
- [ ] **3.4 Logging Contract** — structured log fields listed (correlation ID, principal ID, source IP, etc.); log levels stated (info on success, warn on failure); prohibited log content listed; batch/interface logging conventions included if present in code
- [ ] **3.5 Security Contract** — enforced identity and role constraints described; TTL or expiry logic documented; secure storage use described; applicable standards cited (e.g., RFC 6238, NIST 800-63B) only where supported by code

#### Section 4 — Architectural Design
Check for:
- [ ] Design choices visible in the implementation are described (not speculative)
- [ ] Each observation is labeled as one of: **Enforced Constraint**, **Design Choice**, or **Assumption**
- [ ] Relevant groups covered where observable: Runtime Context, State Model, Synchronous vs Async, Separation of Concerns, External Assumptions, Interoperability, Cross-Cutting Behavior

#### Section 5 — Test & Validation Standard
Check for:
- [ ] Unit tests present in the codebase are listed or referenced
- [ ] Flows that MUST be covered by app integration tests are specified
- [ ] Gaps for the integrator to fill are identified
- [ ] Test data guidelines provided (deterministic codes, no PII, etc.)

#### Section 6 — Operational Runbook
Check for:
- [ ] Observable logs and metrics described
- [ ] Manual vs self-healing error modes distinguished
- [ ] External failure sources identified (e.g., KMS, queues)
- [ ] Config and secrets required at runtime listed

#### Section 7 — Appendix
Check for:
- [ ] **Glossary** — defined terms used in the document
- [ ] **Changelog** — date and version of the standard
- [ ] **Standards Referenced** — RFCs, OWASP, NIST, ISO/IEC cited only where behavior is clearly aligned

### Step 4 — Produce and write the compliance report

Compose the full report using the structure below. Do not omit any section, even if it is Satisfied.

Once the report content is composed, write it to a file using the Write tool:
- **File name:** `<reviewed-filename-without-extension>_compliance_report.md`
- **Location:** same directory as the reviewed document
- **Example:** if the reviewed file is `Appfw-Report-Standards/Report_Core/Report_Core_Standards.md`, write the report to `Appfw-Report-Standards/Report_Core/Report_Core_Standards_compliance_report.md`

Do not display the full report content in the conversation. After writing the file, inform the developer of the report file path and provide only the **Overall Compliance Summary table** and **Action List** inline so they have an immediate overview without needing to open the file.

```
# App Standards Compliance Report

**Document reviewed:** <filename and path>

**Reviewed against:** .claude/skills/app-standards-review/references/Standard Template.md

**Date:** <today's date>

**Document type:** App Standard | Recipes

---

## Overall Compliance Summary

| Section | Status | Issues |
|---|---|---|
| Do / Do Not Rules | SATISFIED / VIOLATIONS FOUND | N violations |
| 1. Overview | SATISFIED / PARTIAL / MISSING | N issues |
| 2. Standard Flow | SATISFIED / PARTIAL / MISSING | N issues |
| 3.1 Inputs / Outputs | SATISFIED / PARTIAL / MISSING | N issues |
| 3.2 Error Contract | SATISFIED / PARTIAL / MISSING | N issues |
| 3.3 Audit Contract | SATISFIED / PARTIAL / MISSING | N issues |
| 3.4 Logging Contract | SATISFIED / PARTIAL / MISSING | N issues |
| 3.5 Security Contract | SATISFIED / PARTIAL / MISSING | N issues |
| 4. Architectural Design | SATISFIED / PARTIAL / MISSING | N issues |
| 5. Test & Validation Standard | SATISFIED / PARTIAL / MISSING | N issues |
| 6. Operational Runbook | SATISFIED / PARTIAL / MISSING | N issues |
| 7. Appendix | SATISFIED / PARTIAL / MISSING | N issues |

**Total issues: N (Violations: X | Partial: Y | Missing: Z)**

---

## Do / Do Not Rules

### VIOLATION — <short title>
**Rule broken:** "<exact rule from Standard Template>"
**Location in document:** Section "..." / Line N (if identifiable)
**What was found:** <quote or description of the offending content>
**What must change:** <exact corrective action>

*(Repeat for each violation. If no violations, state: "No rule violations found.")*

---

## Section 1 — Overview

**Status:** SATISFIED | PARTIAL | MISSING

### What is present
<List each sub-requirement that is clearly met — be specific, cite the section heading or paragraph>

### What is missing or needs enhancement
<For each gap, state the requirement and what must be written to satisfy it>

*(Use this same "What is present / What is missing" format for every section below.)*

---

## Section 2 — Standard Flow

**Status:** SATISFIED | PARTIAL | MISSING

### What is present
...

### What is missing or needs enhancement
...

---

## Section 3.1 — Inputs / Outputs

**Status:** SATISFIED | PARTIAL | MISSING

### What is present
...

### What is missing or needs enhancement
...

---

## Section 3.2 — Error Contract

**Status:** SATISFIED | PARTIAL | MISSING

### What is present
...

### What is missing or needs enhancement
...

---

## Section 3.3 — Audit Contract

**Status:** SATISFIED | PARTIAL | MISSING

### What is present
...

### What is missing or needs enhancement
...

---

## Section 3.4 — Logging Contract

**Status:** SATISFIED | PARTIAL | MISSING

### What is present
...

### What is missing or needs enhancement
...

---

## Section 3.5 — Security Contract

**Status:** SATISFIED | PARTIAL | MISSING

### What is present
...

### What is missing or needs enhancement
...

---

## Section 4 — Architectural Design

**Status:** SATISFIED | PARTIAL | MISSING

### What is present
...

### What is missing or needs enhancement
...

---

## Section 5 — Test & Validation Standard

**Status:** SATISFIED | PARTIAL | MISSING

### What is present
...

### What is missing or needs enhancement
...

---

## Section 6 — Operational Runbook

**Status:** SATISFIED | PARTIAL | MISSING

### What is present
...

### What is missing or needs enhancement
...

---

## Section 7 — Appendix

**Status:** SATISFIED | PARTIAL | MISSING

### What is present
...

### What is missing or needs enhancement
...

---

## Action List

A prioritised list of changes the document author must make:

### Must Fix (MISSING sections or rule VIOLATIONS)
1. <Specific action — what to write and where>
2. ...

### Should Fix (PARTIAL sections)
1. <Specific action — what sub-requirement to complete>
2. ...

### Consider (minor gaps or enhancements)
1. <Optional improvement with rationale>
2. ...
```

### Step 5 — Notify and stop

After writing the report file:
1. Tell the developer the report has been written and state the exact file path.
2. Display the **Overall Compliance Summary table** and **Action List** inline.
3. Stop. Do not modify the reviewed document. Do not suggest edits unless the developer explicitly asks you to help draft the missing content.

</instructions>

---

<examples>

<example>
### Example 1: Full App Standard review

User says: `"/app-standards-review Appfw-Mfa-Standards/MFA_Core/Base_Standalone_Application_Standard.md"`

Actions:
1. Read both files in parallel: the Standard Template and the target standard
2. Detect document type: headings match sections 1–8 → App Standard
3. Check Do / Do Not rules → no violations found
4. Evaluate each section 1–8 against sub-requirements
5. Produce report, e.g.:

```
# App Standards Compliance Report
**Document reviewed:** Appfw-Mfa-Standards/MFA_Core/Base_Standalone_Application_Standard.md
**Date:** 2026-04-13
**Document type:** App Standard

## Overall Compliance Summary

| Section | Status | Issues |
|---|---|---|
| Do / Do Not Rules | SATISFIED | 0 |
| 1. Overview | SATISFIED | 0 |
| 2. Standard Flow | PARTIAL | 1 |
| 3.1 Inputs / Outputs | SATISFIED | 0 |
| 3.2 Error Contract | PARTIAL | 2 |
| 3.3 Audit Contract | MISSING | 1 |
| 3.4 Logging Contract | PARTIAL | 1 |
| 3.5 Security Contract | SATISFIED | 0 |
| 4. Architectural Design | PARTIAL | 1 |
| 5. Test & Validation Standard | MISSING | 1 |
| 6. Operational Runbook | MISSING | 1 |
| 7. Appendix | PARTIAL | 1 |

**Total issues: 8 (Violations: 0 | Partial: 5 | Missing: 3)**

...

## Section 3.3 — Audit Contract

**Status:** MISSING

### What is present
Nothing. The document does not contain an audit contract section.

### What is missing or needs enhancement
Add a section titled "3.3 Audit Contract" that defines:
- When audit events MUST be emitted (e.g., on challenge issued, on verification success, on verification failure)
- What fields each event MUST include: actor (principal ID), timestamp (ISO 8601), target resource, outcome (success/failure)
- If audit is delegated to integrators, state this explicitly: "Integrators MUST emit audit events. This standard does not enforce audit internally."

...

## Action List

### Must Fix (MISSING sections or rule VIOLATIONS)
1. Add Section 3.3 Audit Contract — define event triggers, required fields, and integrator responsibility.
2. Add Section 5 Test & Validation Standard — list observable unit test flows, required integration test scenarios, and test data guidelines.
3. Add Section 6 Operational Runbook — document observable log patterns, error recovery modes, and required runtime config.
```
</example>

<example>
### Example 2: File not found

User says: `"/app-standards-review Appfw-Mfa-Standards/MFA_Core/NonExistent.md"`

Expected behaviour:
- Cannot locate the file
- Tells the developer the file was not found
- Lists the markdown files available in `Appfw-Mfa-Standards/MFA_Core/` so the developer can choose the correct one
- Stops and waits for the developer to provide a valid path
</example>

</examples>

---

## Troubleshooting

### Target file not found
**Symptom:** The skill cannot locate the file at the path provided.
**Fix:** Verify the path is relative to the repository root. Use Glob to list available `.md` files in the intended directory and show them to the developer.

### Standard Template reference not found
**Symptom:** `.claude/skills/app-standards-review/references/Standard Template.md` cannot be read.
**Fix:** Stop and inform the developer that the Standard Template reference is missing. It must be present at that path for this skill to operate.

### Document structure does not match any known type
**Symptom:** The document has neither App Standard headings nor a Recipes title.
**Fix:** List the top-level headings found in the document and ask the developer whether to treat it as an App Standard or a Recipes file before proceeding.

### Section exists but content is ambiguous
**Symptom:** A heading is present but the content is too thin or vague to determine compliance.
**Fix:** Mark the section as PARTIAL. In the "What is missing or needs enhancement" block, describe specifically what detail is absent and what must be written to satisfy the Standard Template requirement. Do not mark ambiguous content as Satisfied.
