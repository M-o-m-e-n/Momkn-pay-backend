# Momkn Pay Backend — Milestones & Slices

| Item | Value |
|---|---|
| Document | Delivery plan |
| Scope | Backend track only (no auth module; all endpoints public) |
| Duration | 4 weeks (20 working days, ~6 h/day) |
| Related | [SRS.md](SRS.md) · [HLD.md](HLD.md) · [LLD.md](LLD.md) |

---

## How to read this

- A **milestone** ends with a demo. The next milestone does not start until that demo passes (brief: *review gates*).
- A **slice** is one ticket, one branch, one pull request: **≤ ~400 changed lines, ≤ 1 day of work**, and mergeable on its own (the build stays green, and no half-finished endpoint is exposed).
- Branch: `feature/<slice-id>-<short-name>`, for example `feature/M1-S3-error-envelope`. Commits: `feat(payment): …`, `test(crypto): …`.
- Every slice lists **Done when**, which is copied into the ticket as its acceptance criterion.
- `Refs` point to requirement IDs (SRS) and sections (HLD/LLD).
- **Tracking progress:** tick a box by changing `- [ ]` to `- [x]`. GitHub also lets you click the box directly in the rendered file. Tick each task line as it is done, tick the slice when its **Done when** line is ticked and the pull request is merged, and tick the milestone when all its slices are ticked and the exit demo has passed.

### Overview

```mermaid
gantt
    title Backend milestones
    dateFormat  YYYY-MM-DD
    axisFormat  %d %b
    section M0 Contract
    Bootstrap + OpenAPI draft      :m0, 2026-10-05, 2d
    section M1 Foundations
    Platform, crypto, sessions     :m1, after m0, 3d
    section M2 Catalogue & profile
    Services, sync, profile        :m2, after m1, 5d
    section M3 Payments
    Inquiry, confirm, history      :m3, after m2, 5d
    section M4 Hardening
    Tests, docs, release           :m4, after m3, 5d
```

