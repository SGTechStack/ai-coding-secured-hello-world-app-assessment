-- A registration's hold on its username for the pending period, written whatever state the email address is in, so
-- whether a username got reserved never tells an anonymous caller the address's state (ADR-032 amendment).
-- email is the canonical address the hold was taken for: the same address may repeat its registration.
CREATE TABLE username_holds (
    id         UUID                        NOT NULL,
    username   VARCHAR(32)                 NOT NULL,
    email      VARCHAR(254)                NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_username_holds PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_username_holds_username ON username_holds (username);
CREATE INDEX ix_username_holds_expires_at ON username_holds (expires_at);
