# Subagent Do Work

## Workflow

Use this when subagents are available.

Use your task/todo list tool to track each numbered step as a task. MUST follow every step exactly; do NOT try to be more efficient. Complete each task only after executing that numbered step; do not batch-complete steps.

The main agent orchestrates. Subagents do the reading, implementation, review, and validation. Do not skip required helpers for speed.

Paths under `implementer/` and `reviewer/` are spawn instructions for subagents. The main agent MUST NOT open them; pass their paths to subagents only.

- Subagents: yes
- AWS Bedrock KB retrieval: use the resolved value from [SKILL.md](SKILL.md).
- Detailed KB evidence logging: use the resolved value from [SKILL.md](SKILL.md).

Main-agent boundary:

- The instructions below are the LAW. Follow them exactly.
- Do NOT do anything extra, including reading files or making decisions yourself!
- Do NOT explore! You do not need more info.

### 1. Run Issue

Do not read the issue body, PRD, tracker, codebase files, role files, or helper files in the main agent.
If the user passed no issue reference, this is not blocked. Do not ask the user what to work on. Spawn the implementer and let it select the work item.

1. Spawn an implementer subagent with the repo path, initial issue reference only if passed by user, else let implementer select, AWS Bedrock KB retrieval value, detailed KB evidence logging setting, existing implementer log location if any, existing log state path if any, `docs/agents/reviewer-decisions.md` as the prior human decisions reference, and this exact first instruction:
   ```
   MUST START by reading and following `do-work/implementer/implementer.md`.
   ```
2. If blocked, stop and report the implementer blocker.
3. Record the implementer return as the issue summary for the reviewer. Do not inspect created or changed files in the main agent.
4. Spawn a reviewer subagent with changed files, selected issue, AWS Bedrock KB retrieval value, detailed KB evidence logging setting, existing reviewer log location if any, existing log state path if any, acceptance criteria covered, test seams used, issue summary if any, and this exact first instruction:
   ```
   MUST START by reading and following `do-work/reviewer/reviewer.md`.
   ```
5. For `Human Decision Needed`, read and use [common/decision-memory.md](common/decision-memory.md). Do not choose for the user.
6. If reviewer returns `Must-fix > 0` or a chosen human decision needs implementation, bundle reviewer must-fixes and chosen human decisions into one correction pass.
7. Route the correction pass to the implementer with the selected issue, previous test seams used, reviewer findings, and prior human decisions reference, then rerun the reviewer with updated changed files, acceptance criteria covered, test seams used, and issue summary.
8. Continue only when `Must-fix=0`, `Human decisions=0`, and required logs return `Log appended: yes`; non-required logs may return `Log appended: skipped`.

Stop after two correction rounds for the same issue and report the blocker.

### 2. Validate

Final gate after the reviewer loop is clean.

- Code-reviewer subagents for all registered checks in [../code-reviewer/SKILL.md](../code-reviewer/SKILL.md). Default code-reviewer mode runs all registered checks.
- Pass the selected issue's `<issue-number>-<issue-slug>` to the aggregator.
- Also instruct the aggregator to also return a compact blocker brief on `FAIL`; do not return non-blocking findings.

MUST check total number of spawned review subagents = all registered code-reviewer checks
and number of aggregator subagents spawned after = 1

Wait for all agents.

### 3. Compliance Report

Ensure the compliance report required by [../code-reviewer/SKILL.md](../code-reviewer/SKILL.md) from the immediately preceding complete final-gate Step 2 batch is written.

Continue only if no code-reviewer check is `FAIL`.

For a code-reviewer `FAIL`, the main agent MUST NOT open, read, or summarize the compliance report. Use the aggregator's blocker brief to track blockers across reruns. Pass the unread report path to the implementer and instruct it to read the report and fix the blockers, then rerun reviewer and Steps 2-3.

To prevent infinite loops, only if the same blocker remains after two correction passes, stop and request human intervention.

### 4. Mutation Gate

Optionally spawn one mutation-testing subagent to follow [../mutation-testing/SKILL.md](../mutation-testing/SKILL.md). Instruct it to fix Critical or High survivors and rerun mutation testing until none remain or progress is blocked; do not route findings to other agents or rerun earlier workflow steps.

### 5. Commit

**Ignore hygiene** — Follow [common/gitignore.md](common/gitignore.md).

Once all checks pass, use [../git-commit-skill/SKILL.md](../git-commit-skill/SKILL.md) to commit only the selected issue's work.

### 6. Closeout using common/closeout.md

Use [common/closeout.md](common/closeout.md).
