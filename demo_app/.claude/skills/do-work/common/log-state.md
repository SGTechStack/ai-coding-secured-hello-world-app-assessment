# Do Work Log State

Use this when writing or updating implementer/reviewer logs.

Source of truth:

`artifacts/do-work/json/<issue-number>-<issue-slug>.json`

Edit the JSON only, then render:

```bash
python3 <do-work-skill-dir>/common/render-do-work-log.py artifacts/do-work/json/<issue-number>-<issue-slug>.json
```

Keep the exact repo-relative `.html` path printed by the renderer as the HTML log path. Post or edit GitHub comments from the rendered Markdown in `artifacts/do-work/md/`. The HTML report is local. When detailed KB evidence logging is on, the Markdown also includes the KB queries, guidance, and code comparisons in collapsed sections grouped by workflow round.

## Completion gate

- When a log is required, verify existence only; do not read its contents. Confirm the JSON state, the rendered Markdown when produced, and the local HTML render exist, and that the required issue post or edit succeeded. Return `Log appended: yes` and `HTML log: <repo-relative-html-path>` only when all checks pass; otherwise return `Status: blocked`.
- When KB log mode is on, the reviewer Markdown and issue comment are required even when there are no findings or human decisions.
- When no log is required, return `Log appended: skipped` and `HTML log: none`.
- If detailed KB evidence logging was requested but AWS Bedrock KB retrieval is unavailable, return `Log appended: skipped` with reason `AWS Bedrock KB retrieval unavailable`.

## Write ownership

- Implementer: append only `implementer.updates[]`.
- Reviewer: append `reviewer.updates[]`; write `findings[]` and `comparisons[]`.
- Main agent: add new choices only to the originating update's `decisions[]`, using the finding's `evidence_id`.

## Shape

```json
{
  "issue": {
    "reference": "issue tracker reference",
    "slug": "short-stable-slug",
    "goal": "short goal"
  },
  "detailed_kb_evidence": false,
  "implementer": {
    "updates": [
      {
        "round": 1,
        "kb_lanes": ["spring-boot"],
        "status": "completed",
        "changed_files": ["path"],
        "kb_queries": 1,
        "evidence": [
          {
            "task": "what changed",
            "files": ["path or file:line"],
            "kb_lanes": ["spring-boot"],
            "query": "query text",
            "quote": "selected KB quote",
            "application": "how code follows it"
          }
        ]
      }
    ]
  },
  "reviewer": {
    "updates": [
      {
        "round": 1,
        "kb_lanes": ["spring-boot"],
        "status": "completed",
        "changed_files": ["path"],
        "kb_queries": 1,
        "findings": [
          {
            "evidence_id": "same id as comparison when present",
            "location": "file:line or concern",
            "kb_lane": "spring-boot",
            "query": "query text",
            "recommendation": "selected KB recommendation",
            "violation": "what is wrong",
            "proposed": "fix or decision needed",
            "status": "Must-fix | Human Decision Needed | Resolved"
          }
        ],
        "decisions": [
          {
            "evidence_id": "same id as originating finding",
            "area": "backend",
            "kb_finding": "decision trigger",
            "chosen_action": "Follow KB recommendation: ... | Do not follow KB recommendation: ...",
            "reuse": "when to reuse"
          }
        ],
        "comparisons": [
          {
            "evidence_id": "same id as finding when present",
            "concern": "file or concern",
            "kb_lane": "spring-boot",
            "query": "query text",
            "recommendation": "selected KB recommendation",
            "result": "Compliant: ... | Deviation: ... | Blocked: ..."
          }
        ]
      }
    ]
  }
}
```

## Rules

- Treat the JSON shape above as closed: use only the documented fields at each level. Do not add summaries, validation results, acceptance criteria, test seams, controlled-deviation objects, or other fields. The renderer rejects unknown fields; keep that information in the role return or existing workflow artifacts instead.
- Create the JSON on first log; after that append one update per role pass.
- Set `round` on each update.
- Findings are the human-facing action list.
- Comparisons are optional KB evidence rows.
- Link a finding to its comparison with the same `evidence_id`.
- To close a finding, update the original finding to `Resolved`; do not add a duplicate resolved finding.

Allowed finding `status` values:

- `Must-fix`
- `Human Decision Needed`
- `Resolved`

Allowed comparison `result` prefixes:

- `Compliant:`
- `Deviation:`
- `Accepted deviation:`
- `Blocked:`

Every decision `chosen_action` must start with `Follow KB recommendation:` or `Do not follow KB recommendation:`.
