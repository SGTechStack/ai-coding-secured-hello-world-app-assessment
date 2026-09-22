# Service-blueprint Drafter Brief

Draft a service blueprint linking user actions to front-stage and back-stage support.

## Target Output Path
`Knowledge Base/{{track}}/Service blueprints/{{journey-scope}}.md`

---

## Step 1 — Confirm the Run Context
Follow the standard context confirmation rules in [filing_and_conventions.md](../filing_and_conventions.md). Elicit:
- **`Programme name`** (`{{programme}}`)
- **`Track`** (`{{track}}`)
- **`Journey`**: Page reference under `Knowledge Base/{{track}}/Journeys/*`.

---

## Step 2 — Gather Inputs
Gather inputs following the input precedence in [filing_and_conventions.md](../filing_and_conventions.md):
- Read the journey map named by the user (fall back to `Knowledge Base/Programme-wide/Journeys/*` if no track-level is found).
- Look up the persona referenced by the journey at `Knowledge Base/{{track}}/Personas/*` (fall back to programme-wide).
- Search the programme's artifacts folder for system-context pages (under Systems, Architecture, Operations, Teams folders). If unavailable, leave back-stage cells blank and flag as research gaps.
- Show the user what you found and ask them to confirm before reading in detail.
- *In copy-paste mode*: ask for the journey map, persona, and description of back-stage systems/teams.

---

## Step 3 — Draft Rules
A good service blueprint:
- Aligns customer actions, front-stage, back-stage, and support across the same stages.
- Surfaces invisible back-stage actions that the operator relies on.
- Identifies the specific systems and people involved.
- Highlights handoffs, which are common failure points.
- If information is missing, leave the cell blank and flag under "Research gaps". Do not invent.

### Drafting Template
```markdown
## Service blueprint: [journey scope]
**Persona:** [name]

| Stage | Customer action | Front-stage | Back-stage | Support |
|---|---|---|---|---|
| [stage] | [what they do] | [visible interactions] | [hidden systems / actions] | [supporting processes] |

### Handoffs
- **[Stage] → [Stage]:** [what passes between front-stage and back-stage, and how]

### Visible gaps
- [gap] — [evidence or "research needed"]
```

---

## Step 4 — File the Output
Apply the filing checks in [filing_and_conventions.md](../filing_and_conventions.md) before writing.
- Write target file to `./artifacts/Knowledge Base/{{track}}/Service blueprints/{{journey-scope}}.md`.
- Link to the journey map file using relative Markdown links.
- *In copy-paste mode*: return the markdown and the user will file it manually.
