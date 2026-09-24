# Reviewer RAG

Use after changes when AWS Bedrock KB retrieval is available.

Use [../../query-kb/SKILL.md](../../query-kb/SKILL.md) to execute each AWS Bedrock KB query. Do not satisfy this step with Kiro native KB search, web search, local file search, a failing MCP query, implementer results, or an unexecuted query plan.

Build fresh queries from written code/config using [../common/kb-router.md](../common/kb-router.md). Do not reuse implementer queries.
Make all likely useful queries for lanes with concrete file/symbol evidence. Cover all changed code/config with likely useful queries. Do not query unused candidate lanes. If no useful KB lane applies to a changed file, say so.

Existing code patterns do not waive KB-backed violations.

Any KB-backed deviation must be `Must-fix` or `Human Decision Needed`.

Before returning `Human Decision Needed`, check `docs/agents/reviewer-decisions.md` for a matching prior decision. If a matching prior decision exists, apply it and do not ask again. If no matching decision exists, return the `Human Decision Needed` prompt to the parent.

If [../../query-kb/SKILL.md](../../query-kb/SKILL.md) fails, return `Status: blocked` with the command and error.

Use [log.md](log.md) only when there are findings, human decisions, or KB log mode is on.
