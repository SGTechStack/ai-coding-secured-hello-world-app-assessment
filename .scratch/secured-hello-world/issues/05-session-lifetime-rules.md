# 05: Session lifetime rules

**What to build:** A Session that sits idle for 15 minutes, or lasts 8 hours in total, stops working. A failed login ends any Session the browser already carried. When that happens, the SPA quietly takes the Account holder to the login page. Session control can end every Session for a given Account, which later tickets use for Password Change, reset and admin actions. Sessions and their limits survive a restart. See the spec's stories 19–22, "Session control", and the Clock test seam.

**Blocked by:** 04

**Status:** ready-for-agent

- [ ] Idle timeout is 15 minutes and absolute timeout is 8 hours, both configurable. A filter records each Session's start time and ends it after the absolute limit, reading the injected `Clock`.
- [ ] A failed login invalidates any Session the request carried.
- [ ] Session control offers "end every Session for Account X" through Spring Session's lookup-by-principal, for later tickets to call.
- [ ] Each Session that expires, or that the system ends (second login, or later a Password Change, reset or admin action), emits a `session-end` audit event.
- [ ] The SPA's global handler treats any 401 as "Session ended": it clears auth state and redirects to login without showing an error.
- [ ] Tests (Clock seam) cover: an idle Session rejected after 15 minutes; a busy Session rejected after 8 hours; a failed login ending the carried Session; the `session-end` audit event; and a Session still valid after restarting the application context on the same file database.
