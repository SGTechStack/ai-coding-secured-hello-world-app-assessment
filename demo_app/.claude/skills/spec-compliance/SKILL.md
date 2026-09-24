---
name: spec-compliance
description: Vets a Specification (Spec) against organisational policies (IM8, ARC). Trigger when validating, auditing, or compliance-checking a Spec or TRD.
---

# Spec Compliance

Use this skill to **vet** a Specification (Spec) or Technical Requirements Document (TRD) against organisational policies before issue decomposition begins.

## Workflows

### Step 1: Locate the Spec

Identify the Spec or TRD content in the current conversation.

* **Completion Criterion:** The Spec/TRD text is fully read and loaded. If missing, halt and prompt the user to provide it.

### Step 2: Audit against Policies

Load the `/policies` skill. Audit the Spec against all IM8 and ARC controls listed in the `/policies` references. Assign each control a **verdict**:

* `PASS` — Explicitly meets the control criteria.
* `AUTO-FIX` — Vague or missing details that can be resolved by tightening wording or clarifying scenarios.
* `FAIL` — Contradicts the policy or omits core requirements (e.g., missing audit logging).
* `DEFERRED` — Legitimate policy requirement that is not applicable at the Spec phase and must be handled during implementation.
* `N/A` — Control does not apply to this application's scope.
* **Completion Criterion:** Every control in `/policies` has a dedicated row in the audit table with an explicit verdict and a specific, logical reason.

### Step 3: Write the Compliance Report

Derive a slug from the Spec title (lowercase, alphanumeric, spaces to hyphens). Write a self-contained HTML file to `artifacts/spec-compliance/spec-compliance-{spec-slug}-{YYYY-MM-DD-HHmm}.html`.

* **Progressive Disclosure:** Refer to [HTML-REPORT.md](HTML-REPORT.md) for the exact HTML scaffold and Tailwind class definitions.
* **Completion Criterion:** The HTML file is successfully written to the local filesystem. A summary listing the report path, verdict counts, and all `FAIL` items is output to the conversation.

### Step 4: Resolve and Sync AUTO-FIX and FAIL Findings back to Spec/TRD documents.

For each `AUTO-FIX` or `FAIL` findings, prompt the user for explicit guidance using grill-me and update the results back to Spec/TRD document in conversation.

* **Completion Criterion:** All `AUTO-FIX` and `FAIL` findings are resolved with user guidance. Do not proceed to issue decomposition until all pending verifications are resolved.

## Failure Modes

* **Premature completion:** Marking a check as `PASS` or `N/A` without verifying the specific control in the `/policies` reference document. *Prevention: Verify that every control in the policy checklist has an explicit row in the verdict table.*
* **Scope Creep:** Auto-fixing core architectural design, permission boundaries, or technology choices. *Prevention: Mark these as `FAIL` and present them to the user; do not attempt to auto-fix security scope decisions.*
* **Stale Context:** Auditing an outdated draft of the Spec. *Prevention: Always read the latest Spec/TRD content in the current conversation before commencing Step 2.*
