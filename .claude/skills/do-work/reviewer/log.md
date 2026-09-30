# Reviewer Log

Use when routed by the reviewer workflow. Maintain exactly one reviewer log per issue.

Default logging records only reviewer findings and human decisions. KB log mode adds KB queries, guidance, and code comparisons in collapsed sections grouped by workflow round.

Use [../common/log-state.md](../common/log-state.md).

Log location:

- Use the issue reference passed by the parent workflow.
- Add one entry to `reviewer.updates` with `round` set to the current workflow round.
- Render the log.
- Use `artifacts/do-work/md/<issue-number>-<issue-slug>-reviewer.md` as the issue comment body.
- Read `docs/agents/issue-tracker.md`. Edit the existing reviewer log comment on rerun; create it on first run.
- If editing fails or is unavailable, append one update comment that links to the existing log.
- If no issue number is known, include the entry in final output.
- Gate: never create a second reviewer log for the same issue.
- Gate: if an issue number is known, final-output-only logging is `Log appended: no`.
- Return `Log location` with the issue number and comment ID/URL when available.
- Return `HTML log` with the exact repo-relative `.html` path printed by the renderer.

Return:

```text
RAG skill used: <reviewer/rag.md or none>
KB queries: <count>
Detailed KB evidence logging: on | off
Log skill used: <reviewer/log.md or none>
Status: completed | blocked
KB lanes queried: <lanes or none>
Must-fix: <count>
Human decisions: <count>
Decision prompts: <all Human Decision Needed blocks or none>
Main-agent reminder: <read common/decision-memory.md, then use the grilling skill for unresolved decisions | none>
Changed files: <paths or none>
Log appended: yes | skipped
Log location: <issue tracker reference and comment ID/URL, or final output>
Log state: <artifacts/do-work/json/<issue-number>-<issue-slug>.json or none>
HTML log: <artifacts/do-work/<issue-number>-<issue-slug>.html or none>
```

Gate: only log findings backed by exact KB guidance.

Gate: any KB-backed deviation must be `Must-fix` or have a human decision. Do not log KB deviations as informational, low severity, or future work.

If KB log mode is on, also add KB comparison rows to `reviewer.updates[].comparisons`. These rows appear in the local HTML report and in the GitHub Markdown comment's collapsed round history.

Gate: when a comparison row supports a finding, give both rows the same `evidence_id`. The finding is the human summary/action; the comparison is the detailed KB evidence. Do not write them as two separate conclusions.

Gate: if a rerun confirms an existing finding is closed, update the original finding with the matching `evidence_id` to `status: Resolved`; do not add a second resolved finding to the rerun update.

Gate: rerun updates should contain only new unresolved findings. Put fixed-finding verification in `comparisons` as `Compliant:` rows with the same `evidence_id`.

When a human chooses not to follow the KB, use `Accepted deviation:` for the linked comparison while keeping the comparison message unchanged.

Example:

```json
{
  "findings": [
    {
      "evidence_id": "dto-entity-exposure",
      "location": "TodoController.java",
      "violation": "Controller returns the JPA entity directly.",
      "proposed": "Add DTOs or ask the user to accept MVP exposure.",
      "status": "Human Decision Needed"
    }
  ],
  "comparisons": [
    {
      "evidence_id": "dto-entity-exposure",
      "concern": "TodoController.java request/response types",
      "result": "Deviation: API exposes entity instead of DTO."
    }
  ]
}
```

If there is no human decision, leave `decisions` empty.

If there is no finding, no human decision, and KB log mode is off, do not create a log; return `Log appended: skipped`.
