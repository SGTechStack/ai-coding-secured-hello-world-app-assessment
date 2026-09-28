-- TOTP: enrolled means a totp_user_details row exists; the user id is each table's primary key (ADR-053).
-- totp_key is the 69-byte AES-GCM envelope (16-byte IV, 37-byte ciphertext, 16-byte tag). VARBINARY, never BINARY,
-- which zero-pads. validate does not see widths, so named checks pin them (ADR-028; REJ-033).
-- key_version is copied into a one-byte field inside the envelope, hence 0..255 (ADR-022; ADR-028).
CREATE TABLE totp_user_details (
    user_id             UUID                        NOT NULL,
    totp_key            VARBINARY(69)               NOT NULL,
    key_version         SMALLINT                    NOT NULL,
    -- Replay rejection: the last accepted time-step counter.
    last_used_counter   BIGINT,
    -- Tier 1: consecutive failures in a window, then an auto-lifting lock (ADR-027).
    failed_attempts     INTEGER                     DEFAULT 0 NOT NULL,
    last_failed_at      TIMESTAMP(6) WITH TIME ZONE,
    locked_until        TIMESTAMP(6) WITH TIME ZONE,
    -- Tier 2: cumulative failures, never reset by a success; the factor is disabled at the cap (ADR-027).
    cumulative_failures INTEGER                     DEFAULT 0 NOT NULL,
    factor_disabled_at  TIMESTAMP(6) WITH TIME ZONE,
    created_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_totp_user_details PRIMARY KEY (user_id),
    CONSTRAINT fk_totp_user_details_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_totp_user_details_totp_key_len CHECK (OCTET_LENGTH(totp_key) = 69),
    CONSTRAINT ck_totp_user_details_key_version_range CHECK (key_version BETWEEN 0 AND 255)
);

-- A provisioned secret awaiting confirmation. Confirmation copies totp_key verbatim, so the widths match.
CREATE TABLE pending_totp (
    user_id     UUID                        NOT NULL,
    totp_key    VARBINARY(69)               NOT NULL,
    key_version SMALLINT                    NOT NULL,
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_pending_totp PRIMARY KEY (user_id),
    CONSTRAINT fk_pending_totp_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_pending_totp_totp_key_len CHECK (OCTET_LENGTH(totp_key) = 69),
    CONSTRAINT ck_pending_totp_key_version_range CHECK (key_version BETWEEN 0 AND 255)
);
