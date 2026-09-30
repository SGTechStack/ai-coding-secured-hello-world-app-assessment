# 05: Admin creates and lists Accounts

**What to build:** A signed-in admin creates an Account with a unique username and a Role and receives a server-generated Temporary Password once, to hand to the person. The admin can list all Accounts. Endpoints live on the template's separate admin chain (`/admin/api/**`, requires `ADMIN`) with its own OpenAPI spec and generated client. The SPA has a create-account page and an Account list.

**Blocked by:** 03 Sign in, greeting, sign out

**Status:** ready-for-agent

- [ ] Create returns the Temporary Password exactly once; only its hash is stored; must-change-password and expiry (default 24 h, a property) are set; the Account is enabled
- [ ] Duplicate username is refused with a conflict
- [ ] List shows username, Role, enabled and created date; never hashes, Temporary Passwords or delay fields
- [ ] Every admin endpoint returns 403 for a non-admin and 401 when unauthenticated
- [ ] Audit line for Account created, with actor and target and no password
- [ ] Admin OpenAPI spec and generated admin client are regenerated as documented
- [ ] SPA create-account page and list; frontend tests cover them
- [ ] Integration tests cover creation, duplicate, listing fields and 403 for non-admins
- [ ] Backend and frontend verify pass
