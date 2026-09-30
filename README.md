# Momkn Pay — Backend

Simulated bill-payment API for the Momkn Pay internship capstone (electricity, water, gas, internet, mobile top-up, landline). No real money moves; every payment outcome is decided by a deterministic mock engine.

> **No authentication module.** Every endpoint is public and the caller names the user with the `X-User-Id` header. This is a deliberate teaching trade-off — see [docs/DECISIONS.md](docs/DECISIONS.md) (ADR-001). Do not deploy this anywhere real.

## Stack

Java 25 · Spring Boot 4 · PostgreSQL 16 · Spring Data JPA · Flyway · Maven · Docker Compose

## Prerequisites

- JDK 25
- Docker (for PostgreSQL and for the integration tests, which use Testcontainers)

## Build and test

```bash
./mvnw verify          # unit tests (*Test) + integration tests (*IT, need Docker)
./mvnw test            # unit tests only
```

## Run locally (Docker Compose, HTTPS)

```bash
cp .env.example .env                       # fill DB_PASSWORD, APP_MASTER_KEY, TLS_KEYSTORE_PASSWORD
TLS_KEYSTORE_PASSWORD=<same as in .env> ./scripts/generate-certs.sh
# hosts file: 127.0.0.1 api.momknpay.local
docker compose up --build
```

- API: `https://api.momknpay.local/v1` · Swagger UI: `https://api.momknpay.local/docs` · health: `/actuator/health`
- HTTPS only — there is no HTTP listener.
- `APP_MASTER_KEY`: `openssl rand -base64 32`.

### Certificate and SPKI pins

`scripts/generate-certs.sh` writes a self-signed certificate for `api.momknpay.local`, the keystore, an offline backup key, and `certs/pins.txt` with the **live** and **backup** SPKI SHA-256 pins.

Every run creates a new key, so the backend track generates the certificate **once**, shares `certs/keystore.p12` privately (never through git), and publishes the two pins from `pins.txt` to the iOS and Android tracks and the contract repository. Clients pin both hashes so the key can be rotated to the backup.

Check a running server against the certificate:

```bash
curl --cacert certs/cert.pem https://api.momknpay.local/actuator/health
```

## API contract

The contract is [`docs/openapi.yaml`](docs/openapi.yaml) (OpenAPI 3.1), mirrored to the `momknpay-contract` repository together with [`postman/momknpay.postman_collection.json`](postman/momknpay.postman_collection.json).

- **Frozen** since day 3. A change needs an issue, sign-off from both client tracks, and a new row in the changelog inside `info.description` with a version bump.
- Lint: `npx @redocly/cli lint docs/openapi.yaml`
- Mock server for client teams (no backend needed): `npx @stoplight/prism-cli mock docs/openapi.yaml` → `http://127.0.0.1:4010`
- Postman collection: every endpoint and every mock rule (folders 01–06). Run it against the local stack with
  `npx newman run postman/momknpay.postman_collection.json --insecure --env-var "baseUrl=https://localhost/v1"`.
  Encrypted payloads come from `postman/momkn-encrypt.js` (AES-256-GCM in plain JavaScript, because the Postman sandbox has none).
  After editing it: `node scripts/verify-postman-crypto.js && node scripts/sync-postman-crypto.js`.

## Performance

`node scripts/perf-smoke.js` measures p50/p95/max per endpoint against a running stack (it writes payments, so use a throwaway database: `docker compose up -d`, run it, then `docker compose down -v`). Latest run on a developer laptop (Docker Desktop, 16 vCPU), 200 requests per endpoint at concurrency 10:

| Endpoint | p50 ms | p95 ms | Budget |
|---|---|---|---|
| `GET /services` | 9.8 | 16.6 | 300 |
| `GET /services/sync` | 9.3 | 12.3 | 300 |
| `GET /profile` | 6.9 | 10.4 | 300 |
| `GET /payments/transactions` | 13.0 | 22.5 | 300 |
| `GET /payments/transactions/{id}` | 16.4 | 35.6 | 300 |
| `POST /payments/inquiry` | 29.6 | 50.3 | 300 |
| `POST /payments/confirm` (10 samples, rate-limited) | 239.9 | 277.5 | 500 |

Confirm is dominated by the bcrypt cost-12 PIN check (~230 ms by itself) — a deliberate security cost, see [ADR-010](docs/DECISIONS.md).

## Contributing

- `main` is protected: every change goes through a pull request with one peer and one mentor approval, and a green CI check.
- One slice (see [Milestones](docs/MILESTONES.md)) = one branch = one pull request, at most ~400 changed lines.
- Branches: `feature/<slice-id>-<short-name>` (e.g. `feature/M1-S3-error-envelope`) or `fix/<ticket>-<short-name>`.
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/): `type(scope): summary`, for example `feat(payment): return stored transaction for repeated idempotency key`. Scope is the feature package: `payment`, `session`, `catalog`, `user`, `transaction`, `common`.
- Run `./mvnw spotless:apply` before committing; `./mvnw verify` must pass.

## Documentation

| Document | Purpose |
|---|---|
| [SRS](docs/SRS.md) | Requirements, error codes, mock rules |
| [HLD](docs/HLD.md) | Architecture |
| [LLD](docs/LLD.md) | Schema, classes, API details |
| [Coding standards](docs/CODING_STANDARDS.md) | How code is written here |
| [Milestones](docs/MILESTONES.md) | Delivery plan and progress |
| [Decisions](docs/DECISIONS.md) | Why things are the way they are |
