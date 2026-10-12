# Changelog

All notable changes to the Momkn Pay backend. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/). API contract changes are also recorded in the changelog inside [`docs/openapi.yaml`](docs/openapi.yaml).

## [4.0.0] — 2026-10-07

**Breaking:** the session key is agreed by both sides and never sent (ADR-013). Contract v4.0.0.

### Added
- `GET /v1/sessions/public-key`: the server's long-term P-256 handshake key (`keyId`, `algorithm`, `publicKey`). Not a secret.
- `POST /v1/sessions` takes a body `{ clientPublicKey }` and returns `serverPublicKey` (the server's ephemeral key for the session).
- `scripts/generate-session-keypair.sh` and the setting `APP_SESSION_PRIVATE_KEY` (startup fails without a valid P-256 key).
- The Postman collection does the handshake itself (pure-JS P-256 and HKDF, checked against Node's crypto).

### Changed
- Both sides derive the AES-256 key: `HKDF-SHA256( ECDH(client, server long-term) ‖ ECDH(client, server ephemeral), info )`.

### Removed
- `sessionKey` in the response of `POST /v1/sessions`.

### Unchanged
- The payload format `base64(iv ‖ ciphertext ‖ tag)`, `X-Session-Id`, nonce and `ts` rules, the 30-minute lifetime, revoke, rate limits, all 22 error codes, the database schema.

### Migration for clients
1. Build the app with the server's public key (from the backend track; base64 of the X.509 SubjectPublicKeyInfo). Do not fetch it at runtime.
2. Per session, make a P-256 key pair in memory and send `clientPublicKey` (same encoding) to `POST /v1/sessions`.
3. Compute `ikm = ECDH(private, built-in server key) ‖ ECDH(private, serverPublicKey from the response)`, each 32 bytes.
4. `key = HKDF-SHA256(ikm, no salt, info, 32 bytes)` with `info = "momknpay/v4/session|" + sessionId + "|" + userId + "|" + clientPublicKey + "|" + serverPublicKey` (UTF-8, the keys exactly as the base64 strings sent and received).
5. Use `key` exactly as `sessionKey` was used before. Discard the private key and the key on logout, expiry or exit.

- iOS: `P256.KeyAgreement.PrivateKey()`, `publicKey.derRepresentation`, `sharedSecretFromKeyAgreement(with:)`, `HKDF<SHA256>.deriveKey(inputKeyMaterial:info:outputByteCount:)`.
- Android: `KeyPairGenerator("EC")` with `secp256r1`, `publicKey.encoded`, `KeyAgreement("ECDH")`, HKDF from `Mac("HmacSHA256")` (RFC 5869) or Tink.
- Reference implementations: `postman/momkn-encrypt.js` (`momknHandshake`) and `TestCrypto.deriveSessionKey` in the tests.

## [3.0.0] — 2026-10-06

**Breaking:** sessions are back, as in 1.0.0 (ADR-012). Contract v3.0.0.

### Added
- `POST /v1/sessions` returns `{ sessionId, sessionKey, expiresAt }` (AES-256 key, valid 30 minutes) and `DELETE /v1/sessions/{sessionId}` revokes it.
- The `X-Session-Id` header is required again on `POST /v1/payments/inquiry` and `POST /v1/payments/confirm`.
- Error codes `SESSION_NOT_FOUND` (404) and `SESSION_EXPIRED` (410); there are 22 codes again.
- Rate limit of 5 per minute per user on `POST /v1/sessions`.
- Migration `V6__restore_sessions.sql` re-creates the `sessions` table and the `session_id` columns; an existing database upgrades in place.

### Changed
- Payloads are encrypted with the key of the caller's session, not with a shared key.
- Configuration: `APP_MASTER_KEY` (wraps session keys at rest) replaces `APP_PAYLOAD_KEY`.
- The Postman collection creates a session first (38 requests, folders 01–06). It still defaults to `https://localhost/v1` and verifies the server certificate.

### Removed
- The static shared payload key (`APP_PAYLOAD_KEY`, the `payloadKey` Postman variable).

### Migration for clients
1. On app start (or before the first payment), call `POST /v1/sessions` with `X-User-Id` and keep `sessionKey` in memory only.
2. Encrypt `payload` with that key and send `X-Session-Id` on inquiry and confirm.
3. On `SESSION_EXPIRED`, create a new session and retry; on logout or exit, call `DELETE /v1/sessions/{sessionId}`.
4. Remove the built-in shared key from the app.

## [2.0.0] — 2026-10-01

**Breaking:** sessions are removed (ADR-011). Contract v2.0.0.

### Removed
- `POST /v1/sessions`, `DELETE /v1/sessions/{sessionId}` and the `X-Session-Id` header.
- Error codes `SESSION_NOT_FOUND` and `SESSION_EXPIRED`; inquiry no longer returns 410.
- The `sessions` table and the `session_id` columns (migration `V5__drop_sessions.sql`), the master key and session-key wrapping, the session cleanup job, and the rate limit on `/sessions`.
- Configuration `APP_MASTER_KEY` and `APP_SESSION_TTL`.

### Changed
- Payloads (subscriber number, PIN) are still AES-256-GCM-encrypted, now with **one static key** from `APP_PAYLOAD_KEY` that is also built into the apps. Startup fails without a valid 32-byte key.
- The replay guard no longer references a session; nonces stay single-use and `ts` must be within 120 s.
- The `session` package is now `payload`.
- The Postman collection has no session requests, takes the key from the `payloadKey` variable, defaults to `https://localhost/v1` and verifies the server certificate (trust `certs/cert.pem`).

### Fixed
- `Retry-After` was one second too long when exactly a whole number of seconds remained (61 instead of 60).

### Migration for clients
Remove the session calls and the `X-Session-Id` header, and encrypt payloads with the shared key from the backend track instead of a per-session key. Nothing else in the payment flow changes: same payload format, PIN, `X-User-Id` and `Idempotency-Key`.

## [1.0.0] — 2026-09-30

First complete release: all ten endpoints of the frozen contract v1.0.0, delivered in milestones M0–M4 ([docs/MILESTONES.md](docs/MILESTONES.md)).

### Added
- **Contract and tooling (M0):** the OpenAPI 3.1 contract (`docs/openapi.yaml`), Redocly lint config, a Prism mock server, the Postman collection, GitHub Actions CI, Spotless formatting, and a pull request template with the review checklist.
- **Platform (M1):**
  - PostgreSQL schema and seed data through Flyway: 24 services, 3 users with bcrypt PINs.
  - The error envelope with 22 codes in English and Arabic.
  - `X-Request-Id` propagation and required client headers.
  - HTTPS-only Docker Compose, with a certificate and SPKI-pin script.
  - `X-User-Id` resolution.
  - AES-256-GCM with session keys wrapped at rest.
  - `POST`/`DELETE /v1/sessions`, payload decryption with nonce and timestamp replay protection.
  - Per-user rate limits.
- **Catalogue and profile (M2):**
  - `GET /v1/services` and delta sync with `deletedIds`; `syncedAt` leans 5 s into the past to absorb clock skew.
  - `GET`/`PATCH /v1/profile`.
  - The generated OpenAPI spec aligned with the contract, checked by `OpenApiContractIT`.
- **Payments (M3):**
  - The fee calculator and the deterministic mock engine.
  - `POST /v1/payments/inquiry`, with the `_slow` delay.
  - `POST /v1/payments/confirm`, idempotent: key lookup before decryption, an inquiry row lock and a unique-constraint backstop.
  - Wrong-PIN counting and invalidation, decline recording, and `PENDING` → `SUCCESS` resolution.
  - `GET /v1/payments/transactions[/{id}]`, and seeded history for `usr_01`.
  - Postman folders for every mock rule, with a verified AES-GCM helper in plain JavaScript.
- **Hardening (M4):**
  - A log-hygiene test and a gitleaks history scan in CI.
  - ArchUnit architecture rules and JaCoCo coverage (≈ 94 % lines, crypto package gated at 90 %).
  - A performance smoke test.
  - Parameter checks in the contract test.
  - The rewritten README and a clean-machine rehearsal.

### Decisions
- ADR-001…009: no auth module and the consequences, recorded in [docs/DECISIONS.md](docs/DECISIONS.md).
- ADR-010: confirm has a 500 ms p95 budget because bcrypt cost 12 (required) takes about 230 ms.

### Known limitations
See the README section *Known limitations*. The most important one: there is **no authentication**, so `X-User-Id` is trusted.
