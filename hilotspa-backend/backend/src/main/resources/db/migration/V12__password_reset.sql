-- V12 - forgotten passwords, and an administrator who can help at the counter.
--
-- Until now the only way back into an account was to already know the password.
-- There was no /auth/forgot-password, no token, and the TEMPORARY PASSWORD
-- field in the admin Accounts drawer only existed while CREATING an account,
-- so not even the front desk could help.
--
-- WHAT IS STORED, AND WHAT IS NOT.
--
-- token_hash holds the SHA-256 of the reset token. The token itself exists in
-- exactly one place: the email that was sent. This is the same reasoning as
-- password_hash on users - a database dump, a stray pg_dump on a laptop, or a
-- replica pulled by the other node must not hand anybody a working reset link.
-- A hash is enough to VERIFY a token somebody presents, and useless for
-- MINTING one.
--
-- SHA-256 rather than BCrypt here on purpose, and it is not an oversight. A
-- password is low-entropy and human-chosen, so it needs a deliberately slow
-- hash to survive a dictionary attack. This token is 32 bytes from
-- SecureRandom; there is no dictionary to try, and a slow hash would only mean
-- a slow endpoint.
--
-- used_at rather than DELETE: a reset that has already happened is worth being
-- able to see afterwards. "Somebody reset this account at 02:14" is an audit
-- question, and a deleted row cannot answer it.
--
-- NOT replicated. This table carries no EntityListeners(SyncAudited), so
-- nothing about it is offered to /api/v1/sync/changes. A reset token is
-- meaningful only on the node that issued it and the node that will consume
-- it - which are the same node, because the link points at that node's own
-- site. Accounts do not cross nodes either (stage 3 carries appointments), so
-- there is nothing here for a peer to want.

CREATE TABLE IF NOT EXISTS password_reset_token (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL,
    expires_at  timestamp   NOT NULL,
    used_at     timestamp,
    issued_by   varchar(40) NOT NULL,
    origin_node_id varchar(64) NOT NULL,
    created_at  timestamp   NOT NULL
);

-- The lookup on every reset attempt is by hash alone, and it must be unique:
-- two rows sharing a hash would mean either a collision or a bug, and either
-- way the safe answer is to refuse the insert rather than pick one.
CREATE UNIQUE INDEX IF NOT EXISTS ux_password_reset_token_hash
    ON password_reset_token (token_hash);

-- Used when a reset succeeds (expire this account's other outstanding tokens)
-- and when throttling repeat requests.
CREATE INDEX IF NOT EXISTS ix_password_reset_token_user
    ON password_reset_token (user_id, created_at DESC);
