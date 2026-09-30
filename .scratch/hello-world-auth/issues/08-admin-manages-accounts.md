# 08: Admin enables, disables, changes Role and deletes

**What to build:** An admin can suspend an Account, change its Role, or delete it. Disabling or deleting takes effect at once: the Account's sessions end and further sign-ins are refused. An admin cannot disable, demote or delete their own Account.

**Blocked by:** 05 Admin creates and lists Accounts

**Status:** ready-for-agent

- [x] Enable/disable endpoint updates the enabled flag; a disabled Account's sessions end and sign-in returns the generic error
- [x] Role-change endpoint accepts valid Roles only (`USER`, `USER_MANAGER`, `ADMIN`)
- [x] Delete endpoint removes the Account and its sessions
- [x] Disabling, demoting or deleting the admin's own Account is refused
- [x] 403 for non-admins, 401 when unauthenticated
- [x] Audit lines for enable, disable, Role change and delete with actor and target
- [x] SPA actions on the Account list; frontend tests cover them
- [x] Integration tests cover each action, session ending on disable/delete and every self-action refusal
- [x] Admin OpenAPI spec and generated client regenerated
- [x] Backend and frontend verify pass
