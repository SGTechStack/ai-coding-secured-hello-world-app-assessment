-- Accounts and their Password History. Usernames and emails are stored in lowercase.
CREATE TABLE users (
	id BINARY(16) NOT NULL,
	username VARCHAR(32) NOT NULL,
	email VARCHAR(254) NOT NULL,
	password_hash VARCHAR(100) NOT NULL,
	role VARCHAR(16) NOT NULL,
	enabled BOOLEAN NOT NULL,
	password_change_required BOOLEAN DEFAULT FALSE NOT NULL,
	failed_login_attempts INT DEFAULT 0 NOT NULL,
	locked_until DATETIME(6) NULL,
	created_at DATETIME(6) NOT NULL,
	CONSTRAINT users_pk PRIMARY KEY (id),
	CONSTRAINT users_username_uk UNIQUE (username),
	CONSTRAINT users_email_uk UNIQUE (email),
	CONSTRAINT users_role_ck CHECK (role IN ('USER', 'ADMIN'))
) ENGINE=InnoDB;

CREATE TABLE password_history (
	id BINARY(16) NOT NULL,
	user_id BINARY(16) NOT NULL,
	password_hash VARCHAR(100) NOT NULL,
	created_at DATETIME(6) NOT NULL,
	CONSTRAINT password_history_pk PRIMARY KEY (id),
	CONSTRAINT password_history_user_fk FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE INDEX password_history_user_ix ON password_history (user_id);
