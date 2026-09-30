# Implementer Log

Use when KB log mode is on. Maintain exactly one implementer evidence log per issue.

Do not create an implementer evidence log unless routed here.

Use [../common/log-state.md](../common/log-state.md).

Log location:

- Use the issue reference passed by the parent workflow.
- Add one entry to `implementer.updates` with `round` set to the current workflow round.
- Render the log. Do not post a separate implementer comment; the reviewer Markdown includes the implementer evidence in its collapsed round history.
- If no issue number is known, include the entry in final output.
- Gate: never create a second implementer evidence log for the same issue.
- Gate: if an issue number is known, final-output-only logging is `Log appended: no`.
- Return `Log location: local HTML` when rendering succeeds.
- Return `HTML log` with the exact repo-relative `.html` path printed by the renderer.

Return:

```text
RAG skill used: <implementer/rag.md or none>
KB queries: <count>
Detailed KB evidence logging: on | off
Log skill used: <implementer/log.md or none>
Status: completed | blocked
KB lanes queried: <lanes or none>
Changed files: <paths or none>
Log appended: yes | skipped
Log location: <local HTML or final output>
Log state: <artifacts/do-work/json/<issue-number>-<issue-slug>.json or none>
HTML log: <artifacts/do-work/<issue-number>-<issue-slug>.html or none>
```

Gate: include evidence rows for every KB lane queried when detailed KB evidence logging is active.

Gate: every `implementer.updates[].evidence[]` row should include `files` with the narrowest useful paths where the logged implementation exists. Use file paths or `file:line` when the line is known. Do not rely only on `changed_files`; that is the broad update list, not row-level evidence.
