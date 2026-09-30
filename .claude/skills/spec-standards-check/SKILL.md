---
name: spec-standards-check
description: Checks a spec document against the standards and questions referenced in its linked GitHub issue. Flags critical gaps and warnings. Use when you have a spec and want to verify alignment with applicable appfw standards.
---

# Spec Standards Check

Verify a spec addresses the standards and questions referenced by its linked GitHub issue.

## Steps

### 1. Gather Inputs

Read the spec from conversation, working directory, or ask the user. Then resolve the linked GitHub issue using (in order): explicit issue number in the spec or from the user, or content-match the spec's problem statement / solution / user stories against open issues via `gh` and confirm with the user. Fetch the full issue body with `gh issue view`.

### 2. Load Standards and Questions

Find the **"Standards to Refer"** table in the issue body (columns: `#`, `Sub-Standard`, `Standard`, `Recipes`, `Questions`). Extract **every row** — the table may reference multiple standards and multiple questions documents. Read **all** of them. Each standard and each questions document must be checked individually against the spec in subsequent steps. Ask the user for the standards repo path if files cannot be found.

If no such table exists, tell the user: _"No standards references in issue #{number} — this is expected for issues without appfw standards. No check needed."_ Then halt.

### 3. Compare Spec Against Standards and Questions

For **every** standard and **every** questions document loaded in step 2, check the spec for alignment. Do not stop after the first standard — all referenced standards and questions documents must be evaluated. Classify findings as **Critical** or **Warning**. Neither blocks implementation -- both are flags for review.

**Treat every standard as prescriptive.** Tables, mappings, enum values, field names, and constraints in the standard are authoritative — do not reinterpret them as indicative, typical, or flexible. If the spec uses a different value than the standard defines, that is a contradiction — flag it. Compare actual values, not just whether the topic is mentioned.

**Always quote the exact text** from the standard or questions document verbatim in the Requirement/Question column. Do not rephrase. The Finding column explains how the spec relates to it.

Check **both** the standard document and the questions document for each reference. **Every single question** in the questions document must be individually evaluated against the spec -- do not skip, batch, or summarize questions. Every requirement in the standard must also be evaluated.

#### Categories

Each finding must be tagged with one of these categories:

| Category | Applies To | Meaning |
|----------|------------|---------|
| Absent | Standards | A core requirement or mandated pattern is completely missing from the spec |
| Contradicted | Standards | Spec directly contradicts a requirement |
| Unjustified Deviation | Standards | Spec deviates from a requirement without justification or rationale |
| Unacknowledged Deviation | Standards | Spec deviates with good rationale but doesn't acknowledge it as a conscious deviation |
| Weak Justification | Standards | Spec deviates with only partial or unconvincing rationale |
| Partial Coverage | Both | Requirement or question is addressed but incompletely — key details missing |
| Not Considered | Questions | A significant question area shows no evidence of having been considered |
| Minor Gap | Both | A minor or optional aspect is not covered |

#### Critical

- **From standards:** Category is Absent, Contradicted, or Unjustified Deviation.
- **From questions:** Category is Not Considered, and the question covers a significant concern area (security, data flow, integration, error handling, etc.).

"Significant" means the gap could lead to rework, architectural misalignment, or missed non-functional requirements if not addressed.

#### Warning

- **From standards:** Category is Partial Coverage, Weak Justification, or Unacknowledged Deviation.
- **From questions:** Category is Partial Coverage.
- Category is Minor Gap (from either standards or questions).

### 4. Output Report

Output the full report directly in the conversation using this format:

```markdown
# Spec Standards Check: {spec-title}

**Issue:** #{issue-number} — {issue-title}
**Date:** {YYYY-MM-DD HH:mm}
**Verdict:** {PASS | FAIL}

## Summary

| Category | Count |
|----------|-------|
| Critical | {count} |
| Warning | {count} |
| Standards Checked | {count} |
| Questions Reviewed | {count} |

### Flagged Standards

| Standard | Critical | Warning |
|----------|----------|---------|
| {standard-name} | {count} | {count} |

### Flagged Questions

| Questions Document | Critical | Warning |
|--------------------|----------|---------|
| {questions-doc-name} | {count} | {count} |

## Critical

> Significant gaps or contradictions — review recommended.

| # | Source | Category | Requirement / Question | Finding |
|---|--------|----------|------------------------|---------|
| 1 | {source} | {category} | {verbatim quote} | {description} |

_(omit section if none)_

## Warning

> Minor gaps or partial coverage — worth a look.

| # | Source | Category | Requirement / Question | Finding |
|---|--------|----------|------------------------|---------|
| 1 | {source} | {category} | {verbatim quote} | {description} |

_(omit section if none)_

## Detail: {standard-or-questions-name}

| Requirement / Question | Severity | Category | Finding |
|------------------------|----------|----------|---------|
| {verbatim quote} | Critical / Warning / OK | {category} | {description} |

_(one section per standard and per questions document)_
```

### 5. Grill the User on Flagged Items

After writing the report, run a `/fry-me` style grilling session on every item flagged as Critical or Warning. Walk through each flagged standard requirement and question **one at a time**, quoting the exact text from the standard/questions document, and grill the user on:

- Why the spec diverges or omits this item (intentional or oversight)
- What the decision and rationale should be

For each item, provide a recommended answer and alternative options with trade-offs. Always clarify with the user -- do not infer answers from the codebase.

Once all flagged items are resolved, summarise the decisions made and offer to update the spec document with the agreed changes.

## Failure Modes

- **False positives:** Search the entire spec for relevant content before flagging -- check synonyms, related concepts, and different sections.
- **Missing docs:** Ask the user for the standards repo path. Do not guess or skip.
- **Shallow matching:** Compare actual content of requirements, not just matching headings.
- **Softening contradictions:** Never downgrade a contradiction to OK by reinterpreting the standard as flexible. If the spec uses a different value than the standard defines, that is Contradicted — even if the spec's choice seems reasonable. Flag first, let the user decide.
- **Scope creep:** This skill checks and reports only. Do not propose spec changes.
