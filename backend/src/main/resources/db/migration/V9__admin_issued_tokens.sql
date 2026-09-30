-- Marks a credential token an administrator issued: an invite's activation token or an admin-issued reset token
-- (ADR-006; ADR-007 amendment). The marker, not the account's role, is what tells an invite from a self-registration,
-- and what a self-service reset request leaves in place while it is pending.
ALTER TABLE credential_tokens ADD COLUMN admin_issued BOOLEAN DEFAULT FALSE NOT NULL;
