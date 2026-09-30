-- Trusted devices: one row per device cookie issued after a correct password (ADR-075). The cookie carries the id and
-- an HMAC over it; this row binds it to its account and holds the device lane's own lock, on the same ladder as the
-- account's untrusted lane, so a device lock survives a restart (Std §5:451). Every row of an account is deleted when
-- its credential changes, it is disabled or reset by an administrator, and by cascade when it is deleted.
CREATE TABLE trusted_devices (
    id                    UUID                        NOT NULL,
    user_id               UUID                        NOT NULL,
    created_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    expires_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    -- The device lane's windowed counter and its staleness anchor (ADR-012), and its lock (REJ-017: derived).
    failed_login_attempts INTEGER                     DEFAULT 0 NOT NULL,
    last_failed_at        TIMESTAMP(6) WITH TIME ZONE,
    locked_until          TIMESTAMP(6) WITH TIME ZONE,
    -- The device lane's failures since its last success, which picks its rung (ADR-011). The NIST cap is the account's.
    consecutive_failures  INTEGER                     DEFAULT 0 NOT NULL,
    CONSTRAINT pk_trusted_devices PRIMARY KEY (id)
);

CREATE INDEX ix_trusted_devices_user_id ON trusted_devices (user_id);

-- Added after the index so the foreign key reuses it.
ALTER TABLE trusted_devices ADD CONSTRAINT fk_trusted_devices_user_id
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
