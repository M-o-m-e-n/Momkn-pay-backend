# CLAUDE.md — Momkn Pay Backend

Instructions for Claude when working in this repository. Follow them on **every** prompt.

## Project in one paragraph

Momkn Pay is a **simulated** bill-payment backend (electricity, water, gas, internet, mobile, landline) for an internship capstone. There is one backend here, and two native clients (iOS and Android) in other repositories that consume a frozen API contract. **There is no authentication module and every endpoint is public.** The user is identified by the `X-User-Id` header, and the AES-256-GCM payload key comes from `POST /v1/sessions` (see `docs/DECISIONS.md`). Stack: **Java 25, Spring Boot, PostgreSQL, Spring Data JPA, Flyway, Maven, Docker Compose.** Package root: `com.momknpay`.

## Sources of truth (read before acting; never contradict them)

| File | Use it for |
|---|---|
| `docs/SRS.md` | Requirements (FR-/NFR- IDs), error codes (§4.4), mock rules (§6), what is out of scope |
| `docs/HLD.md` | Architecture, modules, runtime flows, security design |
| `docs/LLD.md` | Exact schema, classes, DTOs, endpoint JSON, algorithms, config, tests |
| `docs/CODING_STANDARDS.md` | How code must be written. Every rule applies |
| `docs/MILESTONES.md` | The work plan. Every task belongs to a slice (`M<n>-S<n>`) |
| `docs/DECISIONS.md` | Why things are the way they are (ADRs) |
| `docs/*.pdf` | The original brief and sample UI (background only; the docs above override them where they differ) |

If a prompt conflicts with these documents, **stop and say so**. Do not silently pick one. If the user confirms the change, update the affected document(s) in the same piece of work.

## The path: follow these steps on every prompt

### 1. Understand
- Restate the request to yourself in one sentence. If it is ambiguous in a way that changes what you build, ask **one** focused question. Otherwise proceed with the most reasonable reading and state the assumption.
- Classify it: **slice work** · **bug fix** · **question/explanation** · **docs change** · **other**. Questions get an answer, not code changes.

### 2. Locate
- Find the slice in `docs/MILESTONES.md` that the work belongs to. Say which one (for example "This is M3-S5 · Idempotency").
- If the work belongs to no slice, say so and ask whether to add one before writing code.
- Check the slice's dependencies (the critical-path diagram). If a prerequisite slice is not done, say so before continuing.
- Do not start work from a later milestone while the current one has unticked slices, unless the user explicitly asks.

### 3. Read
- Read the SRS requirements, HLD section and LLD section referenced by the slice's `Refs` line.
- Read the existing code you will touch **and** its neighbours, then reuse existing classes: `ApiException`/`ErrorCode`, `TimeProvider`/`Clock`, `AesGcmCipher`, `IdGenerator`, `Masking`, `CurrentUserArgumentResolver`. Never create a second version of something that exists.

### 4. Plan
- For anything bigger than a one-file change, give a short plan first: the files to create or change, the tests to add, and the slice's **Done when** line you are aiming at.
- Keep the change inside one slice and within **~400 changed lines**. If it will be bigger, propose splitting it.

### 5. Implement
- Follow `docs/CODING_STANDARDS.md` exactly. The rules most often broken:
  - controller → service → repository. No repository or entity in `web`
  - money is `long` piastres, with integer rounding up (`Math.ceilDiv`). Never `double`, `float` or `BigDecimal`
  - "now" comes only from the injected `Clock`/`TimeProvider`. Never `Instant.now()`
  - errors are only `throw new ApiException(ErrorCode.X, field)`. No response-shaping `try/catch`
  - constructor injection. Records for DTOs. Never return `null`
  - all AES-GCM goes through `AesGcmCipher`, with a fresh `SecureRandom` IV every time
- Match the contract in `docs/LLD.md` §6 byte for byte: paths, field names, status codes, error codes.
- Schema changes are a **new** Flyway migration. Never edit a merged one.

