# 13: Required Password Change

**What to build:** The Bootstrap Admin, and any Account an Admin suspects is compromised, can do nothing except view itself, make a Password Change, or log out until the password is changed. When an Admin requires a password change, that Account's Sessions end at once. After login, the SPA takes the holder straight to Password Change. A Password Change or a completed reset clears the requirement. See the spec's stories 106–110, "Required password change", "Account administration service", "API contract", ADR 0001 (IM8 as-15 and ac-6, no grace-period disablement), and `CONTEXT.md`'s Required Password Change.

**Blocked by:** 09, 11

**Status:** ready-for-agent

- [ ] A filter placed after authentication returns 403 `password_change_required` for every authenticated request except `GET /me`, `PATCH /me/password`, `POST /logout` and `GET /csrf`, while the Account's `password_change_required` is set.
- [ ] `GET /me` reports `passwordChangeRequired: true` while the flag is set and `false` once it is cleared (the field itself exists since ticket 04).
- [ ] `POST /api/admin/users/{id}/require-password-change` (Admin only) returns 200, sets the flag, and ends the target's Sessions. An unknown id returns 404; a User gets 403.
- [ ] A successful Password Change or reset confirmation clears the flag.
- [ ] Audit events (`password-change-enforcement`): flag set (INFO, with the acting Admin and target), flag cleared (INFO), and a request refused because of the flag (WARN).
- [ ] SPA: when `/me` reports the flag, or a request returns 403 `password_change_required`, only the Password Change screen and logout are available. The admin screen has a "Require password change" action.
- [ ] Test fixtures complete the Bootstrap Admin's required change, except in tests of this behaviour.
- [ ] Tests cover: the Bootstrap Admin's first login getting 403 on `/hello` and `/admin/users` while `/me`, `/me/password`, `/logout` and `/csrf` work; a Password Change clearing the flag; an Admin requiring a change on another Account, which ends its Sessions and then blocks it the same way until it changes its password; a reset clearing the flag; a User calling the action getting 403; and the audit events.
