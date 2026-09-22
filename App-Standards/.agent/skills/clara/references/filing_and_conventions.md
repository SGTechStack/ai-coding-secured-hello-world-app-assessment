# Filing & Operating Conventions

This document outlines CLARA's strict filing rules, naming conventions, directory structure, input precedence (cascade rules), and the verification steps required before committing any changes to the local filesystem.

---

## 1. Confirming the Run Context

At the start of every artefact run, CLARA must elicit the programme and track tokens before performing any operations:

- **Ask which programme this is for (`{{programme}}`)**:
  - The programme is the named DSTA initiative the user is working on (e.g., `SKYPROTECT`).
  - It is **not** the deployment environment (e.g., ANZ C, on-prem, internet).
  - This is a sanity check to verify CLARA is operating inside the correct workspace before any filesystem updates occur.
- **Ask which track within the programme this artefact belongs to (`{{track}}`)**:
  - Tracks represent workstreams, capability areas, feature lines, or sub-systems.
  - If the artefact spans multiple tracks, the literal track value is **`Programme-wide`**.

### Programme Type Lookup

The `Programme type` is not elicited at run time. Once `{{programme}}` is confirmed, CLARA reads the `Programme type` field from `./artifacts/Knowledge Base/Knowledge Base.md` to determine if the programme is `digital` or `engineering`.

- If this file is missing or unreadable, CLARA must ask the user to confirm the programme type before proceeding.

---

## 2. Knowledge Base Path Convention

All research artefacts file inside a programme's local artifacts folder under a single top-level directory named **`Knowledge Base`**.

### Target Path Template

```
./artifacts/Knowledge Base/{{track}}/<artefact-type> ({{track}})/<name>.md
```

### Path Segments

- **`Knowledge Base`**: Literal folder name.
- **`{{track}}`**: The track name verbatim (e.g., `Operator-console`, `Programme-wide`).
- **`<artefact-type>`**: Folder name carrying the track suffix verbatim, matching the unique-title convention for folder naming on disk (e.g., `Personas (Operator-console)`).
- **`<name>`**: Specific markdown filename with `.md` extension.

### Examples

- `./artifacts/Knowledge Base/Operator-console/Personas (Operator-console)/Console-operator.md`
- `./artifacts/Knowledge Base/Programme-wide/Research-synthesis (Programme-wide)/Research-synthesis.md`
- `./artifacts/Knowledge Base/Tasking-engine/Prior-knowledge (Tasking-engine)/Shift-pattern-effects.md`

---

## 3. Input Precedence (The Cascade Rule)

Every artefact in the Knowledge Base lives at either the **Track-level** scope or the **Programme-wide** scope. When downstream artefacts require upstream inputs (e.g., a storyboard needs a persona), CLARA must search both locations:

1. `Knowledge Base/{{track}}/<artefact-type> ({{track}})/`
2. `Knowledge Base/Programme-wide/<artefact-type> (Programme-wide)/`

### Resolution Precedence

- **Track-level version takes precedence**.
- **Programme-wide version is the fallback**.
- **The fallback must be visible**: CLARA must notify the user which version was resolved and why, ensuring the user is aware of missing track-level inputs.

---

## 4. Field Notes and Session IDs

Field notes are the raw observation records, interview transcripts, and walkthrough notes used for synthesis.

### Folder Placement

A `Field-notes` folder exists at every level of the KB and contains a default template page:

- `Knowledge Base/Programme-wide/Field-notes (Programme-wide)/_Template — Field note (Programme-wide).md`
- `Knowledge Base/{{track}}/Field-notes ({{track}})/_Template — Field note ({{track}}).md`

### Session ID Assignment

Session IDs are track-prefixed, sequential IDs assigned automatically by CLARA (e.g., `PW-01` for Programme-wide, `OC-01` for Operator-console). The prefix represents the initials of the track folder name.

### Write-back Mechanism

- The first time CLARA processes a field note with an empty `Session ID` field, she assigns the next available sequential ID and writes it back to the markdown note file.
- On subsequent runs, CLARA reads the existing stamped ID.
- **Carve-out from verification**: Session ID stamping is the **only** write operation that is done automatically without prompting the user first.
- **Failure handling**: If write-back fails, CLARA must stop immediately and report the error; she must never proceed with synthesis using an unstamped note.

### Citations

When citing field notes, CLARA must use both:

1. **Inline Session ID** (e.g., `*evidence: OC-03*`)
2. **Relative Markdown path** to the field note file.

---

## 5. Filing Guardrails and Verification Steps

When preparing to write or update any file, CLARA must apply the following checks top-down:

1. **Base Directory Check**:
   - Verify `./artifacts` directory exists. Ask the user before creating it if it is missing.
2. **Hierarchy & Folder Creation Check**:
   - Resolve the target path top-down. If intermediate folders are missing, list all missing folders in the confirmation prompt so the user authorizes them together with the leaf file.
   - Folders must be named verbatim, appending the correct track suffix for the artefact-type folder.
   - Folder placeholder files must contain the body text: `"Placeholder — created to support filing structure."`
3. **No Silent Fallbacks**:
   - If the full target path is blocked or uncreatable, halt and report it to the user. Never write to default or fallback paths without explicit consent.
4. **Update vs. Create Check**:
   - If a file already exists at the target path, ask the user whether to overwrite the file or save a new version at an alternative path. Never silently overwrite.
5. **Post-Write Verification**:
   - After writing, verify the file exists on disk and has a non-zero size. Halt and report if verification fails.
