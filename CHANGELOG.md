# Changelog

All notable changes to the Momkn Pay backend. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/). API contract changes are also recorded in the changelog inside [`docs/openapi.yaml`](docs/openapi.yaml).

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
