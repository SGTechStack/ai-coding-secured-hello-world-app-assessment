# Implementer RAG

Use before implementation when AWS Bedrock KB retrieval is available.

Use [../../query-kb/SKILL.md](../../query-kb/SKILL.md) to execute each AWS Bedrock KB query. Do not satisfy this step with Kiro native KB search, web search, local file search, a failing MCP query, or an unexecuted query plan.

Build queries from expected code/config using [../common/kb-router.md](../common/kb-router.md).

Make all likely useful queries for lanes with concrete file/symbol evidence. Do not query unused candidate lanes. If no useful KB lane applies to planned changes, say so.

If [../../query-kb/SKILL.md](../../query-kb/SKILL.md) fails, return `Status: blocked` with the command and error.

Use [log.md](log.md) only when KB log mode is on.
