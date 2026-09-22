# Mission-thread Mapper Brief

Map an end-to-end mission thread for the operational task a capability supports.

## Target Output Path
`Knowledge Base/{{track}}/Mission threads/{{mission-task}}.md`

---

## Step 1 — Confirm the Run Context
Follow the standard context confirmation rules in [filing_and_conventions.md](../filing_and_conventions.md). Elicit:
- **`Programme name`** (`{{programme}}`)
- **`Track`** (`{{track}}`)
- **`Operational scenario`**: Page reference under `Knowledge Base/{{track}}/Operational scenarios/*`.
- **`Mission task`**: Be specific (e.g., "detect, identify, and engage an inbound air contact from a frigate"). Becomes `{{mission-task}}`.

---

## Step 2 — Gather Inputs
Gather inputs following the input precedence in [filing_and_conventions.md](../filing_and_conventions.md):
- Read the operational scenario at the path specified (fall back to `Knowledge Base/Programme-wide/Operational scenarios/*`).
- Optionally search for system-context pages (under Systems/Architecture/Platforms).
- Show the user what you found and ask them to confirm before reading in detail.
- *In copy-paste mode*: ask for the operational scenario and a description of the systems/data flows.

---

## Step 3 — Draft Rules
A good mission thread:
- Covers the full task from trigger to outcome — not just the parts the new capability touches.
- Names all actors (people, platforms, automated systems) at each step.
- Shows the flow of data: what gets passed, in what form, and to whom.
- Highlights decision points and the underlying logic (human or automated).
- Identifies step-level dependencies and failure modes (with recovery paths).
- Leaves missing information blank and flags under "Open questions". Do not invent.

### Drafting Template
```markdown
## Mission thread: [mission task]

### Trigger
- **Event:** [what starts the thread]
- **Initial actor:** [who or what acts first]

### Steps

| # | Actor | Action | Inputs | Outputs | Decision point | Dependencies | Failure mode |
|---|---|---|---|---|---|---|---|
| 1 | [actor] | [what they do] | [data in] | [data out] | [if applicable] | [upstream needs] | [what can go wrong + recovery] |
| 2 | ... | ... | ... | ... | ... | ... | ... |

### Outcome
- **Success:** [end state of a clean run]
- **Partial:** [what counts as a partial outcome]
- **Failure:** [what counts as failure, and where recovery routes back]

### Cross-thread dependencies
- [thing that has to be true across the whole thread, e.g., comms availability]

### Open questions
- [question]
```

---

## Step 4 — File the Output
Apply the filing checks in [filing_and_conventions.md](../filing_and_conventions.md) before writing.
- Write target file to `./artifacts/Knowledge Base/{{track}}/Mission threads/{{mission-task}}.md`.
- Link to the operational scenario file using relative Markdown links.
- *In copy-paste mode*: return the markdown and the user will file it manually.
