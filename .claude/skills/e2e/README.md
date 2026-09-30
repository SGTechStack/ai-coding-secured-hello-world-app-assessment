# Story E2E

Turn documented user stories into Playwright browser tests with fresh Allure,
video, and trace evidence.

## Install

Install the skill with your host's skill installer. It needs fresh subagents
and a project-local Playwright browser tool. Where the host supports named
agents, install its Story-E2E adapter; otherwise the skill uses a fresh generic
subagent with the relevant role rules and handoff.

### Claude Code

From the target project root:

```powershell
npx skills add SGTechStack/ai-coding-workflow-d2 --skill e2e --agent claude-code --copy --yes
```

If Node cannot validate your organisation's proxy certificate, prefix the
command with `$env:NODE_OPTIONS='--use-system-ca';`.

## Run

Invoke your host's `e2e` skill from the project root. In Claude Code, type:

```text
/e2e
```

Before starting, choose the same-tier 1M variant if your host offers one and
set medium effort. Story-E2E roles inherit the selected model.

On first use it sets up and verifies the project's E2E foundation. Later runs
offer the next documented user story or feature. Generated evidence is kept in
`artifacts/e2e/`; runnable tests and shared test support are kept in `e2e/`.