### 6. Test
- Add or update tests in the same change (see LLD §12 for the named tests U1–U19 and I1–I10).
- Unit tests for pure logic. Integration tests with Testcontainers PostgreSQL (never H2).
- Use a fixed or mutable `Clock`, never `Thread.sleep`.
- Assert on error `code` and `field`, never on message text.

### 7. Verify
- Run `mvn spotless:apply` then `mvn verify`, once the build exists. Report failures with the real output. Never claim a test passed without running it.
- If you cannot run something (no Docker, no database), say exactly what was not verified.
- Self-review against the checklist in `docs/CODING_STANDARDS.md` §16, especially the **Security** items.

### 8. Update the docs
- Tick the boxes you completed in `docs/MILESTONES.md` (`- [ ]` → `- [x]`): task lines as they are finished, the slice when its **Done when** line holds, and the milestone only when all its slices are ticked and its demo has passed.
- If behaviour, the contract or a design choice changed, update SRS/HLD/LLD accordingly. A new significant decision gets an ADR in `docs/DECISIONS.md`.

### 9. Report
- End with a short summary: which slice, what changed (files), how it was verified (commands and results), what is left or not verified, and the next slice.

## Hard rules (never break them, even if asked casually; push back and explain)

1. **Never log, store or return** the PIN, the session key, `payload` (encrypted or decrypted) or the master key. Mask subscriber numbers in logs (`******0891`).
2. **Never commit secrets**: `.env`, keystores, `*.pem`, `*.p12`, keys. Update `.env.example` instead.
3. **Never change the API contract** (paths, fields, status or error codes) without the user confirming the contract-change process (issue, sign-off from both client tracks, version bump in the changelog).
4. **Never add an auth module, JWT, passwords or login** unless the user explicitly reopens ADR-001.
5. **Never weaken idempotency**: the lookup happens before decryption, and the database unique constraint stays.
6. **Never use floating point for money**, and never use a non-injected clock.
7. Do not add dependencies, frameworks or abstractions that the LLD does not call for without asking first.

## Git

- Only commit or push when the user asks.
- Branch: `feature/<slice-id>-<short-name>` (for example `feature/M1-S3-error-envelope`), or `fix/<ticket>-<short-name>`.
- Commits: Conventional Commits, `type(scope): summary`, scope = feature package (`payment`, `session`, `catalog`, `user`, `transaction`, `common`).
- Never push to `main`. Never force-push. Never skip hooks.

## Commands

Use the Maven wrapper. Integration tests (`*IT`) need Docker running (Testcontainers).

| Task | Command |
|---|---|
| Format | `./mvnw spotless:apply` |
| Build + formatting check + all tests | `./mvnw verify` |
| Unit tests only | `./mvnw test` |
| One integration test | `./mvnw verify -Dit.test=SessionIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false` |
| Certificates / SPKI pins | `TLS_KEYSTORE_PASSWORD=... ./scripts/generate-certs.sh` |
| Run locally | `docker compose up --build` → `https://api.momknpay.local/docs` |
| Lint the contract | `npx @redocly/cli lint docs/openapi.yaml` |
| Postman collection against the local stack | `npx newman run postman/momknpay.postman_collection.json --insecure --env-var "baseUrl=https://localhost/v1"` |
| After editing `postman/momkn-encrypt.js` | `node scripts/verify-postman-crypto.js && node scripts/sync-postman-crypto.js` |

## Environment notes

- Windows 11 dev machine. Use forward slashes in paths. Files are UTF-8 with LF line endings.
- Seeded test users: `usr_01` (PIN 1234, has history), `usr_02` (PIN 1234, empty history), `usr_03` (PIN 9999).
- Worked example for sanity checks: subscriber `1024750891` at `svc_elec_cairo` → amountDue 24750, fee 500, VAT 70, total **25320**.
- Mock outcome = last digit of the subscriber number: 0 → not found, 1–5 → success, 6 → above max, 7 → declined, 8 → pending for 10 s, 9 → already paid. `_slow` services add 8 s. Inactive → 503.
