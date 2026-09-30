-- Tombstones of Deleted Accounts, kept indefinitely: the soft-delete record the Standard requires
-- (ADR 0001). The username is unique here as well as in users, so a deleted username can never be
-- registered or bootstrapped again; the email is not unique, because a Deleted Account's email may
-- be reused. No foreign key on deleted_by: the deleting Admin's own Account may be deleted later,
-- and the tombstone must outlive it.
CREATE TABLE deleted_users (
	id UUID NOT NULL,
	username VARCHAR(32) NOT NULL,
	email VARCHAR(254) NOT NULL,
	deleted_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
	deleted_by UUID NOT NULL,
	CONSTRAINT deleted_users_pk PRIMARY KEY (id),
	CONSTRAINT deleted_users_username_uk UNIQUE (username)
);
