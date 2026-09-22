# Research Synthesiser Brief

Turn raw field notes (interview transcripts, observation notes, walkthrough reactions) into a single Research synthesis page.

## Target Output Path
`Knowledge Base/{{track}}/Research-synthesis ({{track}})/Research-synthesis.md`

---

## Step 1 — Confirm the Run Context
Follow the standard context confirmation rules in [filing_and_conventions.md](../filing_and_conventions.md). Elicit:
- **`Programme name`** (`{{programme}}`)
- **`Track`** (`{{track}}`)
- **`Outcome question`**: What the research was trying to answer. Extract this from the interview guide if one exists.

---

## Step 2 — Gather Inputs
Gather inputs following the input precedence in [filing_and_conventions.md](../filing_and_conventions.md):
- Search the programme's artifacts folder for field notes (under Interviews, Field notes, Sessions, Exercises folders).
- Search `Prior-knowledge` summaries (track and programme-wide) that should ground the synthesis.
- Search `Interview-guides` (track and programme-wide) for the outcome question.
- Show the user all found files (separately listing track folder, programme-wide, and broader workspace) and ask them to confirm before reading.
- *In copy-paste mode*: ask the user to paste transcripts. Mark sessions with `--- Session [N] / [role] / [date] ---` to ensure traceability.

---

## Step 3 — Draft Rules
A research synthesis must contain four sections, produced together in this order (each building on the previous):
1. **Themes**: Recurring patterns underneath what people said. Focus on the core patterns. Describe 4–7 themes (fewer is too coarse, more is just listing raw observations). Ground each in session references.
2. **Friction points**: Severe or frequent struggles. Ground in session references and rank by severity × frequency descending.
3. **Problem statement**: Single paragraph (3–5 sentences). Start with `[Role] needs to / cannot / struggles to ...`. Avoid solutions in disguise. Mention alternative framings considered and why the chosen one wins.
4. **Success criteria**: 3–5 measurable, capability-focused criteria indicating a win. Each must link back to the problem statement and friction points.
5. **Open questions**: Gaps that require further research or stakeholder confirmation.

### Drafting Template
```markdown
# Research synthesis

**Outcome question:** [Outcome question]
**Sources:** [List of session refs / page links]

## Themes
- **[Theme name]** — [description]. Evidence: [session refs / page links]

## Friction points

| Friction | Severity (1–5) | Frequency | Type | Evidence |
|---|---|---|---|---|
| [pain] | [N] | [observed in X of Y sessions] | [design / training / systemic] | [session refs] |

## Problem statement
> [Problem statement, 3–5 sentences. Start with "[Role] struggles to..." / "cannot..."]

**Alternatives considered:** [Alternative framings and why this one was chosen]

## Success criteria
- [Criterion] — [how it would be measured / observed]

## Open questions
- [Open question]
```

---

## Step 4 — File the Output
Apply the filing checks in [filing_and_conventions.md](../filing_and_conventions.md) before writing.
- Write target file to `./artifacts/Knowledge Base/{{track}}/Research-synthesis ({{track}})/Research-synthesis.md`.
- Link back to all source field notes using Session IDs and relative links.
- *In copy-paste mode*: return the markdown and the user will file it manually.
