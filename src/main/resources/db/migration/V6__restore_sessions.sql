-- Sessions are back (ADR-012, contract v3.0.0): the payload key is issued per session by
-- POST /v1/sessions again, as it was before V5.

CREATE TABLE sessions (
    id           VARCHAR(40) PRIMARY KEY,           -- ses_<32 hex>
    user_id      VARCHAR(32) NOT NULL REFERENCES users (id),
    wrapped_key  BYTEA       NOT NULL,              -- iv(12) || AES-GCM(master, key) || tag(16)
    created_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ NULL
);
CREATE INDEX ix_sessions_user ON sessions (user_id);
CREATE INDEX ix_sessions_expires ON sessions (expires_at);

-- Replay records live for five minutes and none of the existing ones belongs to a session.
DELETE FROM used_nonces;
ALTER TABLE used_nonces
    ADD COLUMN session_id VARCHAR(40) NOT NULL REFERENCES sessions (id) ON DELETE CASCADE;

ALTER TABLE inquiries
    ADD COLUMN session_id VARCHAR(40) NULL REFERENCES sessions (id) ON DELETE SET NULL;
