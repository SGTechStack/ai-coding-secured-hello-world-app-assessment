# User Stories File Format

The Dependency Orchestrator expects user stories in a structured YAML file. This format replaces free-form text files — it's deterministic to parse, schema-validated, and eliminates an entire LLM parsing stage.

If you have an existing unstructured stories file (markdown, spreadsheet export, Jira dump), use the `/convert-stories` skill to convert it to this format.

---

## Quick Example

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
    title: "User can log in with email and password"
    group: patient_intake
    release: 1
    acceptance_criteria:
      - "Given a registered user, when they enter valid credentials, then they see the dashboard"
      - "Given invalid credentials, when they submit, then they see an error message"

  - id: "1.2"
    title: "User receives MFA challenge on login"
    group: patient_intake
    release: 1
    acceptance_criteria:
      - "Given MFA is enabled, when the user logs in, then they are prompted for a 6-digit OTP"

  - id: "2.1"
    title: "Generate patient PDF report"
    group: reporting
    release: 2
    acceptance_criteria:
      - "Given completed intake, when user clicks Export, then PDF downloads"
      - "Report includes patient demographics, triage assessment, and timestamps"
```

---

## Schema Reference

### Top-Level Fields

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `groups` | No | array | Business value groups. If omitted, all stories go into a single default group. |
| `releases` | No | array | Sequential release definitions. If omitted, all stories are a single release. |
| `stories` | **Yes** | array | The user stories (minimum 1). |

### Group Object

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `id` | **Yes** | string | Machine-friendly identifier, `snake_case` (e.g., `patient_intake`). |
| `name` | **Yes** | string | Human-readable name (e.g., "Patient Intake"). |
| `priority` | No | integer | Scheduling priority. Lower = higher priority. Same number = parallel. Default: `1`. |

**Priority rules:**
- `1` = highest priority, scheduled first
- Stories in a priority-1 group are scheduled before stories in a priority-2 group
- Groups with the same priority number can be parallelized
- If omitted, defaults to `1` (no deprioritization)

### Release Object

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `number` | **Yes** | integer | Release sequence number (1, 2, 3...). |
| `description` | No | string | Optional label (e.g., "MVP", "Phase 2"). |

Release N must complete before release N+1 starts. The orchestrator enforces this by injecting cross-release dependency edges.

### Story Object

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `id` | **Yes** | string | Unique identifier (e.g., `"1.1"`, `"US-001"`). |
| `title` | **Yes** | string | Story title or "As a..." statement. |
| `acceptance_criteria` | **Yes** | array of strings | Acceptance criteria. Copied verbatim into reports and GitHub issues. |
| `group` | No | string | Group ID this story belongs to. Must match a `groups[].id`. Defaults to `"default"`. |
| `release` | No | integer | Release number. Must match a `releases[].number`. Defaults to `1`. |

---

## Validation

The orchestrator validates your file immediately after you provide the path — before any expensive work. It checks:

1. **YAML syntax** — is the file valid YAML?
2. **Schema compliance** — does it match the expected structure? (missing fields, wrong types)
3. **Unique story IDs** — no duplicates
4. **Referential integrity** — every story's `group` exists in `groups`, every story's `release` exists in `releases`
5. **Story count** — reported back to you for confirmation

If validation fails, you get a specific error message pointing to the problem. Fix the file and re-run.

---

## Tips

- **Quote numeric IDs** — YAML treats bare `1.1` as a float. Always quote: `id: "1.1"`.
- **Multi-line acceptance criteria** — use YAML list syntax, one criterion per line.
- **Groups are optional** — if you don't need business grouping, omit the `groups` section entirely.
- **Releases are optional** — if everything ships together, omit the `releases` section.
- **Priority is numeric** — use integers, not words like "high" or "low". Lower number = higher priority.
