## Findings

| Finding | Required action | Status |
| --- | --- | --- |
| KB unavailable. Acceptance criterion 4 says each Session that expires emits session-end. A Session is audited only when it is presented again after its limit or ended by a new login. A Session that is abandoned is deleted silently by Spring Session JDBC cleanup (JDBC fires no expiry events), with no session-end event. Also, an abandoned Session later removed by a new login is audited as new_login although it had already passed its idle limit.<br><br><details><summary><strong>Verify in</strong></summary><br><code>SessionControl.java (enforceLifetime) and application.properties (spring.session.timeout)</code></details> | Human decision: accept presentation-time auditing (document it in the issue), or add a scheduled sweep that audits and deletes authenticated Sessions past their limits before Spring Session cleanup, or at least compute the real reason (idle/absolute) when endOldestBeyondLimit/endAll remove an already-expired Session. | Resolved |


## Human decisions

| Area | Decision | Chosen action | Reuse when |
| --- | --- | --- | --- |
| backend session-end auditing | Abandoned expired Sessions are deleted by Spring Session JDBC cleanup with no session-end event; already-expired Sessions removed by a later login are recorded as new_login. | Do not follow KB recommendation: no KB available; chose option C + A. endOldestBeyondLimit and endAll record idle_timeout/absolute_timeout when the removed Session had already expired; the remaining gap for untouched abandoned Sessions is accepted and noted in the issue. No scheduled sweep. | Audit-completeness questions for records a store expires in the background: record the true reason wherever the app touches them and document the untouched-expiry gap. |


## Full do-work log

Open locally: `artifacts/do-work/05-session-lifetime-rules.html`

Includes reviewer findings and human decisions.
