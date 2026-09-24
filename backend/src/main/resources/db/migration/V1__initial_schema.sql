-- Baseline schema. H2, in every profile.
--
-- This is the schema of record. Flyway runs it everywhere, and outside dev
-- Hibernate is set to `validate`, so if the entity classes and this file ever
-- disagree the application refuses to start. The schema is owned here; Hibernate's
-- job is to check rather than to change.
--
-- Unlike the PostgreSQL version this replaced, these migrations *are* exercised:
-- dev and the whole test suite build their schema from this file rather than from
-- the entity model, so a mistake here fails the build instead of waiting for a
-- deployment. That is the one clear gain from standardising on H2.
--
-- Column types are spelled out rather than left to a dialect default, because
-- `validate` compares what the entities expect against what the database has.
-- They were taken from Hibernate's own generated DDL for this entity model on
-- H2, not guessed: `timestamp(6) with time zone` is how Hibernate 6 maps
-- java.time.Instant here, and `varchar(255)` is an unannotated String.
--
-- Enums are varchar, which requires `hibernate.type.preferred_enum_jdbc_type:
-- VARCHAR` in application.yml. Left to itself, Hibernate would expect H2's native
-- `ENUM ('A','B')` column type, which bakes the value list into the schema and
-- means adding an enum constant needs a migration to alter the column. varchar
-- keeps the vocabulary in Java, where the enum already lives.

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
    -- Generated, not written by the application, and deliberately absent from the
    -- User entity. See the unique indexes below for why they exist at all.
    --
    -- Hibernate's schema validation checks that every column the entities expect
    -- is present; it does not object to columns it has never heard of. So these
    -- stay invisible to the domain model while still being enforced by the
    -- database.
    username_lower         varchar(255) GENERATED ALWAYS AS (LOWER(username)),
    email_lower            varchar(255) GENERATED ALWAYS AS (LOWER(email)),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- The repository looks accounts up case-insensitively
-- (findByUsernameIgnoreCase / findByEmailIgnoreCase), so the constraints above do
-- not cover the lookup: 'Alice' and 'alice' are two permitted rows that both
-- answer it, and which one wins is arbitrary. Registration checks
-- existsByUsernameIgnoreCase first, but that is a read followed by a write, so two
-- concurrent registrations can both pass it.
--
-- PostgreSQL expresses this directly as a unique index on lower(username). H2
-- rejects expression indexes, so the expression moves into a generated column and
-- the index goes on that. Same guarantee, one more column.
CREATE UNIQUE INDEX uq_users_username_lower ON users (username_lower);
CREATE UNIQUE INDEX uq_users_email_lower ON users (email_lower);

-- Supports the enabled-admin count that guards every destructive admin mutation
-- and the self-service erasure path. PostgreSQL would make this a partial index
-- on (role) WHERE enabled, since that count is the only question ever asked of it;
-- H2 has no partial indexes, so it covers both columns instead. Slightly larger,
-- same lookups served.
CREATE INDEX ix_users_role_enabled ON users (role, enabled);

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
-- database credentials needs a grant this file cannot make, because the migration
-- runs as the owner. H2 supports the necessary statements, so the shape is the
-- same as it would be on any engine:
--
--   CREATE USER app_user PASSWORD '...';
--   GRANT INSERT, SELECT ON admin_audit_log TO app_user;
--
-- H2 grants are additive rather than revocable per operation, so the application
-- must connect as a role that was never granted UPDATE or DELETE on this table in
-- the first place. That belongs in deployment provisioning. See
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
