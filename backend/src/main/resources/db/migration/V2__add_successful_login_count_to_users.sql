-- Adds a per-account count of successful logins, shown to admins on the user
-- list (UserSummaryResponse). Purely informational: nothing in access control
-- or throttling reads this column, unlike failed_login_attempts.
--
-- DEFAULT 0 covers any row that predates this column; new rows always set it
-- explicitly via the User constructor.
ALTER TABLE users ADD COLUMN successful_login_count integer NOT NULL DEFAULT 0;
