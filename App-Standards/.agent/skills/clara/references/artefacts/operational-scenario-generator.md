# Operational-scenario Generator Brief

Draft an operational scenario from operator research and a capability brief.

## Target Output Path
`Knowledge Base/{{track}}/Operational scenarios/{{scenario-title}}.md`

---

## Step 1 — Confirm the Run Context
Follow the standard context confirmation rules in [filing_and_conventions.md](../filing_and_conventions.md). Elicit:
- **`Programme name`** (`{{programme}}`)
- **`Track`** (`{{track}}`)
- **`Scenario title`**: Short title (e.g., "Tank crew night transit through contested terrain"). Becomes `{{scenario-title}}`.

---

## Step 2 — Gather Inputs
Gather inputs following the input precedence in [filing_and_conventions.md](../filing_and_conventions.md):
- Search the programme's artifacts folder for operator research (under Interviews/Exercises/Field notes).
- Find the capability brief or statement of operational need.
- Optionally read doctrinal or procedural references (under Doctrine/Procedures/Standards).
- Read Themes and Friction-points sections of `Research-synthesis` if available.
- Show the user what you found and ask them to confirm before reading in detail.
- *In copy-paste mode*: ask for the operator research and Themes/Friction-points of the Research-synthesis.

---

## Step 3 — Draft Rules
A good operational scenario:
- Is specific enough that an operator reading it can point at details and verify them.
- Includes both the smooth operational path and points where things go wrong.
- Names decision points explicitly, with options and stakes.
- Details exactly what the proposed capability would change.
- If research doesn't support a section, leave it blank or flag as an open question. Do not invent.

### Drafting Template
```markdown
## [Scenario title]

- **Operator(s):** [role, training level, equipment]
- **Mission context:** [what they're trying to accomplish]
- **Environmental conditions:** [physical, informational, time pressure]

### Sequence of events
[beat-by-beat description of events]

### Decision points
- **[Decision]:** options and cost
  - Option A: [...] — cost
  - Option B: [...] — cost

### Success modes
- [outcome] — [conditions]

### Failure modes (including partial success)
- [failure] — [trigger] — [evidence from research]

### What the proposed capability changes
- Before: [current state]
- After: [with the capability]
- Specifically: [concrete changes]

### Open questions
- [question]
```

---

## Step 4 — File the Output
Apply the filing checks in [filing_and_conventions.md](../filing_and_conventions.md) before writing.
- Write target file to `./artifacts/Knowledge Base/{{track}}/Operational scenarios/{{scenario-title}}.md`.
- Link to source research pages using relative Markdown links.
- *In copy-paste mode*: return the markdown and the user will file it manually.
