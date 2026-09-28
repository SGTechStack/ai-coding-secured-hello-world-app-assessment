-- Accounts. The id is an application-generated UUIDv4 (ADR-050).
-- Being locked is derived from locked_until and is never stored (REJ-017).
CREATE TABLE users (
    id                                 UUID                        NOT NULL,
    username                           VARCHAR(32)                 NOT NULL,
    email                              VARCHAR(254)                NOT NULL,
    -- Null until the user sets a password by redeeming a token (ADR-006; ADR-032).
    password_hash                      VARCHAR(255),
    role                               VARCHAR(20)                 NOT NULL,
    enabled                            BOOLEAN                     NOT NULL,
    activated_at                       TIMESTAMP(6) WITH TIME ZONE,
    -- Lockout window counter and its staleness anchor (ADR-012).
    failed_login_attempts              INTEGER                     DEFAULT 0 NOT NULL,
    last_failed_at                     TIMESTAMP(6) WITH TIME ZONE,
    locked_until                       TIMESTAMP(6) WITH TIME ZONE,
    -- NIST cap: its own counter and its own disabled state, never enabled = false (ADR-013; REJ-020).
    consecutive_failures_since_success INTEGER                     DEFAULT 0 NOT NULL,
    password_disabled_at               TIMESTAMP(6) WITH TIME ZONE,
    -- Forced change and its lazy 30-day expiry (ADR-046).
    force_password_change              BOOLEAN                     DEFAULT FALSE NOT NULL,
    credential_issued_at               TIMESTAMP(6) WITH TIME ZONE,
    created_at                         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT fk_users_role FOREIGN KEY (role) REFERENCES roles (name)
);

CREATE UNIQUE INDEX ux_users_username ON users (username);
CREATE UNIQUE INDEX ux_users_email ON users (email);
