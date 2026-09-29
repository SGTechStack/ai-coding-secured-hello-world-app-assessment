CREATE TABLE roles (
    name VARCHAR(64) PRIMARY KEY
);

INSERT INTO roles (name) VALUES ('USER');
INSERT INTO roles (name) VALUES ('USER_MANAGER');

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_accounts_role FOREIGN KEY (role_name) REFERENCES roles (name),
    CONSTRAINT chk_accounts_lowercase_username CHECK (username = LOWER(username)),
    CONSTRAINT chk_accounts_supported_password CHECK (
        password_hash LIKE '{argon2}%' OR password_hash LIKE '{bcrypt}%'
    )
);
