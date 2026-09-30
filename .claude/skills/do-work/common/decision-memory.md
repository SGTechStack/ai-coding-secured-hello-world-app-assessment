# Decision Memory

Use in the main agent with the `grilling` skill for repeated reviewer decisions across issues.

Location: `docs/agents/reviewer-decisions.md`

If the file is missing, create it when the first decision is recorded.

Before asking the user about `Human Decision Needed`, check this file for the same KB-backed decision.

If a matching decision exists, apply it and do not ask again.

## Human review

For a new unresolved human decision returned by the reviewer, the main agent MUST invoke the [grilling skill](../../grilling/SKILL.md) with the reviewer-provided decision context to ask 1 question at a time.

After the user answers a new decision, append or update one concise entry with:

- Area
- KB finding
- Chosen action
- When to reuse it

Before updating the current do-work log JSON, read [log-state.md](log-state.md). Record the chosen decision in `reviewer.updates[].decisions` using these keys:

```json
{
  "evidence_id": "same id as originating finding",
  "area": "affected area",
  "kb_finding": "decision trigger",
  "chosen_action": "Follow KB recommendation: ... | Do not follow KB recommendation: ...",
  "reuse": "when to reuse"
}
```

Start `chosen_action` with exactly `Follow KB recommendation:` or `Do not follow KB recommendation:` so the log makes the decision's relationship to the KB explicit.

If the choice accepts a deviation without implementation, set the originating finding to `Resolved` and its linked comparison to `Accepted deviation:`.

Then follow [log-state.md](log-state.md) to rerender and edit the existing GitHub log comment, then return to the workflow.
