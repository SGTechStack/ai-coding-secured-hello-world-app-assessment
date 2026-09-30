# Default Do Work

## Workflow

Use your task/todo list tool to track each numbered step as a task. MUST follow every step exactly; do NOT try to be more efficient. Complete each task only after executing that numbered step; do not batch-complete steps.

### 1. Implement

Use the AWS Bedrock KB retrieval and detailed KB evidence logging values resolved in [SKILL.md](SKILL.md).

1. Read `docs/agents/reviewer-decisions.md` if it exists. Apply only decisions relevant to the current issue, touched code, or KB guidance. These decisions constrain implementation approach; they are not new feature scope.
2. Use [common/prepare-work.md](common/prepare-work.md) to select or resolve the work item and pass the before-coding gates. Do not edit code until the gates pass.
3. Use [implementer/rag.md](implementer/rag.md) if AWS Bedrock KB retrieval is available; choose KB lanes from concrete files/symbols and make all likely useful queries.
4. Implement the selected issue.
5. Write or skip [implementer/log.md](implementer/log.md) according to the resolved KB log mode.
6. Review changed files against the issue summary, acceptance criteria covered, and test seams used. Use [reviewer/rag.md](reviewer/rag.md) from changed files only if AWS Bedrock KB retrieval is available; choose KB lanes from concrete files/symbols and make all likely useful fresh queries.
7. Write or update [reviewer/log.md](reviewer/log.md) only when the reviewer returns findings, human decisions, or KB log mode is on.
8. For `Human Decision Needed`, use [common/decision-memory.md](common/decision-memory.md). Do not choose for the user.
9. If reviewer returns `Must-fix > 0` or a chosen human decision needs implementation, bundle reviewer must-fixes and chosen human decisions into one correction pass.
10. Resolve every `Must-fix`, applying relevant decisions from `docs/agents/reviewer-decisions.md` if it exists.
11. Rerun the affected reviewer.
12. Continue only when `Must-fix=0`, `Human decisions=0`, and required logs return `Log appended: yes`; non-required logs may return `Log appended: skipped`.

For backend code or frontend logic, hooks, API clients, and utils, use [common/tdd.md](common/tdd.md).

For setup code, component rendering, config, and integration wiring: implement directly.

If setup creates dependencies, build outputs, or generated scaffolding, create or update `.gitignore` before staging or committing.

### 2. Validate

Final gate after the reviewer loop is clean.

Run [../code-reviewer/SKILL.md](../code-reviewer/SKILL.md) yourself without subagents.


### 3. Compliance Report

Write/update the compliance report required by [../code-reviewer/SKILL.md](../code-reviewer/SKILL.md) only from the immediately preceding complete final-gate Step 2 batch. If any fix or code change happens after Step 2, rerun the full Step 2 batch before writing/updating the report.

Continue only if code-reviewer has no `FAIL` checks; `WARN` does not block.
If blocked, fix it, then rerun the affected review and Steps 2-3. Stop after two failed fix rounds for the same blocker.

### 4. Mutation gate

Optionally run [../mutation-testing/SKILL.md](../mutation-testing/SKILL.md) directly. Fix Critical or High survivors and rerun mutation testing within this step until none remain or progress is blocked; do not rerun earlier workflow steps.

### 5. Commit

**Ignore hygiene** — Follow [common/gitignore.md](common/gitignore.md).

Once all checks pass, use [../git-commit-skill/SKILL.md](../git-commit-skill/SKILL.md) to commit only the selected issue's work.

### 6. Closeout using common/closeout.md

Use [common/closeout.md](common/closeout.md).
