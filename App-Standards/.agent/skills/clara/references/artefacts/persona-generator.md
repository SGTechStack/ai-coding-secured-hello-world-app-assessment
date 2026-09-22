# Persona Generator Brief

Draft a persona from research evidence.

## Target Output Path
`Knowledge Base/{{track}}/Personas/{{persona-name}}.md`

---

## Step 1 — Confirm the Run Context
Follow the standard context confirmation rules in [filing_and_conventions.md](../filing_and_conventions.md). Elicit:
- **`Programme name`** (`{{programme}}`)
- **`Track`** (`{{track}}`)
- **`Persona name`**: Short identifier (e.g., "Field operator"). Becomes `{{persona-name}}`.

---

## Step 2 — Gather Inputs
Gather inputs following the input precedence in [filing_and_conventions.md](../filing_and_conventions.md):
- Search for interview transcripts, observation notes, or survey responses (under Interviews, Field notes, Sessions, Surveys).
- Read Themes and Friction-points sections of `Research-synthesis` if available.
- Show the user what you found and ask them to confirm before reading.
- *In copy-paste mode*: ask the user to paste transcripts (marked with `--- Session [N] / [role] ---`) and Themes/Friction-points from the synthesis.

---

## Step 3 — Draft Rules
A good persona:
- Names a specific archetype, not a vague generic "user".
- Roots every claim in evidence, citing session references or pages.
- Focuses goals on outcomes, not features.
- Highlights at least one surprising or non-obvious trait that differentiates them.
- If research doesn't support a section, leave it blank. Do not invent.

### Drafting Template
```markdown
### [Persona name — specific, memorable; not "User A"]

- **Summary:** [one line summary]
- **Goals (3 to 5):**
  - [goal] — [evidence: session refs or page links]
- **Pains (3 to 5):**
  - [pain] — [evidence: session refs or page links]
- **Context:** [when, where, and with whom they perform tasks]
- **Real quote:** "[verbatim quote]" — [session ref or page link]
- **Non-obvious trait:** [differentiating trait]
- **Evidence sources:** [list of session IDs or page references]
```

---

## Step 4 — File the Output
Apply the filing checks in [filing_and_conventions.md](../filing_and_conventions.md) before writing.
- Write target file to `./artifacts/Knowledge Base/{{track}}/Personas/{{persona-name}}.md`.
- Link back to source research files using relative Markdown links.
- *In copy-paste mode*: return the markdown and the user will file it manually.
