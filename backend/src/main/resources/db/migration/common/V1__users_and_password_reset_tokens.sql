-- Portable across H2 and PostgreSQL.
CREATE TABLE users (
    id                    UUID                     NOT NULL,
    username              VARCHAR(32)              NOT NULL,
    email                 VARCHAR(254)             NOT NULL,
    password_hash         VARCHAR(100)             NOT NULL,
    role                  VARCHAR(16)              NOT NULL,
    enabled               BOOLEAN                  NOT NULL,
    failed_login_attempts INTEGER                  NOT NULL DEFAULT 0,
    locked_until          TIMESTAMP WITH TIME ZONE,
    -- Not in the PRD data model: needed to apply the lockout "within a window" rule.
    last_failed_login_at  TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_failed_login_attempts CHECK (failed_login_attempts >= 0)
);

CREATE INDEX ix_users_role ON users (role);

CREATE TABLE password_reset_tokens (
    id         UUID                     NOT NULL,
    user_id    UUID                     NOT NULL,
    -- Hex SHA-256 of the token; the token itself is never stored.
    token_hash VARCHAR(64)              NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_password_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX ix_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);
