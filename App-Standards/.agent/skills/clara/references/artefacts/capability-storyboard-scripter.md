# Capability-storyboard Scripter Brief

Script a visual storyboard showing how a capability is exercised end-to-end.

## Target Output Path
`Knowledge Base/{{track}}/Capability-storyboards/{{storyboard-title}}.md`

---

## Step 1 — Confirm the Run Context
Follow the standard context confirmation rules in [filing_and_conventions.md](../filing_and_conventions.md). Elicit:
- **`Programme name`** (`{{programme}}`)
- **`Track`** (`{{track}}`)
- **`Storyboard title`**: Short name (e.g., "Tank-crew alerting under degraded comms"). Becomes `{{storyboard-title}}`.
- **`Length`**: Number of panels (typically 8–12; shorter (5–6) for quick briefs, longer (15+) for full narratives).
- **`Audience`**: Target audience (e.g., operators reviewing the capability for the first time, or the engineering team scoping the build).

---

## Step 2 — Gather Inputs
Gather inputs following the input precedence in [filing_and_conventions.md](../filing_and_conventions.md):
- Read the operational scenario at the path the user named (fall back to `Knowledge Base/Programme-wide/Operational scenarios/*`).
- Optionally read the capability spec at `Knowledge Base/{{track}}/Capability specs/*` (or programme-wide).
- Optionally read the persona at `Knowledge Base/{{track}}/Personas/*` (or programme-wide) to anchor the protagonist.
- Show the user what you found and confirm length + audience before drafting.
- *In copy-paste mode*: ask the user for the operational scenario, capability spec (if available), length, and audience.

---

## Step 3 — Draft Rules
A good storyboard script:
- Hooks the audience in the first panel — the operational situation that demands the capability.
- Shows the capability in use, not just the capability as a diagram.
- Includes at least one failure-recovery panel — the moment where the capability earns its keep.
- Builds tension before resolution — don't lead with the outcome.
- Ends with the changed state — what is different now that this capability exists.
- Each panel describes WHAT TO SHOW (visual subject) + WHAT'S HAPPENING (narration).

### Drafting Template
```markdown
## Storyboard: [capability name]
**Audience:** [audience]
**Panels:** [N]

### Panel 1
- **Show:** [the visual subject]
- **Happening:** [narration in 1–2 sentences]
- **Beat purpose:** [why this panel exists in the narrative]
- **Carry-over:** [what changes from the prior beat — "n/a" for panel 1]

(repeat for each panel)

### Continuity notes
- Things that should stay consistent across panels (operator's equipment, time of day, etc.).

### Suggested rendering tool
- Recommends Luma / Nano Banana / Forma for each panel based on visual needs.
```

---

## Step 4 — File the Output
Apply the filing checks in [filing_and_conventions.md](../filing_and_conventions.md) before writing.
- Write target file to `./artifacts/Knowledge Base/{{track}}/Capability-storyboards/{{storyboard-title}}.md`.
- Link to the operational scenario and capability spec using relative Markdown links.
- *In copy-paste mode*: return the markdown and the user will file it manually.
