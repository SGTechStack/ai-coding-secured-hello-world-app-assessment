# Prior-knowledge Summariser Brief

Summarise prior knowledge from past programmes on a specific topic.

## Target Output Path
`Knowledge Base/{{track}}/Prior-knowledge/{{topic}}.md`

---

## Step 1 — Confirm the Run Context
Follow the standard context confirmation rules in [filing_and_conventions.md](../filing_and_conventions.md). Elicit:
- **`Programme name`** (`{{programme}}`)
- **`Track`** (`{{track}}`)
- **`Topic`**: What topic or domain is being researched (e.g., "scheduling operator interviews around shift patterns"). Becomes `{{topic}}`.

---

## Step 2 — Gather Inputs
Gather inputs following the input precedence in [filing_and_conventions.md](../filing_and_conventions.md):
- Search the workspace **broadly** across all reachable folders (not just the current programme).
- Look for research writeups, retrospectives, post-iteration reviews, or other programmes' Knowledge Bases.
- Show the user the list of pages found and ask them to confirm before reading.
- *In copy-paste mode*: ask the user to paste past writeups or research summaries.

---

## Step 3 — Draft Rules
A good prior-knowledge summary:
- Identifies recurring patterns or lessons learned.
- Surfaced unresolved questions or contradictions.
- Highlights adjacent work that touched on this topic.
- Cites source pages for every finding.
- If the corpus has nothing on this topic, state so plainly. Do not invent.

### Drafting Template
```markdown
## Recurring patterns
- [Learnings or pattern name] — [evidence/sources]

## Unresolved questions
- [Unresolved question or contradiction]

## Adjacent work
- [Adjacent project name / capability] — [how it related to the topic]

## Sources
- [List of source pages or files used]
```

---

## Step 4 — File the Output
Apply the filing checks in [filing_and_conventions.md](../filing_and_conventions.md) before writing.
- Write target file to `./artifacts/Knowledge Base/{{track}}/Prior-knowledge/{{topic}}.md`.
- *In copy-paste mode*: return the markdown and the user will file it manually.
