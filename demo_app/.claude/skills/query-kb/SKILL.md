---
name: query-kb
description: Query AWS Bedrock KB. Use when a user supplies a KB query, or another skill needs KB retrieval.
argument-hint: "QUERY [--id KB_ID | --tag KEY=VALUE]"
---

# Query KB

Arguments:

```text
$ARGUMENTS
```

Use `$ARGUMENTS` as the command arguments. If `$ARGUMENTS` is empty but the caller provided query text, use that query text as the arguments. If neither exists, return:

```text
Usage: /query-kb QUERY
Usage: /query-kb QUERY --id KB_ID
Usage: /query-kb QUERY --tag KEY=VALUE
```

Examples:

```text
/query-kb React useEffect cleanup
/query-kb React useEffect cleanup --id ABCDE12345
/query-kb React useEffect cleanup --tag nonchunk-kb=true
```

Put the query first. Put `--id` or `--tag` last; everything after it is treated as the KB id or tag.

## Availability check

When another skill needs to verify KB retrieval, use `/query-kb --check`. Check mode uses a built-in neutral query.

Resolve this skill directory, then run from the repo root:

```bash
uv run <query-kb-skill-dir>/tools/query-bedrock-kb.py $ARGUMENTS
```

Return the command output.
