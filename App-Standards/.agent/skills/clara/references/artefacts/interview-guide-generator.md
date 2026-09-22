# Interview-guide Generator Brief

Generate a field-ready interview guide that surfaces the data the team needs.

## Target Output Path
`Knowledge Base/{{track}}/Interview-guides/{{topic}}.md`

---

## Step 1 — Confirm the Run Context
Follow the standard context confirmation rules in [filing_and_conventions.md](../filing_and_conventions.md). Elicit:
- **`Programme name`** (`{{programme}}`)
- **`Track`** (`{{track}}`)
- **`Topic`**: Short topic identifier (e.g., "operator decision-making under time pressure"). Becomes `{{topic}}` and shapes the questions.
- **`Interviewee`**: Target operator role, seniority, and number of planned sessions.
- **`Outcome question`**: What the research needs to answer. Be specific (e.g., "do operators trust the alert system enough to act on it without secondary confirmation?").

---

## Step 2 — Gather Inputs
Gather inputs following the input precedence in [filing_and_conventions.md](../filing_and_conventions.md):
- Search `Prior-knowledge` summaries on this topic in track and programme-wide directories. Read them to avoid duplicate questions.
- Show the user what you found and ask them to confirm.
- *In copy-paste mode*: ask the user to paste any prior-knowledge summary or context.

---

## Step 3 — Draft Rules
A good interview guide:
- Focuses on open-ended, non-leading questions.
- Clearly states what the interviewer is listening for (the signals that answer the outcome question).
- Includes concrete follow-up probes.
- Performs self-check: no questions presuppose answers, and no questions reference hypothetical solutions.

### Drafting Template
```markdown
## Interview guide — {{topic}}

**Purpose:** [restate the outcome question]
**Interviewee:** [role / seniority]
**Estimated duration:** [target minutes]

### Warmup (5 min)
Low-stakes questions to establish rapport and context.
- [Question]
  - *Listening for:* [signal]

### Core (20–30 min)
Questions that directly address the outcome question.
- [Question]
  - *Listening for:* [signal]
  - *Probe:* [follow-up]

### Probes (use as needed)
- "Can you give me an example?"
- "What were you thinking at that moment?"
- "What would have changed your answer?"

### Wrap-up (5 min)
- "Anything I should have asked but didn't?"
- "Who else should I be talking to about this?"

## Anti-leading checks (apply before running)
- No question presupposes the answer.
- No question references a solution the team has imagined.
- Every Core question maps to a piece of the outcome question.
```

---

## Step 4 — File the Output
Apply the filing checks in [filing_and_conventions.md](../filing_and_conventions.md) before writing.
- Write target file to `./artifacts/Knowledge Base/{{track}}/Interview-guides/{{topic}}.md`.
- *In copy-paste mode*: return the markdown and the user will file it manually.
