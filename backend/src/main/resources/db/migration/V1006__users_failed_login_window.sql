-- Start of the current run of consecutive failed logins, so the lockout counts only failures that fall
-- within app.security.login.lockout-window (PRD Story 3: "N consecutive failed attempts within a window").
-- NULL means no run is in progress.
ALTER TABLE users ADD COLUMN failed_login_window_started_at TIMESTAMP WITH TIME ZONE NULL;
