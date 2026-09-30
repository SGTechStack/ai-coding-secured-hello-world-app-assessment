## Findings

| Finding | Required action | Status |
| --- | --- | --- |
| KB skipped by request. Reviewer finding. Criterion 4 assigns two questions to triage (does a 403 from GET /api/csrf get session-end treatment; should the thrown Error remain once listeners fire). The implementer answered both itself and recorded the answer in reviewer-decisions.md, whose header defines it as human decisions that later reviewers apply without asking. No prior human decision covers a 403 from /csrf. The code choice (403 and 5xx reject; 401 resolves to a 401 result) is technically sound and tested, but it is not yet a human decision.<br><br><details><summary><strong>Verify in</strong></summary><br><code>19-csrf-refresh-401-session-end.md (Triage decision)</code><br><code>reviewer-decisions.md (&#x27;A 401 from the CSRF bootstrap is a session end...&#x27;)</code></details> | Human confirms the triage (keep as is), or picks another option; if confirmed, keep the reviewer-decisions.md entry, otherwise revise or remove it. | Resolved |


## Human decisions

| Area | Decision | Chosen action | Reuse when |
| --- | --- | --- | --- |
| frontend/src/api/client.ts CSRF bootstrap (issue 19 criterion 4) | Implementer self-decided criterion-4 triage and recorded it in reviewer-decisions.md without human approval | Do not follow KB recommendation: no KB (skipped). Human confirmed option A: only 401 from /csrf is a session end; 403/5xx stay rejected Errors; keep reviewer-decisions.md entry, marked human-confirmed. | Any supporting request that can signal session end: map only the session-end status, notify once, keep other failures visible errors. |


## Full do-work log

Open locally: `artifacts/do-work/19-csrf-refresh-401-session-end.html`

Includes reviewer findings and human decisions.
