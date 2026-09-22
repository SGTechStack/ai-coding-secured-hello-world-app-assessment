# Journey-map Drafter Brief

Draft a current-state journey map for a persona.

## Target Output Path
`Knowledge Base/{{track}}/Journeys/{{journey-scope}}.md`

---

## Step 1 — Confirm the Run Context
Follow the standard context confirmation rules in [filing_and_conventions.md](../filing_and_conventions.md). Elicit:
- **`Programme name`** (`{{programme}}`)
- **`Track`** (`{{track}}`)
- **`Persona`**: Page reference or name of the persona.
- **`Journey scope`**: Be specific (e.g., "submitting an incident report from the field"). Becomes `{{journey-scope}}`.

---

## Step 2 — Gather Inputs
Gather inputs following the input precedence in [filing_and_conventions.md](../filing_and_conventions.md):
- Look up the persona at `Knowledge Base/{{track}}/Personas/*` (fall back to `Knowledge Base/Programme-wide/Personas/*`).
- Read Themes and Friction-points sections of `Research-synthesis` if available.
- Search the programme's artifacts folder for interview transcripts and observation notes covering the journey scope.
- Show the user what you found and ask them to confirm before reading in detail.
- *In copy-paste mode*: ask for the persona, journey scope, and the Themes/Friction-points sections of the Research-synthesis.

---

## Step 3 — Draft Rules
A good journey map:
- Maps the journey AS-IS, not the ideal state.
- Has tight stages so that each stage contains only a few actions.
- Names emotions specifically ("frustrated because X", not just "frustrated").
- Cites evidence (Session IDs/files) for every friction point.
- Flags opportunities ONLY if research evidence supports them.
- Leaves blank cells and flags under "Research gaps" if evidence is missing. Do not invent.

### Drafting Template
```markdown
## Journey: [scope]
**Persona:** [name]

### Stage 1: [stage name]
- **Actions:** [what the persona does]
- **Touchpoints:** [systems, people, artefacts they interact with]
- **Emotion:** [specific feeling + because]
- **Friction:** [pain points + evidence: session refs or page links]
- **Opportunity:** [where AI / new capability could help — only if research supports it]

(repeat for each stage)

## Moments of truth
- [moment] — [why it matters]

## Opportunities summary
1. [highest-priority opportunity] — [rationale]
2. ...

## Research gaps
- [gap in data]
```

---

## Step 4 — File the Output
Apply the filing checks in [filing_and_conventions.md](../filing_and_conventions.md) before writing.
- Write target file to `./artifacts/Knowledge Base/{{track}}/Journeys/{{journey-scope}}.md`.
- Link to the persona file and source research pages using relative Markdown links.
- *In copy-paste mode*: return the markdown and the user will file it manually.
