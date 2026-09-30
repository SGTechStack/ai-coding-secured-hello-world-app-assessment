# The bootstrap admin is created in every profile

The standard says development-only seed accounts must never exist in production. That rule targets test fixtures. PRD Story 12's bootstrap admin is a different thing: the operator's way into a fresh deployment. The seeder therefore runs in every profile, but only when no active (non-deleted) admin exists. It reads `app.admin.username`, `app.admin.password` and `app.admin.email` from the environment; outside the dev profile these have no defaults, and startup fails if any is missing. The password must pass the normal password policy. Nothing else is ever seeded outside the dev profile.

The self-action guards (an admin can't disable, delete or demote their own account) mean the acting admin always remains an admin. So once an active admin exists there is always at least one, and the seeder never creates a second.
