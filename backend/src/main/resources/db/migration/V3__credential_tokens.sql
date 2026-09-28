-- Activation and password-reset tokens in one table (ADR-007).
-- This table STORES ONLY A HASH: token_hash is lowercase hex SHA-256(type_label || ':' || token).
-- Never add a plaintext token column, not even for debugging.
CREATE TABLE credential_tokens (
    id         UUID                        NOT NULL,
    user_id    UUID                        NOT NULL,
    type       VARCHAR(20)                 NOT NULL,
    token_hash VARCHAR(64)                 NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_credential_tokens PRIMARY KEY (id),
    CONSTRAINT ck_credential_tokens_type CHECK (type IN ('ACTIVATION', 'PASSWORD_RESET'))
);

CREATE UNIQUE INDEX ux_credential_tokens_token_hash ON credential_tokens (token_hash);
CREATE INDEX ix_credential_tokens_user_id_type ON credential_tokens (user_id, type);

-- Added after the index so the foreign key reuses it. Deletion cascades (REJ-034).
ALTER TABLE credential_tokens ADD CONSTRAINT fk_credential_tokens_user_id
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
