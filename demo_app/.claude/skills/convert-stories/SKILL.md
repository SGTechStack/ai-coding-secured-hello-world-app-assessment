---
name: convert-stories
description: Converts an unstructured user stories file (markdown, spreadsheet export, Jira dump, etc.) into the structured YAML format required by the Dependency Orchestrator.
allowed-tools:
  - Bash
  - Read
  - Write
  - AskUserQuestion
---

# Convert Stories

You are a conversion assistant. Your job is to take an unstructured user stories file and produce a structured YAML file that conforms to the Dependency Orchestrator's schema.

---

## Input

Ask the user for:

1. **File path** — the absolute path to their unstructured stories file. Verify it exists.
2. **Approximate story count** — roughly how many user stories are in the file. This helps disambiguate hierarchical numbering (e.g., is `14.1` a section or a story?).

That's it. No other questions.

---

## Conversion

1. Read the entire stories file.
2. Extract every story. For each one, capture:
   - `id` — preserve original IDs. If the file uses hierarchical numbering, pick the depth closest to the user's approximate count.
   - `title` — the story title or "As a..." statement.
   - `acceptance_criteria` — copy **verbatim**. If none exist, use the story description as a single criterion.
3. Handle edge cases:
   - **Duplicate IDs** — prefix with section identifier to disambiguate.
   - **Hierarchical numbering** — compare item counts at each nesting depth; pick the depth closest to the user's count.
   - **No clear story boundaries** — ask the user for clarification.

If the source file contains grouping, release assignments, or priority information, capture them:
- Add a `groups` section with the groups found in the file.
- Add a `releases` section if stories are assigned to releases.
- Set `group` and `release` on each story accordingly.
- If priorities are mentioned for groups, set numeric `priority` values (1 = highest).

If the source file does NOT contain this information, omit the `groups` and `releases` sections entirely. The user can add them manually in the YAML file afterward.

---

## Output

Write the output file to the same directory as the input, with a `.stories.yaml` extension:
- Input: `/path/to/requirements.md` → Output: `/path/to/requirements.stories.yaml`

The output must conform to the schema at `<ORCHESTRATOR_DIR>/schemas/stories-schema.json`. Key rules:

- `groups` is optional. If present, each group has `id` (snake_case), `name`, and optional `priority` (integer >= 1, lower = higher priority). No other fields on groups.
- `releases` is optional. If present, each release has `number` (integer >= 1, sequential from 1) and optional `description`. No other fields on releases.
- `stories` is required. Each story has `id`, `title`, `acceptance_criteria`, and optional `group` (string referencing a `groups[].id`) and `release` (integer referencing a `releases[].number`).
- If stories reference groups, a `groups` section must exist. If stories reference releases, a `releases` section must exist.

```yaml
groups:
  - id: patient_intake
    name: Patient Intake
    priority: 1
  - id: reporting
    name: Reporting
    priority: 2

releases:
  - number: 1
    description: "MVP"
  - number: 2
    description: "Phase 2"

stories:
  - id: "1.1"
    title: "Story title"
    group: patient_intake
    release: 1
    acceptance_criteria:
      - "Criterion 1"
      - "Criterion 2"

  - id: "1.2"
    title: "Another story"
    group: reporting
    release: 2
    acceptance_criteria:
      - "Criterion 1"
```

**YAML rules:**
- Always quote story IDs that look numeric (e.g., `"1.1"`, `"14"`)
- Use 2-space indentation
- Include `groups` and `releases` sections only if detected in the source file

---

## Validate

Run the validation script:

```bash
python3 <ORCHESTRATOR_DIR>/scripts/validate-stories.py <output-file>
```

Where `<ORCHESTRATOR_DIR>` is the absolute path to the `dependency-orchestrator` skill directory (a sibling of this skill's directory).

If validation fails, fix the issues and re-validate.

---

## Report

Tell the user what was converted and what they can enhance. Tailor the message based on what was and wasn't found in the source file.

**Always include:**
> "Converted N stories to `<output-path>`."

**Then, for each feature that was NOT found in the source, tell them they can add it:**

- If no groups were detected:
  > "No business grouping was detected. You can optionally add a `groups` section to organize stories by business area (e.g., 'Patient Intake', 'Checkout Flow') and set `group` on each story. Groups let you set scheduling priority — lower number = higher priority."

- If no releases were detected:
  > "No release assignments were detected. You can optionally add a `releases` section to split stories into sequential phases and set `release` on each story. Release N must complete before release N+1 starts."

- If no priorities were detected (even if groups exist):
  > "No group priorities were detected. You can add a `priority` field (integer) to each group — lower number = higher priority. Groups with the same number are parallelized."

**For features that WERE found, confirm what was captured:**
- If groups were detected: > "Found N groups: {list names}."
- If releases were detected: > "Found N releases."
- If priorities were detected: > "Group priorities captured."

**Always end with:**
> "Schema reference: `<ORCHESTRATOR_DIR>/schemas/STORIES-FORMAT.md`
>
> When ready, run `/dependency-orchestrator` and point it to this file."

---

## Important

- Parse EVERY story in the file. Do not skip any.
- Preserve original story IDs where they are unique.
- Copy acceptance criteria **verbatim** — do not summarise or rephrase.
