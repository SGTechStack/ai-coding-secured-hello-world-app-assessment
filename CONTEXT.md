# Demo App

A demo web application with username/password sign-in, a protected greeting and an admin area for managing accounts.

## Language

**Account**:
The persisted identity of a person who can sign in, with a username, credentials, a Role and an enabled flag.
_Avoid_: User (when meaning the stored record)

In code the persisted class is named `AppUser` (package `user`, table `app_user`, role rows `AppUserRole`, admin endpoints under `/admin/api/users`); a template-wide rename to Account is deferred. Read those names as Account.

**Role**:
The privilege level of an Account: `USER`, `USER_MANAGER` or `ADMIN`.
_Avoid_: Permission, authority

**Temporary Password**:
A one-time credential an admin sets when creating or resetting an Account, which the holder must replace on first sign-in and which expires if unused.
_Avoid_: Reset token, reset link, initial password
