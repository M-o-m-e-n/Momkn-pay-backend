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

## Run locally

Full Docker Compose + HTTPS instructions arrive with milestone M1 (slice M1-S5). Until then:

```bash
docker run -d --name momknpay-db -e POSTGRES_DB=momknpay -e POSTGRES_USER=momknpay \
  -e POSTGRES_PASSWORD=change-me -p 5432:5432 postgres:16-alpine
DB_PASSWORD=change-me ./mvnw spring-boot:run
```

## API contract

The contract is [`docs/openapi.yaml`](docs/openapi.yaml) (OpenAPI 3.1), mirrored to the `momknpay-contract` repository together with [`postman/momknpay.postman_collection.json`](postman/momknpay.postman_collection.json).

- **Frozen** since day 3. A change needs an issue, sign-off from both client tracks, and a new row in the changelog inside `info.description` with a version bump.
- Lint: `npx @redocly/cli lint docs/openapi.yaml`
- Mock server for client teams (no backend needed): `npx @stoplight/prism-cli mock docs/openapi.yaml` → `http://127.0.0.1:4010`

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
