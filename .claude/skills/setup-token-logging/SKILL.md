---
name: setup-token-logging
description: Configures your project to automatically log token usage and costs every time a skill runs. It installs a post-run hook to track tokens, provides a Python script to generate cost reports, and updates .gitignore to keep logs local.
---

# Setup Token Logging

## Instructions

Set up skill invocation tracking in the current project. Follow these steps in order:

### 0. Find the project root

Determine the project root by finding the nearest ancestor directory containing `.git/`. All `.claude/` paths below are relative to this project root. `cd` to the project root or use absolute paths for all file operations.

### 1. Locate source files

The source files live alongside this SKILL.md:

- `track-token-count.sh` — the Stop hook script
- `aggregate.py` — cost aggregation script

Resolve the directory of this skill file to find them.

### 2. Copy the hook script

```bash
mkdir -p <project-root>/.claude/hooks
cp <skill-dir>/track-token-count.sh <project-root>/.claude/hooks/track-token-count.sh
chmod +x <project-root>/.claude/hooks/track-token-count.sh
```

### 3. Copy the aggregation script

```bash
mkdir -p <project-root>/.claude/analytics
cp <skill-dir>/aggregate.py <project-root>/.claude/analytics/aggregate.py
```

### 4. Configure the Stop hook in `.claude/settings.json`

Read the existing `<project-root>/.claude/settings.json` if it exists. Merge the following `hooks` configuration into it, preserving any existing settings and hooks:

```json
{
  "hooks": {
    "Stop": [
      {
        "matcher": "",
        "hooks": [
          {
            "type": "command",
            "command": "${CLAUDE_PROJECT_DIR}/.claude/hooks/track-token-count.sh"
          }
        ]
      }
    ]
  }
}
```

If a `Stop` hook array already exists, append the new entry only if `track-token-count.sh` is not already configured.

### 5. Update `.gitignore`

Add the following line to `<project-root>/.gitignore` if not already present:

```
.claude/analytics/skill-invocations.jsonl
```

### 6. Ensure `jq` is available

Run `which jq`. If not found:

1. Ask the user for permission to install `jq` (it is required for the hook to work).
2. If they approve, install it:
   - macOS: `brew install jq`
   - Linux (Debian/Ubuntu): `sudo apt-get install -y jq`
3. If they decline, warn them that the hook will silently fail without `jq` and they must install it themselves before it will work.

### 7. Verify installation

After all steps above, verify everything is in place:

1. Confirm `<project-root>/.claude/hooks/track-token-count.sh` exists and is executable (`test -x`)
2. Confirm `<project-root>/.claude/analytics/aggregate.py` exists
3. Confirm `<project-root>/.claude/settings.json` contains the `track-token-count.sh` hook entry
4. Confirm `.gitignore` contains the JSONL exclusion line
5. Confirm `jq` is available on `PATH`

If any check fails, report which ones failed and attempt to fix them. If a fix is not possible, clearly tell the user what is missing and how to resolve it.

## Return

Confirm what was set up, including verification results:

- Hook script: `<project-root>/.claude/hooks/track-token-count.sh` — present / missing
- Aggregation script: `<project-root>/.claude/analytics/aggregate.py` — present / missing
- Settings: `<project-root>/.claude/settings.json` (Stop hook configured) — configured / missing
- `.gitignore` updated — yes / no
- `jq` — installed / already present / skipped (user declined)

Mention that skill invocations will now be logged to `.claude/analytics/skill-invocations.jsonl` and that they can run the aggregation report with:

```bash
python3 .claude/analytics/aggregate.py .claude/analytics/skill-invocations.jsonl
```
