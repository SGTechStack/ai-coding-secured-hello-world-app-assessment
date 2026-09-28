-- Role definitions: seeded once here and never written at runtime (ADR-042).
-- app.security.roles is the source of truth; this table exists so users.role can be a foreign key.
CREATE TABLE roles (
    name VARCHAR(20) NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (name)
);

INSERT INTO roles (name) VALUES ('USER');
INSERT INTO roles (name) VALUES ('ADMIN');
