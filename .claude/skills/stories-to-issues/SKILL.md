---
name: stories-to-issues
description: Reads dependency-orchestrator output (processor-output.json) and creates GitHub issues for each user story in Gantt scheduling order. Each issue includes acceptance criteria, dev tasks in implementation order, standards references, and scheduling metadata (effort, float/critical path).
allowed-tools:
  - Bash
  - Read
  - Write
  - AskUserQuestion
---

# Stories to Issues

Create GitHub issues from a dependency-orchestrator processor output. Each user story becomes a well-structured issue with all the information a developer needs to start work.

## Prerequisites

The following file must exist from a prior dependency-orchestrator run:
- `processor-output.json` -- the single output of `dag-processor.py`, containing the DAG, scheduling analysis, delivery waves, Gantt data, and node details

This is typically found in the `artifacts/` directory in the project root (e.g., `artifacts/processor-output.json`).

## Step 1 -- Gather Inputs

Ask the user:

1. **Processor output path** -- where is the `processor-output.json` file? (e.g., `artifacts/processor-output.json`)
2. **Target repo** -- which GitHub repo should receive the issues? (e.g., `myorg/myrepo`). If omitted, `gh` uses the current repo.
3. **Team size** -- which team size to use for scheduling data? (default: `max_useful_developers` from the processor output -- the knee of the makespan curve)
4. **Labels** -- any labels to add to all issues? (e.g., `user-story`, `auto-generated`)
5. **Dry run first?** -- recommend a dry run to preview before creating real issues.

## Step 2 -- Validate & Preview

Run the script in dry-run mode first:

```bash
python3 /path/to/issue-creator.py \
  <processor-output.json> \
  --team-size N --repo OWNER/REPO --dry-run
```

Review the output with the user. Confirm:
- Story count matches expectations
- Gantt ordering looks correct
- Sample issue body has the right structure

## Step 3 -- Create Issues

Once the user confirms, run without `--dry-run`:

```bash
python3 /path/to/issue-creator.py \
  <processor-output.json> \
  --team-size N --repo OWNER/REPO \
  --label user-story
```

Issues are created in Gantt scheduling order (earliest start first), so the issue list naturally reflects the delivery sequence.

## What Each Issue Contains

Each issue body includes:

- **Category badge** -- `infrastructure` or `feature`
- **Critical path flag** -- marked if on the critical path
- **Schedule table** -- effort and float (or "Critical" if on the critical path)
- **Acceptance criteria** -- the verifiable definition of done
- **Dev tasks** -- numbered implementation steps in dependency order, with duration estimates and standard tags
- **Standards references** -- table with standard doc, recipes, and questions paths. If any standard has a Questions document, the issue includes a statement requiring all questions to be raised and answered before implementation begins.
- **Dependency links** -- which stories block this one, and which it unblocks

## Script Reference

```
python3 issue-creator.py <processor-output.json> [OPTIONS]

Options:
  --team-size N         Team size for Gantt schedule (default: max_useful_developers from processor)
  --repo OWNER/REPO     Target GitHub repo (default: current repo)
  --dry-run             Preview without creating issues
  --label LABEL         Add label(s) to every issue (repeatable)
```
