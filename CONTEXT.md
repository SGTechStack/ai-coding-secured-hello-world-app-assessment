# Secured Hello World

A reference application demonstrating a secure username/password account lifecycle: registration, login, logout, password recovery, and admin account management.

## Actors

**Visitor**:
Anyone without an authenticated session.
_Avoid_: Guest, anonymous user

**Account**:
The persisted record of a person who has registered, identified by a system-generated UUID and a unique username.
_Avoid_: User (when meaning the record), member, profile

**User**:
The role held by an ordinary Account holder. Refers to the role only, never the record.
_Avoid_: Using "user" to mean an Account

**Admin**:
The role that permits managing other Accounts.
_Avoid_: Superuser, operator

**Bootstrap Admin**:
The Admin Account created at startup when no Admin exists.
_Avoid_: Seed user, default admin

## Account states

**Enabled / Disabled**:
Whether an Admin permits the Account to log in. Only an Admin changes this.
_Avoid_: Active/inactive, suspended

**Locked**:
A temporary, automatic block on logging in to one Account after repeated failed passwords. Ends when the lockout period expires or an Admin unlocks it.
_Avoid_: Disabled, blocked

**Deleted Account**:
An Account removed by an Admin. A record of it (a tombstone) is kept for audit, and its username can never be registered again.
_Avoid_: Removed, purged

## Credentials and sessions

**Session**:
The server-side record of one authenticated login. An Account has at most one Session at a time.
_Avoid_: Token, login

**Password History**:
The previous passwords of an Account that may not be reused.

**Reset Token**:
A single-use, short-lived secret that lets a Visitor set a new password for an Account without knowing the old one.
_Avoid_: Reset link, OTP, reset code

**Password Change**:
An authenticated Account holder replacing their known password. Distinct from a password reset, which uses a Reset Token.
_Avoid_: Password update

**Required Password Change**:
A state in which an Account may do nothing except view itself, make a Password Change, or log out. Set on the Bootstrap Admin and by an Admin who suspects compromise; cleared by a Password Change or a password reset.
_Avoid_: Forced reset, password expiry

**IP Throttle**:
A temporary block on login attempts from one source address after repeated failures across any usernames. Independent of Locked.
_Avoid_: Rate limit (when meaning this specifically), IP ban
