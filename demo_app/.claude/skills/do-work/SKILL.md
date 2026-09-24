---
name: do-work
description: "Execute a unit of work end-to-end: implement, review, validate with typecheck and tests, then commit. Use when user wants to do work, build a feature, fix a bug, or implement a phase from a plan."
argument-hint: "[--kblog]"
---

# Do Work

## User Input

```text
$ARGUMENTS
```

Only if arguments are passed, You MUST consider the user input before proceeding.

## Workflow

Resolve workflow files and scripts relative to this skill directory, not the repo root.

1. Resolve AWS Bedrock KB retrieval: yes only when [../query-kb/SKILL.md](../query-kb/SKILL.md)'s `--check` command succeeds from the repo root.
2. Resolve detailed KB evidence logging: `--kblog` or an explicit request turns it on; otherwise off. It is active only when AWS Bedrock KB retrieval is available.
3. Check whether subagents are available.
4. If subagents are available, read and do [SUBAGENTS.md](SUBAGENTS.md).
5. Otherwise, read and do [DEFAULT.md](DEFAULT.md).

If the user explicitly asks for no subagents, use [DEFAULT.md](DEFAULT.md).
If the user asks for subagents but they are unavailable, warn once, then use [DEFAULT.md](DEFAULT.md).
