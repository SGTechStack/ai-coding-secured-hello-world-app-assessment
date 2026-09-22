# Test-plan Generator Brief

Draft a complete test plan with scenarios, participants, measurement, and analysis.

## Target Output Path
`Knowledge Base/{{track}}/Test-plans/{{test-name}}.md`

---

## Step 1 — Confirm the Run Context
Follow the standard context confirmation rules in [filing_and_conventions.md](../filing_and_conventions.md). Elicit:
- **`Programme name`** (`{{programme}}`)
- **`Track`** (`{{track}}`)
- **`Test type`**: e.g., usability test on interactive prototype, moderated walk-through, instrumented A/B, capability rehearsal.
- **`Test name`**: Short name (e.g., "Console-v1-usability-test"). Becomes `{{test-name}}`.
- **`Test focus`** (optional): Specific user story or success criteria. If blank, test against all success criteria of the artefact.
- **`Constraints`** (optional): Time budget, recruiting limits, classification, secrecy, environment.

---

## Step 2 — Gather Inputs
Gather inputs following the input precedence in [filing_and_conventions.md](../filing_and_conventions.md):
- Identify the artefact being tested. For digital: a PRD page. For engineering: an operational-scenario + capability-spec pair. Confirm paths with user.
- Read the Success-criteria of the relevant `Research-synthesis` (track or programme-wide).
- Optionally scan field notes for material to seed scenarios (reference field-note Session IDs).
- Show the user what you found and confirm test type, focus, and constraints.
- *In copy-paste mode*: ask the user for the artefact, success criteria, test type, focus, and constraints.

---

## Step 3 — Draft Rules
- **Objective Checkpoint**: Propose a one-sentence objective (what question the test answers) and get user confirmation *before* drafting the rest. If too broad, ask user to narrow it.
- A good test plan:
  - Derives 3–6 scenarios exercising the success criteria, ordered from simple to complex.
  - Names participants (headcount, profile, recruiting source, exclusions).
  - Structures sessions (warmup, scenarios with time budget, debrief/DASH survey).
  - Specifies measurements (observations, metrics, post-session questions, DASH survey type).
  - Lists validity risks and mitigations.
- Rules:
  - Scenarios must map to success criteria *within focus*. Deferred criteria should be noted as "deferred to a later round" rather than inventing scenarios.
  - If a scenario is seeded from a field note, reference the Session ID.
  - Never invent participant counts or recruiting sources. Use placeholders and flag if unknown.

### Drafting Template
```markdown
## Test plan: [name]

### Objective
[One sentence — what question this test answers]

### Success criteria tested
- [criterion] — covered by scenario(s): [refs]

### Scenarios

#### Scenario 1: [name]
- **Setup:** [pre-conditions, system state, participant context]
- **Steps:** [numbered actions]
- **Expected:** [what success looks like]
- **Evidence to capture:** [observations, metrics, artefacts]
- **Maps to:** [success criteria refs]

(repeat for 3-6 scenarios total, ordered by complexity)

### Participants
- **Number:** [N]
- **Profile:** [operators, end users, SMEs]
- **Recruiting source:** [how you'll find them]
- **Exclusions:** [who NOT to include and why]

### Session structure
- **Pre-task (5–10 min):** [briefing, consent, warmup]
- **Scenarios:** [refs, in order, with time budget]
- **Post-task (10 min):** [debrief, planned DASH survey]
- **Total duration:** [N minutes]

### Measurement
- **Behavioural observations:** [task completion, hesitation, errors]
- **Metrics:** [if instrumented]
- **DASH survey:** [prototype survey OR system survey — name which and why]
- **Open questions:** [debrief questions]

### Analysis
- [how raw observations translate into design adjustments]
- [who reviews the DASH output]

### Validity risks
- **[risk]:** [mitigation]
```

---

## Step 4 — File the Output
Apply the filing checks in [filing_and_conventions.md](../filing_and_conventions.md) before writing.
- Write target file to `./artifacts/Knowledge Base/{{track}}/Test-plans/{{test-name}}.md`.
- Link to the target being tested and the Research-synthesis using relative links.
- *In copy-paste mode*: return the markdown and the user will file it manually.
