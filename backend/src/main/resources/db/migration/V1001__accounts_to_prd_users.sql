-- Reshapes the account table to the PRD data model (`users`). Versioned above the V1000 dev seed so the
-- committed seed still applies to the old shape; V1002 (dev seed) and V1003 finish the change.
ALTER TABLE accounts RENAME TO users;

ALTER TABLE users DROP CONSTRAINT fk_accounts_role;
DROP TABLE roles;

ALTER TABLE users RENAME COLUMN role_name TO role;
-- Roles are exactly USER and ADMIN; the retired USER_MANAGER (and anything else) becomes USER.
UPDATE users SET role = 'USER' WHERE role NOT IN ('USER', 'ADMIN');
ALTER TABLE users ALTER COLUMN role VARCHAR(16);
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'));

ALTER TABLE users RENAME COLUMN failed_login_count TO failed_login_attempts;
ALTER TABLE users DROP COLUMN first_name;
ALTER TABLE users RENAME CONSTRAINT chk_accounts_lowercase_username TO chk_users_lowercase_username;

-- Nullable until existing rows have one; V1003 makes it NOT NULL.
ALTER TABLE users ADD COLUMN email VARCHAR(254) NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
ALTER TABLE users ADD CONSTRAINT chk_users_lowercase_email CHECK (email = LOWER(email));

ALTER TABLE users ADD COLUMN created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL;
