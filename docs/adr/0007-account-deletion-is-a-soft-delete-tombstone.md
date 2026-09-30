# Account deletion is a soft-delete tombstone

PRD Story 11 says a deleted account "is removed". The standard strictly requires soft deletion so the audit trail survives. We set `deleted_at` and keep the row as a tombstone: it is hidden from every query, list and login, its pending password reset tokens are deleted, its sessions are ended, and its username and email remain reserved so they can never be registered again. From the admin's point of view the account is gone, which meets the PRD. Don't "simplify" this to a hard `DELETE`.
