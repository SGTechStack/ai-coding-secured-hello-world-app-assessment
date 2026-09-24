-- Baseline schema for the production profile (PostgreSQL).
--
-- This is the schema of record. The production profile runs Flyway and sets
-- Hibernate to `validate`, so if the entity classes and this file ever disagree,
-- the application refuses to start. That is the intended relationship: the
-- schema is owned here, and Hibernate's job is to check rather than to change.
--
-- Column types are spelled out rather than left to a dialect default, because
-- `validate` compares what the entities expect against what the database has.
-- `timestamp(6) with time zone` matches how Hibernate 6 maps `java.time.Instant`
-- on PostgreSQL; `varchar(255)` matches an unannotated String column.
--
-- Not yet exercised against a real PostgreSQL instance — this repository has no
-- database to run it against, which is tracked as outstanding work. What is
-- checked automatically is that this file and the entity model name the same
-- tables and columns (see FlywayMigrationTest), which catches the realistic
-- failure: a field added to an entity and never migrated.

CREATE TABLE users (
    id                     uuid                        NOT NULL,
    username               varchar(255)                NOT NULL,
    email                  varchar(255)                NOT NULL,
    password_hash          varchar(255)                NOT NULL,
    role                   varchar(255)                NOT NULL,
    enabled                boolean                     NOT NULL,
    failed_login_attempts  integer                     NOT NULL,
    locked_until           timestamp(6) with time zone,
    last_failed_login_at   timestamp(6) with time zone,
    created_at             timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- Lookups are case-insensitive (findByUsernameIgnoreCase / findByEmailIgnoreCase),
-- so the unique constraints above do not cover them: 'Alice' and 'alice' are two
-- rows that both answer the same lookup, and which one wins is arbitrary.
-- Functional unique indexes close that, and also make the case-insensitive
-- lookups index-assisted instead of sequential scans.
CREATE UNIQUE INDEX uq_users_username_lower ON users (lower(username));
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email));

-- Supports the last-enabled-admin count that guards every destructive admin
-- mutation. Partial, because the only question ever asked of it is how many
-- enabled admins exist.
CREATE INDEX ix_users_enabled_admins ON users (role) WHERE enabled;

CREATE TABLE password_reset_tokens (
    id          uuid                        NOT NULL,
    user_id     uuid                        NOT NULL,
    token_hash  varchar(255)                NOT NULL,
    expires_at  timestamp(6) with time zone NOT NULL,
    used_at     timestamp(6) with time zone,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash),
    -- No ON DELETE CASCADE, matching the entity model: the application clears a
    -- user's tokens explicitly before deleting the account, because the user
    -- package deliberately does not depend on the passwordreset package. A
    -- cascade here would make that ordering invisible and the explicit delete
    -- look redundant.
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Drives both the retention sweep and the "retire outstanding tokens on issue"
-- lookup.
CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);
CREATE INDEX ix_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);

-- Append-only audit trail for irreversible, privilege-changing and
-- personal-data-revealing actions.
--
-- Note the absence of a foreign key to users on actor_id and target_id. That is
-- the point: a record of a deletion has to outlive the row it describes, and a
-- foreign key would force it to either cascade away with the account (destroying
-- the evidence) or block the deletion.
--
-- Append-only is enforced in the application by an immutable entity and a
-- repository that exposes no delete. Enforcing it against someone holding
-- database credentials needs a grant this file cannot express, because the
-- migration runs as the owner:
--
--   REVOKE UPDATE, DELETE, TRUNCATE ON admin_audit_log FROM <application_role>;
--   GRANT INSERT, SELECT ON admin_audit_log TO <application_role>;
--
-- That belongs in deployment provisioning, with the application connecting as a
-- role distinct from the migration owner. See
-- docs/adr/0007-append-only-admin-audit-log.md.
CREATE TABLE admin_audit_log (
    id           uuid                        NOT NULL,
    occurred_at  timestamp(6) with time zone NOT NULL,
    action       varchar(255)                NOT NULL,
    actor_id     uuid,
    actor_ref    varchar(255)                NOT NULL,
    target_id    uuid,
    target_ref   varchar(255)                NOT NULL,
    client_ip    varchar(255)                NOT NULL,
    request_id   varchar(255)                NOT NULL,
    detail       varchar(512)                NOT NULL,
    CONSTRAINT pk_admin_audit_log PRIMARY KEY (id)
);

CREATE INDEX ix_admin_audit_log_occurred_at ON admin_audit_log (occurred_at);
CREATE INDEX ix_admin_audit_log_target_id ON admin_audit_log (target_id);
CREATE INDEX ix_admin_audit_log_action ON admin_audit_log (action);
