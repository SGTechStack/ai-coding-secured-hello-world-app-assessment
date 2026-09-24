# 11: Admin user list, end to end

**What to build:** An admin sees an "Admin" link in the navbar and a page listing every user with username, email, role, status and created date. Non-admins never see the link and are sent away from the page, and the server enforces the same rule. Profile fields that look like HTML are shown as plain text. See spec §Backend modules › Admin module, §Frontend modules, and Acceptance scenarios › Story 7.

**Blocked by:** 04, 10

**Status:** ready-for-agent

- [ ] `GET /api/v1/admin/users` → `200` with a list of `{id, username, email, firstName, role, enabled, createdAt}` sorted by `createdAt`. It never includes the password hash, lockout fields or tokens.
- [ ] `/api/v1/admin/**` requires `ROLE_ADMIN` in the filter chain, and the admin service enforces it at method level too. A `USER` gets `403 FORBIDDEN`. Anonymous gets `401`.
- [ ] Admin query options for TanStack Query exist for the list.
- [ ] The navbar shows "Admin" next to Log out only when the cached profile's role is `ADMIN`.
- [ ] The `/admin/users` route redirects to `/login` without a session, and to `/` for a non-admin.
- [ ] The page renders a semantic `<table>` with a caption and column headers. The controls on the admin's own row are disabled, with an explanation. A load failure shows `ErrorAlert`.
- [ ] Frontend tests: the admin sees the table, the navbar link shows only for admins, a non-admin at `/admin/users` ends on `/`, and a `firstName` of `<img src=x onerror=alert(1)>` renders as literal text with no `img`.
- [ ] e2e Story 7 scenarios 1, 5, 6 and 7 pass under the CSP fixture, signing in as the bootstrap admin.
