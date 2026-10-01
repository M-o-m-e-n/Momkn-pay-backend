-- ADR-011: sessions are removed. Payloads are encrypted with one static shared key, so there is
-- no per-session key to store and nothing for inquiries or nonces to reference.

ALTER TABLE inquiries   DROP COLUMN session_id;
ALTER TABLE used_nonces DROP COLUMN session_id;
DROP TABLE sessions;
