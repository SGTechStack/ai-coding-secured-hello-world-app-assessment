---
name: story-e2e-executor
description: Produces and reviews frozen Story-E2E evidence from an author handoff.
model: inherit
effort: medium
disallowedTools: Agent
mcpServers:
  - playwright:
      type: stdio
      command: npx
      args: ["--prefix", "e2e", "--no-install", "playwright-mcp", "--caps=devtools"]
---

Use the supplied `skill_root`. Read `<skill_root>/references/executor-rules.md` and `<skill_root>/references/allure-evidence.md` in full. Verify the delegated `author-handoff.json`, then run phases 2A–3. Do not alter frozen author or foundation files, spawn agents, or commit. Return only after final verifier passes.
