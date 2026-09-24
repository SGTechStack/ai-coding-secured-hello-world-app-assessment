# 11 — Decide the admin module, role model, and initial admin bootstrap

Type: grilling
Status: open
Blocked by: 01, 04, 05, 06, 19

## Question

What does the admin surface look like, how are roles defined and assigned, and how does the very
first admin account come into existence safely?

Bootstrap is merged in here because it is a role-assignment question wearing a different hat.

## The conflict to resolve first

The PRD (Story 10) gives admins an endpoint to change another user's role between USER and ADMIN.
The App Standard says role **definitions** are configuration-driven, immutable via API, loaded at
startup with fail-fast on duplicates, and that "users cannot assign themselves or others to roles
through the application" — while also saying "role assignments must be configured and managed by
role administrators only."

Those two sentences can be read as flatly prohibiting Story 10, or as permitting admin-performed
assignment while prohibiting self-assignment. Resolve the reading explicitly. Check
`Common_Role-Based_Access_Control_Configuration.md` via "Inventory the prescribed recipes" — it
probably settles this and the file format.

## What to decide

**Role model.**

- Role *definitions* in a config file (name → privileges) vs the PRD's plain `USER`/`ADMIN` enum.
- The **role-based authorization matrix** — role → allowed paths and HTTP methods, loaded at startup.
  This is a whole mechanism the PRD never mentions. Decide whether we adopt it or defer it with
  justification, given the PRD scopes authorization to a USER/ADMIN check on admin endpoints. If
  adopted, decide the file format and how it composes with `@PreAuthorize`.
- Whether role changes are read from config, database, or both, and what "synchronise roles at
  startup" means for us.

**Admin endpoints.** For each of PRD Stories 8–11, decide path, method, request/response shape, and
authorization:

- List users — must never return password hashes. Decide the exact field set and whether emails are
  returned to admins (PII minimisation cuts against it; the PRD asks for it).
- Enable/disable — dedicated endpoint, not the generic update. The standard requires sensitive
  operations to use dedicated endpoints and forbids setting a lock field through generic update.
- Change role — subject to the conflict resolution above.
- Delete — see soft-delete below.
- Unlock — required by the standard, absent from the PRD. Decide whether to add it (it is the only
  way to lift a lockout early, and the standard mandates it).
- The **self-action guard**: admins cannot disable, demote, or delete themselves (PRD Stories 9–11)
  and cannot unlock themselves (standard). Decide where this lives so it cannot be bypassed by
  adding a new endpoint later — one guard, centrally applied, not four copies.

**Soft delete.** The standard requires deleted users retained as tombstones, and requires user
creation to be rejected when the username exists in a tombstone. The PRD says the account is
"removed". Decide the tombstone's fields and retention, and what a tombstone does to email reuse as
well as username reuse. Note the tension with data-minimisation and any IM8 retention control from
"Extract the IM8 and ARC controls".

**Self-read endpoint.** The standard mandates a dedicated endpoint returning only the caller's own
record; `Common_Secure_Self-Read_User_Endpoint.md` likely prescribes it. This is also what the SPA
needs to discover auth state behind an HttpOnly cookie, so its shape is consumed by "Design the
frontend architecture". Decide the field set — it should differ from the admin view.

**Initial admin bootstrap (PRD Story 12).**

- Credential source: the PRD suggests `app.admin.username` / `app.admin.password`. Decide whether a
  plaintext password in a properties file is acceptable (it is not, for anything but local dev) and
  what replaces it — environment variable, fail-fast when unset, refusal to start with a weak or
  default value.
- Whether the seeded admin is flagged for mandatory password change on first login. The standard
  requires this for admin-created users and on re-enable; the same logic applies here, and it closes
  the "shipped with a known bootstrap password" hole.
- Idempotency: seed only when no `ADMIN` exists (PRD Story 12), and decide what happens if an admin
  exists but is disabled or soft-deleted.
- Whether the seed is profile-gated. The standard says development-only seed accounts must not be
  present in production — decide whether the bootstrap admin is a dev seed or a production
  necessity, because they need different treatment.

## Done when

The Story 10 conflict has a written resolution, every admin endpoint is specified with its guard,
the tombstone model is decided, and the bootstrap cannot ship with a known password.
