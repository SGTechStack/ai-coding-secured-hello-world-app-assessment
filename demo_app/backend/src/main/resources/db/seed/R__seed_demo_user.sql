-- Demo account for local runs and tests only. Flyway reads db/seed in the dev profile and in tests,
-- never by default, so no other environment gets this user. BCrypt hash (cost 10, still verified
-- by the cost-12 encoder); the plaintext lives in the README only. MERGE keeps the repeatable
-- migration idempotent. role, enabled, the lockout fields and created_at take their defaults.
MERGE INTO user_account (username, email, password_hash, first_name, role) KEY (username)
VALUES ('johndoe', 'johndoe@example.com', '$2a$10$3s2LaKm3nYQeJOv4V3UYluRC1/hUnJQOxHQE9DiItAVWsWM.SJMCi', 'John', 'USER');
