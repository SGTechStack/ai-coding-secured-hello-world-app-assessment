-- Prior password hashes, for reuse refusal. Purged with the account by cascade (REJ-035).
CREATE TABLE password_history (
    id            UUID                        NOT NULL,
    user_id       UUID                        NOT NULL,
    password_hash VARCHAR(255)                NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_password_history PRIMARY KEY (id)
);

CREATE INDEX ix_password_history_user_id_created_at ON password_history (user_id, created_at);

-- Added after the index so the foreign key reuses it.
ALTER TABLE password_history ADD CONSTRAINT fk_password_history_user_id
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
