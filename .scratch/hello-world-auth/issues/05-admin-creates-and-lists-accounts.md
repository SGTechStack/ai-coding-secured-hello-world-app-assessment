# 05: Admin creates and lists Accounts

**What to build:** A signed-in admin creates an Account with a unique username and a Role and receives a server-generated Temporary Password once, to hand to the person. The admin can list all Accounts. Endpoints live on the template's separate admin chain (`/admin/api/**`, requires `ADMIN`) with its own OpenAPI spec and generated client. The SPA has a create-account page and an Account list.

**Blocked by:** 03 Sign in, greeting, sign out

**Status:** ready-for-agent

- [x] Create returns the Temporary Password exactly once; only its hash is stored; must-change-password and expiry (default 24 h, a property) are set; the Account is enabled
- [x] Duplicate username is refused with a conflict
- [x] List shows username, Role, enabled and created date; never hashes, Temporary Passwords or delay fields
- [x] Every admin endpoint returns 403 for a non-admin and 401 when unauthenticated
- [x] Audit line for Account created, with actor and target and no password
- [x] Admin OpenAPI spec and generated admin client are regenerated as documented
- [x] SPA create-account page and list; frontend tests cover them
- [x] Integration tests cover creation, duplicate, listing fields and 403 for non-admins
- [x] Backend and frontend verify pass
