-- Completes the PRD account model once every row has an email and a BCrypt hash.
ALTER TABLE users ALTER COLUMN email SET NOT NULL;

ALTER TABLE users DROP CONSTRAINT chk_accounts_supported_password;
ALTER TABLE users ADD CONSTRAINT chk_users_bcrypt_password CHECK (password_hash LIKE '{bcrypt}%');
