-- Demo account for local runs and tests only. Flyway reads db/seed in the dev profile and in tests,
-- never by default, so no other environment gets this user. BCrypt hash (cost 10); the plaintext
-- lives in the README only. MERGE keeps the repeatable migration idempotent.
MERGE INTO user_account (username, password_hash, first_name) KEY (username)
VALUES ('johndoe', '$2a$10$3s2LaKm3nYQeJOv4V3UYluRC1/hUnJQOxHQE9DiItAVWsWM.SJMCi', 'John');
