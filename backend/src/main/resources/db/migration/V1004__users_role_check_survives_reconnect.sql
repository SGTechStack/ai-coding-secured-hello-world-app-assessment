-- H2 2.4.240 marks a CHECK built from OR / multi-value IN over string literals as "invalid" once every
-- session on the in-memory database has closed, rejecting all later writes. The CASE form is equivalent
-- and unaffected.
ALTER TABLE users DROP CONSTRAINT chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (
    CASE WHEN role = 'USER' THEN TRUE WHEN role = 'ADMIN' THEN TRUE ELSE FALSE END
);
