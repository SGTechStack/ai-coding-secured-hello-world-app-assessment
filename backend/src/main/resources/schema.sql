-- Portable DDL: only types shared by H2, PostgreSQL and MySQL.
CREATE TABLE IF NOT EXISTS users (
    id                    CHAR(36)     NOT NULL,
    username              VARCHAR(32)  NOT NULL,
    -- Lowercased username, so lookups and uniqueness ignore case.
    username_key          VARCHAR(32)  NOT NULL,
    -- Stored lowercased.
    email                 VARCHAR(254) NOT NULL,
    -- Includes the DelegatingPasswordEncoder prefix, e.g. {bcrypt}.
    password_hash         VARCHAR(255) NOT NULL,
    role                  VARCHAR(16)  NOT NULL,
    enabled               BOOLEAN      NOT NULL,
    failed_login_attempts INT          NOT NULL,
    locked_until          TIMESTAMP    NULL,
    created_at            TIMESTAMP    NOT NULL,
    -- Set means the Account is a Tombstone.
    deleted_at            TIMESTAMP    NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    -- Both keys cover Tombstones too, so deleted identities stay reserved.
    CONSTRAINT uk_users_username_key UNIQUE (username_key),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          CHAR(36)  NOT NULL,
    user_id     CHAR(36)  NOT NULL,
    -- SHA-256 of the token, in hex. The token itself is never stored.
    token_hash  CHAR(64)  NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    -- Set once the token has been redeemed: it never works again.
    used_at     TIMESTAMP NULL,
    created_at  TIMESTAMP NOT NULL,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_password_reset_tokens_token_hash UNIQUE (token_hash)
);
