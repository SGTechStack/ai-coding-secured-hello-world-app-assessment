# Closeout

1. Ensure all boxes in the relevant checklist are ticked for the work completed.
2. Post a verification comment to the relevant issue using `docs/agents/issue-tracker.md`. If no issue number is available, include a clear `Verification` summary in final output.
3. The verification must summarize:
   - What was implemented or fixed
   - Verification steps performed
   - Confirmation that checklist boxes are ticked
4. If an HTML log was generated and its path was not already posted in the reviewer log comment, the verification must also include:
   - `Local do-work HTML log: <repo-relative-html-path>` using the exact HTML log path returned by the log helper
   - `Contents: reviewer findings and human decisions.` when KB log mode is off
   - `Contents: implementation KB queries and guidance, full reviewer code comparisons, findings, and human decisions.` when KB log mode is on
5. When an issue number is available, close the issue only after the verification comment succeeds.
6. STOP. Do not start another issue.
