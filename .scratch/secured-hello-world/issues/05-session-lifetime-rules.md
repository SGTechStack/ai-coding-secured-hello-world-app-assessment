# 05: Session lifetime rules

**What to build:** A Session that sits idle for 15 minutes, or lasts 8 hours in total, stops working. A failed login ends any Session the browser already carried. When that happens, the SPA quietly takes the Account holder to the login page. Session control can end every Session for a given Account, which later tickets use for Password Change, reset and admin actions. Sessions and their limits survive a restart. See the spec's stories 19–22, "Session control", and the Clock test seam.

**Blocked by:** 04

**Status:** resolved

- [x] Idle timeout is 15 minutes and absolute timeout is 8 hours, both configurable. A filter records each Session's start time and ends it after the absolute limit, reading the injected `Clock`.
- [x] A failed login invalidates any Session the request carried.
- [x] Session control offers "end every Session for Account X" through Spring Session's lookup-by-principal, for later tickets to call.
- [x] Each Session that expires, or that the system ends (second login, or later a Password Change, reset or admin action), emits a `session-end` audit event.
- [x] The SPA's global handler treats any 401 as "Session ended": it clears auth state and redirects to login without showing an error.
- [x] Tests (Clock seam) cover: an idle Session rejected after 15 minutes; a busy Session rejected after 8 hours; a failed login ending the carried Session; the `session-end` audit event; and a Session still valid after restarting the application context on the same file database.

## Comments

<!-- reviewer-log -->
### Reviewer log

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

<!-- /reviewer-log -->

**Note (accepted gap, see `docs/agents/reviewer-decisions.md` "Session-end audit for Sessions that expire unused"):** a `session-end` event is written wherever the app touches an expired Session: the next request on it, a later login, or `endAll`. A Session that has already passed its idle or absolute limit is recorded as `idle_timeout` / `absolute_timeout`, not as the caller's reason. Abandoned Sessions that nothing touches again are deleted by Spring Session's cleanup job with no `session-end` event. A scheduled sweep could be a follow-up issue.

### Verification (2026-09-29)

**Implemented:** A new `SessionLifetimeFilter` records each logged-in Session's start time and last use from the injected `Clock`, and ends it after 15 minutes idle or 8 hours total (`app.session.idle-timeout`, `app.session.absolute-timeout`). Spring Session stores Visitor CSRF-only sessions for the idle timeout only; logged-in Sessions are stored for the absolute timeout. A failed login ends the logged-in Session the request carried; a Visitor's CSRF-only session is kept. `SessionControl.endAll(accountId, reason, …)` ends every Session for an Account through Spring Session's lookup-by-principal and deletes the stored rows. Each ended Session writes an INFO `session-end` audit event with `event.reason` `idle_timeout`, `absolute_timeout`, `new_login` or `login_failed`. The startup log reports both timeouts. The SPA's global 401 "Session ended" handling from issue 04 already met criterion 5, so the frontend is unchanged.

**Deviations and decisions:** Abandoned Sessions that nothing touches again get no `session-end` event (accepted gap, see the note above). The `endAll` tests call the `SessionControl` bean directly, because no HTTP endpoint uses it until issue 08. One test reads `spring_session.max_inactive_interval` directly. Both go beyond the spec's HTTP-only testing rule. The `endAll` test for a request carrying the Session uses a `MockHttpSession`, so the real Spring Session path is first exercised end to end by issue 08.

**Verification steps:** `./mvnw verify` passed: 137 tests, 0 failures, JaCoCo gate met. Frontend: 64 tests passed. Reviewer loop clean after one correction pass. The pass fixed Visitor sessions being stored for 8 hours and added `endAll` tests, which found and fixed a stored row surviving `endAll`. It also recorded the real timeout reason for already-expired Sessions. KB retrieval and code-reviewer compliance gates were skipped by request; mutation testing was skipped.

**Checklist:** all acceptance-criteria boxes ticked.

Commit: `d561afc feat(session): Enforce idle and absolute Session timeouts`
