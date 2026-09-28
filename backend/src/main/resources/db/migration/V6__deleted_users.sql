-- Tombstones: kept indefinitely, and block reuse of both identifiers (ADR-044).
-- email_hmac is lowercase hex HMAC-SHA-256 of the canonical email under the tombstone key (ADR-052).
CREATE TABLE deleted_users (
    -- Deliberately NOT a foreign key: the users row is deleted in the same transaction that writes this one.
    user_id       UUID                        NOT NULL,
    username      VARCHAR(32)                 NOT NULL,
    email_hmac    VARCHAR(64)                 NOT NULL,
    deleted_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    -- Deliberately NOT a foreign key: the record must outlive the deleting admin (REJ-034).
    deleted_by_id UUID                        NOT NULL,
    CONSTRAINT pk_deleted_users PRIMARY KEY (user_id)
);

CREATE UNIQUE INDEX ux_deleted_users_username ON deleted_users (username);
CREATE UNIQUE INDEX ux_deleted_users_email_hmac ON deleted_users (email_hmac);
