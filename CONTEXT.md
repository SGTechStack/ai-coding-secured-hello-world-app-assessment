# Hello World Auth

A reference application demonstrating secure username/password authentication, session handling, and administrator management of accounts.

## Language

### People

**Visitor**:
Someone interacting with the app without an authenticated session.
_Avoid_: Guest, anonymous user

**Account**:
The persisted record of one person's identity in the app: username, email, credentials, role, enabled flag, and lock state.
_Avoid_: User (when meaning the record), profile, login

**Regular user**:
An account holder whose account has the `USER` role.
_Avoid_: Member, customer, "user" (ambiguous with the role name and the record)

**Admin**:
An account holder whose account has the `ADMIN` role and may manage other accounts.
_Avoid_: Administrator, superuser, operator

**Bootstrap admin**:
The admin account created at startup from operator-supplied configuration when no active admin exists; a real account in every environment.
_Avoid_: Seed account, default admin, dev account (those are development-only test fixtures, a different thing)

**Tombstone**:
The retained record of a deleted account: invisible to every query and unable to authenticate, but its username and email stay reserved forever.
_Avoid_: Deleted user, archived account

### Access restrictions

**Disabled**:
An account an admin has deliberately switched off; it cannot authenticate until an admin re-enables it. Independent of being locked.
_Avoid_: Suspended, deactivated, inactive

**Locked**:
An account temporarily barred from authenticating because of consecutive failed logins; the lock expires on its own after a fixed duration. Independent of being disabled — re-enabling an account does not clear a lock.
_Avoid_: Blocked, frozen, suspended

**Throttled**:
A request rejected because its source or submitted username exceeded a rate limit. It is a property of the request, not a state of any account.
_Avoid_: Locked, blocked, rate-locked

**Failed-login counter**:
The number of consecutive failed login attempts against one account since its last successful login.
_Avoid_: Strike count, attempts

### Recovery

**Password reset token**:
A single-use, short-lived secret emailed to an account's owner that lets them set a new password without logging in. It does not clear a lock.
_Avoid_: Reset link (the link merely carries the token), reset code, OTP
