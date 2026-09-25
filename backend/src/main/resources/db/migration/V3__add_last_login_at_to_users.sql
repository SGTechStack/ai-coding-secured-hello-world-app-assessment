-- Adds the timestamp of an account's most recent successful login, shown to
-- admins on the user list (UserSummaryResponse) alongside successful_login_count.
-- Purely informational, same as that column: nothing in access control or
-- throttling reads this.
--
-- Nullable, with no default: an account that has never logged in successfully
-- (freshly registered, or migrated before this column existed) has no last
-- login to report, and NULL says that plainly rather than a fabricated epoch.
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP;
