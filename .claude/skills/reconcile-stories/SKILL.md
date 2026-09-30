---
name: reconcile-stories
description: Reconciles the dependency-orchestrator schedule with actual GitHub issue closure times. Fetches issue status, identifies completed stories, recomputes float and greedy scheduling for remaining work, then regenerates pm-report and dev-report with updated projections and progress markers. The DAG structure is unchanged -- only timings and float values are recomputed.
allowed-tools:
  - Bash
  - Read
  - Write
  - AskUserQuestion
---

# Reconcile Stories

Reconcile a dependency-orchestrator project schedule against actual progress tracked via GitHub issues. As developers close issues (created by the `stories-to-issues` skill), this skill recomputes the schedule to reflect reality.

## What Changes

- **Unchanged:** DAG structure (nodes, edges, story dependencies, topological order)
- **Changed:** Node durations for completed stories set to 0 and node `status` set to `"completed"`, so CPM recomputes float for all remaining nodes, and the greedy simulation re-prioritizes to produce updated projected finish times

## Prerequisites

The following must exist from prior skill runs:

1. **`project-dag.json`** -- the original project DAG (from `dependency-orchestrator`)
2. **`processor-output.json`** -- the original processor output (from `dependency-orchestrator`)
3. **GitHub issues** -- created by `stories-to-issues`, with titles in the format `STORY-ID: title`

The `gh` CLI must be authenticated and able to access the target repository.

## Step 1 -- Gather Inputs (MANDATORY)

**You MUST ask the user ALL of the following before proceeding to Step 2. Do NOT skip or assume defaults for any item -- always confirm with the user first.**

Ask the user:

1. **DAG files location** -- where are `project-dag.json` and `processor-output.json`? (default: `artifacts/` in the project root)
2. **GitHub repo** -- which repo has the issues? (e.g., `myorg/myrepo`). If omitted, `gh` uses the current repo.
3. **Team size** -- how many developers? This affects the greedy schedule. There is no default -- you must ask.
4. **Labels** -- filter issues by label. Always use `--label auto-generated` to match only issues created by `stories-to-issues`. Ask the user if they need additional label filters.
5. **Project start date** -- when did work begin? (YYYY-MM-DD, optional -- used to compute elapsed time)
6. **Output directory** -- where to write the reconciled reports (default: `artifacts/` in the project root)

## Step 2 -- Dry Run

Run the reconciliation in dry-run mode first to preview which stories are mapped and their status:

```bash
python3 /path/to/reconcile.py \
  <project-dag.json> <analyzer-output.json> <output-dir> \
  --repo OWNER/REPO --dry-run
```

Review with the user:
- How many stories mapped to issues
- How many are closed (completed) vs open (remaining)
- Any unmapped stories (issues not found)

## Step 3 -- Generate Reconciled Reports

Once confirmed, run without `--dry-run`:

```bash
python3 /path/to/reconcile.py \
  <project-dag.json> <analyzer-output.json> <output-dir> \
  --repo OWNER/REPO \
  --team-size N \
  --project-start YYYY-MM-DD \
  --label user-story
```

## Output Files

Reports are versioned (e.g., `-v1-20260629T1558`) and written to `reports/`. Data files go to `data/`.

| File | Directory | Description |
|------|-----------|-------------|
| `analyzer-output-reconciled-vN-TIMESTAMP.json` | `data/` | Full recomputed analysis with reconciliation summary |
| `dag-reconciled-vN-TIMESTAMP.json` | `data/` | Modified DAG (completed nodes duration=0) |
| `pm-report-reconciled-vN-TIMESTAMP.html` | `reports/` | PM report with progress bar and DONE badges |
| `dev-report-reconciled-vN-TIMESTAMP.html` | `reports/` | Dev report with progress bar and DONE badges |
| `dev-report-reconciled-vN-TIMESTAMP.md` | `reports/` | Markdown delivery plan with reconciliation summary |

## How the Reconciliation Works

1. **Fetch issues** from GitHub via `gh issue list --state all`
2. **Map issues to stories** by parsing story ID from issue title (`STORY-ID: ...`)
3. **Identify completed stories** (issues with state `CLOSED`)
4. **Modify the DAG**: set `duration_days = 0` and `status = "completed"` for all nodes owned by completed stories
5. **Rerun the full analysis pipeline**:
   - CPM forward/backward pass with new durations
   - Float recomputation (remaining stories may gain or lose slack)
   - Greedy simulation (minimum-float priority) for team size estimates
   - Story-level CPM and delivery waves
6. **Generate updated reports** with:
   - Reconciliation progress bar (stories done, remaining work, schedule variance — ahead/behind)
   - DONE badges on completed story cards showing the actual completion date (from GitHub issue close date)
   - Faded styling for completed stories
   - Updated Gantt chart reflecting remaining work only

## Script Reference

```
python3 reconcile.py <project-dag.json> <analyzer-output.json> <output-dir> [OPTIONS]

Options:
  --repo OWNER/REPO         Target GitHub repo (default: current repo)
  --team-size N             Team size for scheduling (default: from project)
  --label LABEL             Filter issues by label (repeatable)
  --project-start YYYY-MM-DD  Project start date for elapsed time calculation
  --dry-run                 Preview without generating reports
```
