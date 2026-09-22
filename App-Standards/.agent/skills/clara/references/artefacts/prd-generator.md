# PRD Generator Brief

Draft a v0 PRD from research synthesis and prior framing.

## Target Output Path
`Knowledge Base/{{track}}/PRDs/{{prd-title}}.md`

---

## Step 1 — Confirm the Run Context
Follow the standard context confirmation rules in [filing_and_conventions.md](../filing_and_conventions.md). Elicit:
- **`Programme name`** (`{{programme}}`)
- **`Track`** (`{{track}}`)
- **`PRD title`**: Short name (e.g., "Incident-report capture v1"). Becomes `{{prd-title}}`.

---

## Step 2 — Gather Inputs
Gather inputs following the input precedence in [filing_and_conventions.md](../filing_and_conventions.md):
- Read the Problem-statement and Success-criteria of `Research-synthesis` (fall back to programme-wide if no track-level is found).
- Look up the persona at `Knowledge Base/{{track}}/Personas/*` (fall back to programme-wide). Ask which one if there are multiple.
- Optionally read Themes from the synthesis.
- Find the original stakeholder ask (brief, charter, or request note).
- Show the user what you found and ask them to confirm before reading in detail.
- *In copy-paste mode*: ask for each of these inputs in turn.

---

## Step 3 — Draft Rules
A good PRD:
- Clearly articulates the problem statement without proposing solutions in disguise.
- Links to target personas.
- Proposes measurable success criteria.
- Defines boundaries (what is in scope vs. out of scope).
- Highlights user stories or jobs-to-be-done.
- Details constraints (compliance, integration) and open questions.
- If input is incomplete, ask up to 3 clarifying questions before drafting. Never invent details; use placeholders and flag under "Open questions".

### Drafting Template
```markdown
## Problem statement
[Problem statement, 3–5 sentences. Focus on who has the problem, the impact, and why it persists.]

## Target users
[List target personas and roles with relative links to their files.]

## Success criteria
- [Measurable, capability-focused criteria]

## Scope
- **In scope:** [thing]
- **Out of scope:** [thing]

## User stories
- **As a** [persona/role], **I want to** [action], **so that** [value outcome].

## Constraints and dependencies
- [Constraint / dependency]

## Open questions
- [Question / placeholder]
```

---

## Step 4 — File the Output
Apply the filing checks in [filing_and_conventions.md](../filing_and_conventions.md) before writing.
- Write target file to `./artifacts/Knowledge Base/{{track}}/PRDs/{{prd-title}}.md`.
- Link to the problem statement, success criteria, and persona files using relative Markdown links.
- *In copy-paste mode*: return the markdown and the user will file it manually.