| Milestone | Days | Theme | Demo (gate) |
|---|---|---|---|
| [M0](#m0--bootstrap-and-contract-freeze-days-12) | 1–2 | Bootstrap and contract freeze | OpenAPI 3.1 published. Clients run a mock server from it. |
| [M1](#m1--platform-foundations-days-35) | 3–5 | Platform, crypto, sessions | `docker compose up` over HTTPS. Create a session. Pinning rejects a proxy. |
| [M2](#m2--catalogue-and-profile-days-610) | 6–10 | Catalogue, sync, profile | Both apps load the catalogue, go offline and still search it. Profile edit works. |
| [M3](#m3--payment-flow-days-1115) | 11–15 | Inquiry, confirm, history | A full encrypted payment, then every mock rule on purpose. |
| [M4](#m4--hardening-and-release-days-1620) | 16–20 | Tests, audit, docs, release | Clean-machine `docker compose up`, `v1.0` tag, final demo. |

### Progress tracker

- [ ] [M0 — Bootstrap and contract freeze](#m0--bootstrap-and-contract-freeze-days-12) (4 slices)
- [ ] [M1 — Platform foundations](#m1--platform-foundations-days-35) (10 slices)
- [ ] [M2 — Catalogue and profile](#m2--catalogue-and-profile-days-610) (6 slices)
- [ ] [M3 — Payment flow](#m3--payment-flow-days-1115) (9 slices)
- [ ] [M4 — Hardening and release](#m4--hardening-and-release-days-1620) (8 slices)

### Critical path

```mermaid
flowchart LR
    S01[M0-S1 skeleton] --> S03[M0-S3 OpenAPI draft]
    S01 --> S11[M1-S1 schema]
    S11 --> S12[M1-S2 seed]
    S01 --> S13[M1-S3 error envelope]
    S13 --> S14[M1-S4 headers]
    S14 --> S16[M1-S6 user resolver]
    S01 --> S17[M1-S7 AES-GCM]
    S17 --> S18[M1-S8 sessions]
    S16 --> S18
    S18 --> S19[M1-S9 decryptor + replay]
    S12 --> S21[M2-S1 catalogue]
    S19 --> S32[M3-S2 inquiry]
    S21 --> S32
    S31[M3-S1 engine] --> S32
    S32 --> S34[M3-S4 confirm]
    S34 --> S35[M3-S5 idempotency]
    S35 --> S38[M3-S8 history]
```

---

## M0 — Bootstrap and contract freeze (days 1–2)

- [ ] **M0 complete** (all slices done and the exit demo passed)

**Goal:** the clients are never blocked. By the end of day 2 the contract is published and frozen.
**Exit demo:** the OpenAPI 3.1 file is in `momknpay-contract`, and a Prism mock server started from it answers every endpoint.

- [x] **M0-S1 · Project skeleton**
  - [x] Spring Boot + Java 25 Maven project, package `com.momknpay`, `MomknPayApplication`.
  - [x] Dependencies: web, validation, data-jpa, flyway, postgresql, actuator, springdoc, spring-security-crypto, bucket4j, caffeine, testcontainers.
  - [x] `.gitignore` (`.env`, `certs/`, `target/`), `.env.example` (LLD §10.2), empty `README.md`.
  - [x] **Done when:** `mvn verify` passes, and the app starts against a local Postgres with an empty Flyway history.
  - Refs: HLD §15, LLD §2

- [ ] **M0-S2 · Repository hygiene and CI**
  - [ ] Protected `main`, a pull request template (description, test evidence, linked ticket), conventional-commit guideline in the README. *(template and conventions done; branch protection is set on GitHub once the remote exists)*
  - [x] GitHub Actions: `mvn -B verify` on every pull request.
  - [ ] **Done when:** a test pull request shows a green CI check and cannot merge without approval. *(needs the GitHub remote)*
  - Refs: NFR-MNT-5

- [ ] **M0-S3 · OpenAPI draft (contract)**
  - [x] Write `docs/openapi.yaml` by hand from LLD §6: all 10 endpoints, headers, DTO schemas, examples, the error envelope and every error code.
  - [x] Changelog section with `v1.0.0 — initial contract`.
  - [ ] **Done when:** the spec validates (`npx @redocly/cli lint`), Prism serves it, and both client tracks have reviewed it. *(lint and Prism verified; waiting for the client tracks' review)*
  - Refs: SRS §4, LLD §6

- [ ] **M0-S4 · Publish and freeze**
  - [ ] Push to `momknpay-contract`, add the Postman collection skeleton (folders from LLD §13.5), and announce the freeze. *(Postman skeleton done; pushing to `momknpay-contract` and announcing are manual)*
  - [ ] **Done when:** both client leads acknowledge the freeze. From now on every contract change needs an issue, sign-off from both clients and a version bump. *(manual)*
  - Refs: SRS C-7

---

## M1 — Platform foundations (days 3–5)

- [ ] **M1 complete** (all slices done and the exit demo passed)

**Goal:** everything that later features rely on: database, errors, headers, TLS, Docker, crypto, sessions.
**Exit demo:** `docker compose up` → `https://api.momknpay.local/docs` loads. `POST /sessions` returns a key. A client behind mitmproxy fails to connect because of pinning.

- [ ] **M1-S1 · Database schema**
  - [ ] `V1__schema.sql` exactly as in LLD §4.1 (tables, checks, unique and partial indexes, `transaction_seq`, `updated_at` trigger).
  - [ ] JPA entities, enums and converters (LLD §5), `ddl-auto: validate`.
  - [ ] **Done when:** the app boots with schema validation passing, and `SchemaIT` (Testcontainers) confirms the constraints exist, including the `ux_txn_idempotency` insert-twice failure.
  - Refs: NFR-REL-1…3, LLD §4–5

- [ ] **M1-S2 · Seed: services and users**
  - [ ] `V2__seed_services.sql`: 24 visible and 1 soft-deleted service (LLD §11.1).
  - [ ] `V3__SeedUsers` Java migration with bcrypt(12) PINs (LLD §11.2).
  - [ ] **Done when:** `SeedDataIT` passes: 3 users with working PINs, 6 categories, ≥ 24 visible services, the `_slow` and inactive services present.
  - Refs: FR-SEED-1…3

- [ ] **M1-S3 · Error envelope and global handler**
  - [ ] `ErrorCode` (all 22 codes, EN/AR messages), `ApiException`, `ErrorResponse`, `GlobalExceptionHandler` with the mapping table from LLD §7.2, whitelabel page off.
  - [ ] **Done when:** `ErrorEnvelopeIT` passes: unknown route → `NOT_FOUND`, wrong method → `METHOD_NOT_ALLOWED`, malformed JSON → `VALIDATION_ERROR`, forced exception → `INTERNAL_ERROR` with no stack trace in the body.
  - Refs: FR-COM-3, NFR-REL-4, NFR-SEC-10

- [ ] **M1-S4 · Request ID and required headers**
  - [ ] `RequestIdFilter` (MDC + echo), `RequiredHeadersInterceptor`, `WebConfig` `/v1` prefix, logback pattern with `requestId`.
  - [ ] **Done when:** `HeadersIT` passes: each missing or invalid header → 400 with the right `field`. `/docs` and `/actuator/health` are exempt. Every log line shows the request ID.
  - Refs: FR-COM-1…2, LLD §7.3–7.4, §7.7

- [ ] **M1-S5 · TLS, Dockerfile and Compose**
  - [ ] Certificate generation script or README steps (LLD §13.3), `server.ssl.*`, multi-stage Dockerfile, `docker-compose.yml` with healthchecks.
  - [ ] Publish the **live and backup SPKI pins** to both client tracks.
  - [ ] **Done when:** on a clean clone, `cp .env.example .env` + certs + `docker compose up` starts over HTTPS only (`http://` is refused), and the pins are posted in the contract repository.
  - Refs: NFR-OPS-1…2, NFR-SEC-8, LLD §13

- [ ] **M1-S6 · Current-user resolver**
  - [ ] `@CurrentUser`, `UserRef`, `UserLookupService`, `CurrentUserArgumentResolver`.
  - [ ] **Done when:** unit tests: missing `X-User-Id` → `VALIDATION_ERROR(X-User-Id)`, unknown → `USER_NOT_FOUND`, valid → `UserRef`.
  - Refs: FR-COM-4, LLD §7.5

- [ ] **M1-S7 · AES-GCM cipher and key wrapper**
  - [ ] `AesGcmCipher` (fresh IV per call, 128-bit tag), `KeyWrapper` with AAD = session ID, fail-fast check on `APP_MASTER_KEY`.
  - [ ] **Done when:** U1, U2, U3 and U18 pass (round-trip, tamper, IV uniqueness, AAD binding), and startup fails with a bad master key.
  - Refs: NFR-SEC-1, NFR-SEC-4, LLD §8.1–8.2

- [ ] **M1-S8 · Sessions API**
  - [ ] `SessionService.create/revoke`, `SessionController` (`POST /v1/sessions`, `DELETE /v1/sessions/{id}`), `SessionCleanupJob`.
  - [ ] **Done when:** `SessionIT`: create → 201 with a 32-byte key. Revoke → 204, idempotent. The key is never in the logs and never readable again. Foreign session → 404.
  - Refs: FR-SES-1…5, LLD §6.1–6.2, §8.3

- [ ] **M1-S9 · Payload decryptor and replay guard**
  - [ ] `PayloadDecryptor`, `ReplayGuard` (`REQUIRES_NEW`), `NonceCleanupJob`, a `TestCrypto` helper that mirrors the clients.
  - [ ] **Done when:** U4 and U5 pass. `SessionIT` also covers expired → `SESSION_EXPIRED`, a bad blob → `DECRYPTION_FAILED`, and a replayed nonce → `DECRYPTION_FAILED`.
  - Refs: FR-SES-2, NFR-SEC-2…3, LLD §8.4–8.5

- [ ] **M1-S10 · Rate limiter**
  - [ ] `RateLimiter`, `RateLimitInterceptor` on `/v1/sessions` and `/v1/payments/confirm`, `Retry-After` header.
  - [ ] **Done when:** `RateLimitIT`: the sixth `POST /sessions` in a minute → 429 with `Retry-After`. A different user is unaffected.
  - Refs: FR-SES-6, FR-PAY-11, NFR-SEC-9, LLD §7.6

---

## M2 — Catalogue and profile (days 6–10)

- [ ] **M2 complete** (all slices done and the exit demo passed)

**Goal:** the endpoints behind the offline-first list, search and profile screens.
**Exit demo:** both apps do a first load, then airplane mode, cold start, and the list and search still work. Pull-to-refresh uses `sync`. Profile edit round-trips.

- [ ] **M2-S1 · `GET /v1/services`**
  - [ ] `CatalogService.getAll`, `ServiceItem` / `CatalogResponse` DTOs, a mapper, and ordering by category then `nameEn`.
  - [ ] **Done when:** `CatalogIT`: 24 items, inactive ones included with `isActive: false`, the deleted one absent, money as integers, `syncedAt` in ISO UTC.
  - Refs: FR-CAT-1…3, FR-CAT-7, LLD §6.5

- [ ] **M2-S2 · `GET /v1/services/sync`**
  - [ ] `since` parsing and validation, `>=` filters, `deletedIds`, `syncedAt` captured before the queries.
  - [ ] **Done when:** `CatalogIT`: `since` before the seed → all rows. `since` = now → empty. Updating a row in SQL then calling sync → the row appears. The soft-deleted ID is in `deletedIds`. A bad `since` → 400 `field=since`.
  - Refs: FR-CAT-4…6, LLD §6.6

- [ ] **M2-S3 · `GET /v1/profile`**
  - [ ] `ProfileService.get`, `ProfileController`, `ProfileResponse`.
  - [ ] **Done when:** `ProfileIT`: `usr_01` returns the seeded data. Missing or unknown user → the right errors.
  - Refs: FR-PRO-1, LLD §6.3

- [ ] **M2-S4 · `PATCH /v1/profile`**
  - [ ] `UpdateProfileRequest` validation, trimming and lower-casing, the email uniqueness check, unknown-property rejection (`mobile`).
  - [ ] **Done when:** `ProfileIT`: update name, update email, empty body → 400, `mobile` in the body → 400 `field=mobile`, duplicate email → 409 `EMAIL_ALREADY_USED`.
  - Refs: FR-PRO-2…5, FR-COM-5, LLD §6.4

- [ ] **M2-S5 · Swagger annotations and Postman (catalogue and profile)**
  - [ ] `@Operation` / `@ApiResponse` with examples so the generated spec matches `docs/openapi.yaml`, and the Postman folders 01–03.
  - [ ] **Done when:** the generated `/v3/api-docs` diffs clean against the frozen spec for these endpoints, and the Postman folders run green.
  - Refs: NFR-DOC-1…3

- [ ] **M2-S6 · Wednesday integration fixes (buffer)**
  - [ ] Reserved for mismatches logged at the integration session. Each one is an issue, and any contract change goes through the version bump.
  - [ ] **Done when:** every issue from the session is closed or scheduled.

---

## M3 — Payment flow (days 11–15)

- [ ] **M3 complete** (all slices done and the exit demo passed)

**Goal:** inquiry → confirm → receipt with encryption, idempotency and every mock rule. This is the heaviest week, so protect it.
**Exit demo:** a full payment with `1024750891` (total 25 320) on both devices, then digits 0, 6, 7, 8, 9, a `_slow` service, an inactive service, wrong PIN ×3, an expired inquiry and a retry with the same key.

- [ ] **M3-S1 · Fee calculator and mock engine (pure)**
  - [ ] `FeeCalculator` (`Math.ceilDiv`), `MockPaymentEngine`, `InquiryDecision`, `CustomerNames`, billMonth logic.
  - [ ] **Done when:** U6–U9 pass: worked example 24750 → 500/70/25320, percentage fee rows, every last digit 0–9, the min-amount clamp. No database, no Spring context.
  - Refs: FR-MCK-1, SRS §6, LLD §9.1–9.2

- [ ] **M3-S2 · `POST /v1/payments/inquiry`**
  - [ ] `InquiryService.inquire` with the check order from LLD §6.7, pattern cache, persistence with a 5-min expiry, masked logging.
  - [ ] **Done when:** `PaymentFlowIT` (inquiry part): the digit-1 happy path returns the exact contract JSON. Digit 0 → 404, 9 → 409, a bad pattern → 400 `subscriberNumber`, unknown service → 404, inactive → 503.
  - Refs: FR-INQ-1…9, LLD §9.3

- [ ] **M3-S3 · `_slow` delay**
  - [ ] `SlowServiceDelay` (injectable, so tests can check it without sleeping), applied in a `finally` after the transaction. Virtual threads on.
  - [ ] **Done when:** U10 passes. Manually, `svc_elec_canal_slow` takes ≈ 8 s while other requests stay fast.
  - Refs: FR-MCK-2, NFR-PER-2

- [ ] **M3-S4 · `POST /v1/payments/confirm` — happy path**
  - [ ] `ConfirmService` with a `TransactionTemplate` and the `ConfirmOutcome` model, row lock on the inquiry, PIN check, `ReferenceGenerator`, and `SUCCESS` for digits 1–5.
  - [ ] **Done when:** `PaymentFlowIT`: inquiry → confirm → 200 `SUCCESS`, `reference` in the form `MP-YYYYMMDD-NNNN`, inquiry `CONFIRMED`. Confirming again with a new key → `INQUIRY_ALREADY_CONFIRMED`. U19 passes.
  - Refs: FR-PAY-1, FR-PAY-5, FR-PAY-9…10, LLD §9.4–9.5

- [ ] **M3-S5 · Idempotency**
  - [ ] Replay lookup before decryption, re-check under the lock, catching the unique-constraint violation, `IDEMPOTENCY_CONFLICT`.
  - [ ] **Done when:** U12 and U13 pass. `ConcurrencyIT`: 10 parallel confirms with the same key → 1 row and the same `transactionId` everywhere. A retry of the same bytes (same nonce) returns the receipt instead of `DECRYPTION_FAILED`.
  - Refs: FR-PAY-2…4, NFR-REL-1…2, HLD §8.4

- [ ] **M3-S6 · Failure outcomes on confirm**
  - [ ] Wrong PIN counter (3 → `INVALIDATED`), expired inquiry, `AMOUNT_OUT_OF_RANGE` (digit 6), a `FAILED` transaction plus 402 (digit 7), inactive service → 503.
  - [ ] **Done when:** U11 and U14–U16 pass. Replaying the digit-7 key returns the same 402. `usr_03` with PIN `1234` → `VALIDATION_ERROR(pin)`.
  - Refs: FR-PAY-5…8, SRS §6.1

- [ ] **M3-S7 · PENDING and its resolution**
  - [ ] `PENDING` for digit 8 with `pendingUntil = now + 10s`, `PendingResolver.resolveIfDue` and the `sweep` job.
  - [ ] **Done when:** U17 passes. `PaymentFlowIT`: confirm → `PENDING` with `paidAt: null`. After the clock advances 10 s, the receipt shows `SUCCESS` and `paidAt = pendingUntil`.
  - Refs: FR-TXN-5, HLD §8.5, LLD §9.6

- [ ] **M3-S8 · History and receipt**
  - [ ] `V4__seed_history.sql` (6 transactions for `usr_01`), `TransactionService.list/receipt`, `PageResponse`, pagination validation.
  - [ ] **Done when:** `PaymentFlowIT`: `usr_01` history is newest first with correct paging totals. `usr_02` → empty list. Receipt fields match LLD §6.10. Another user's receipt → 404 `TRANSACTION_NOT_FOUND`. `size=51` → 400.
  - Refs: FR-TXN-1…4, FR-SEED-4, LLD §6.9–6.10, §11.3

- [ ] **M3-S9 · Postman: every mock rule**
  - [ ] Folders 04–06 with the pre-request encrypt helper, and one request per rule and error.
  - [ ] **Done when:** a Postman or Newman run of the whole collection is green on a fresh `docker compose up`.
  - Refs: FR-MCK-3, NFR-DOC-3

---

## M4 — Hardening and release (days 16–20)

- [ ] **M4 complete** (all slices done and the exit demo passed)

**Goal:** prove it, document it, ship it. **Days 18–19 are a feature freeze:** only fixes, tests and docs after that.
**Exit demo (day 20):** a clean machine runs `docker compose up` successfully the first time, the full happy path plus three failure cases are shown, and each intern answers one security question about their own code.

- [ ] **M4-S1 · Log-hygiene audit**
  - [ ] `LogHygieneTest` (I10), plus a manual `grep` of real logs for `1234`, `sessionKey`, `payload` and the full subscriber number. Hibernate bind logging confirmed off.
  - [ ] **Done when:** the test is green and the grep results are attached to the pull request.
  - Refs: NFR-SEC-6, HLD §12

- [ ] **M4-S2 · Test gap sweep**
  - [ ] Fill any missing items from LLD §12 (U1–U19, I1–I10). Remove flaky sleeps in favour of a mutable `Clock`.
  - [ ] **Done when:** `mvn verify` is green in CI with ≥ 8 unit tests (target: all of U1–U19) and every integration test listed.
  - Refs: NFR-MNT-4

- [ ] **M4-S3 · Performance smoke test**
  - [ ] A simple load script (k6 or JMeter) on the catalogue, inquiry and confirm.
  - [ ] **Done when:** p95 < 300 ms for non-`_slow` endpoints on a laptop, with results recorded in the README.
  - Refs: NFR-PER-1

- [ ] **M4-S4 · Final OpenAPI and contract diff**
  - [ ] Export `/v3/api-docs`, diff it against the frozen `docs/openapi.yaml`, and reconcile every difference (code fix, or version bump with client sign-off). Add the CI diff check.
  - [ ] **Done when:** there is no unexplained diff and the contract changelog is up to date.
  - Refs: NFR-DOC-1…2, HLD §14

- [ ] **M4-S5 · README**
  - [ ] Setup steps a stranger can follow, the architecture diagram (from the HLD), decisions taken, known limitations (SRS §8, especially *no auth*), SPKI pins, seeded accounts, the mock-rule cheat sheet.
  - [ ] **Done when:** someone outside the backend track follows it on a clean machine without asking questions.
  - Refs: NFR-DOC-4

- [ ] **M4-S6 · Clean-machine rehearsal**
  - [ ] A fresh clone on a machine that has never run the project: follow the README only.
  - [ ] **Done when:** `docker compose up` works the first time and the Postman collection is green. Fix anything that needed a workaround.
  - Refs: NFR-OPS-1

- [ ] **M4-S7 · Release `v1.0`**
  - [ ] Tag `v1.0`, export the final spec and Postman collection to `momknpay-contract`, and record the 5-minute screen recording (happy path plus 3 failures).
  - [ ] **Done when:** the tag is pushed, the artifacts are published and the recording is shared.

- [ ] **M4-S8 · Reflection and demo prep**
  - [ ] One-page reflection per intern. Rehearse the security questions: why AES-GCM on top of TLS, why a fresh IV, why the idempotency check comes before decryption, why no auth is not secure.
  - [ ] **Done when:** the reflections are submitted and the demo has been rehearsed once end to end.

---

## Definition of done — backend (from the brief, adapted)

- [ ] Every endpoint in SRS §4.6 works against the Dockerised backend and is used by both apps
- [ ] Every error code in SRS §4.4 is reachable, and each one returns the envelope
- [ ] Encrypted payloads round-trip, and a replayed nonce is rejected
- [ ] Idempotency is proven by `ConcurrencyIT` and by a kill-and-retry from a real device
- [ ] No secrets in git history, and no PIN, key or payload in the logs
- [ ] ≥ 8 unit tests passing, CI green
- [ ] Swagger at `/docs` matches the published contract
- [ ] README: how to run, architecture, decisions, known limitations
- [ ] All pull requests reviewed and merged, `v1.0` tagged

## Stretch (only after the definition of done is fully green)

- [ ] **X-1** · Redis-backed rate limiter and nonce store (removes SRS L-4)
- [ ] **X-2** · Add real auth behind `CurrentUserArgumentResolver` (JWT), without changing controllers
- [ ] **X-3** · Cursor-based pagination for history
- [ ] **X-4** · Prometheus metrics (`/actuator/prometheus`) for payment outcomes
