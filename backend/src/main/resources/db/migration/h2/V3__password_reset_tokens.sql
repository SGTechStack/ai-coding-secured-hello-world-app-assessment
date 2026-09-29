-- Reset Tokens: only a SHA-256 hash is stored, never the token itself. Rows are never deleted;
-- cancelling a pending token marks it used instead.
CREATE TABLE password_reset_tokens (
	id UUID NOT NULL,
	user_id UUID NOT NULL,
	token_hash VARCHAR(64) NOT NULL,
	expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
	used_at TIMESTAMP(6) WITH TIME ZONE,
	CONSTRAINT password_reset_tokens_pk PRIMARY KEY (id),
	CONSTRAINT password_reset_tokens_hash_uk UNIQUE (token_hash),
	CONSTRAINT password_reset_tokens_user_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX password_reset_tokens_user_ix ON password_reset_tokens (user_id);
