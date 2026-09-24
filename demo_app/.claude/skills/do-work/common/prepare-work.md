# Prepare Work

Use at the start of implementation. Select or resolve the work item and read the source issue and upstream context. Do not edit code until the before-coding gates pass.

## Work Selection

- If the parent passed an issue reference, use that exact issue.
- If no issue reference was passed, read `docs/agents/issue-tracker.md` and select the next unblocked implementation issue according to that tracker's rules.
- If the selected item is a PRD, parent, epic, tracking issue, or blocked issue, return `Status: blocked` and name the implementation issue or blocker that should be handled first.

## Context

Read only context needed for the selected issue:

- selected issue body and comments
- parent PRD or plan when referenced or discoverable from the issue; do not skip this for logic work because test seams may live there
- `CONTEXT.md` or `CONTEXT-MAP.md` when present
- ADRs relevant to the selected issue
- code files needed to implement the issue

Do not run broad directory reads or searches over dependency, build, generated, or coverage directories such as `node_modules/`, `target/`, `dist/`, or coverage outputs.

## Before Coding Gates

Before implementation, confirm you have:

- exactly one selected issue
- acceptance criteria for that issue
- test seams for backend code or frontend logic, hooks, API clients, and utils
- relevant out-of-scope constraints
- narrow files/modules needed for the issue

For backend code or frontend logic, hooks, API clients, and utils, `Test seams` are required. If they cannot be derived from the issue, parent PRD/plan, ADRs, or existing test patterns, return `Status: blocked` and name the missing seam decision. Do not ask the user from a subagent.

Gate: if the task is ambiguous, return `Status: blocked` and name the missing scope decision.

Gate: do not implement during preparation.
