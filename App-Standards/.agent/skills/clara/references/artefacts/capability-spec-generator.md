# Capability-spec Generator Brief

Derive measurable capability requirements from an operational scenario.

## Target Output Path
`Knowledge Base/{{track}}/Capability specs/{{capability-name}}.md`

---

## Step 1 — Confirm the Run Context
Follow the standard context confirmation rules in [filing_and_conventions.md](../filing_and_conventions.md). Elicit:
- **`Programme name`** (`{{programme}}`)
- **`Track`** (`{{track}}`)
- **`Operational scenario`**: Relative page path under `Knowledge Base/{{track}}/Operational scenarios/*` to base the spec on.
- **`Capability name`**: Short name (e.g., "Tank-crew alerting aid"). Becomes `{{capability-name}}`.

---

## Step 2 — Gather Inputs
Gather inputs following the input precedence in [filing_and_conventions.md](../filing_and_conventions.md):
- Read the operational scenario at the path the user named (fall back to `Knowledge Base/Programme-wide/Operational scenarios/*` if no track-level version exists).
- Find the capability brief or statement of operational need (under Briefs/Capability/Mission folders).
- Look for known constraints (under Constraints/Compliance/Architecture).
- Show the user what you found and ask them to confirm before reading in detail.
- *In copy-paste mode*: ask for the operational scenario, the capability brief, and any known measurable thresholds (accuracy, latency, recall, etc.).

---

## Step 3 — Draft Rules
A good capability spec:
- Names requirements that the operational scenario actually demands. Every requirement traces back to a scenario beat.
- Is measurable. "System shall be usable" is invalid; "operator completes the primary task within 90 seconds in degraded lighting" is correct.
- Distinguishes functional, performance, and environmental requirements.
- Names constraints honestly (regulatory, integration, schedule).
- Leaves implementation choices open. Specify the WHAT, not the HOW.
- If the scenario doesn't justify a requirement, leave it out and flag under Open questions. Don't invent.

### Drafting Template
```markdown
## Capability spec: [capability name]

### Operational basis
- **Scenario:** [page link or title]
- **Primary mission task supported:** [from scenario]

### Functional requirements
- **FR-1:** [requirement] — traces to scenario beat: [which one]
- **FR-2:** [requirement] — traces to: [...]

### Performance requirements
- **PR-1:** [measurable threshold] — [rationale + evidence]
- **PR-2:** ...

### Environmental requirements
- **ER-1:** [environment] — [tolerance / behaviour required]
- **ER-2:** ...

### Constraints
- **C-1:** [constraint] — [origin: regulatory / integration / schedule]

### Open questions
- [question]

### Out-of-scope (explicit)
- [thing this capability does NOT need to do]
```

---

## Step 4 — File the Output
Apply the filing checks in [filing_and_conventions.md](../filing_and_conventions.md) before writing.
- Write target file to `./artifacts/Knowledge Base/{{track}}/Capability specs/{{capability-name}}.md`.
- Link to the operational scenario file using relative Markdown links.
- *In copy-paste mode*: return the markdown and the user will file it manually.
