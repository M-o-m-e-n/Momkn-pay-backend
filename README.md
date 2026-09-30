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

## Documentation

| Document | Purpose |
|---|---|
| [SRS](docs/SRS.md) | Requirements, error codes, mock rules |
| [HLD](docs/HLD.md) | Architecture |
| [LLD](docs/LLD.md) | Schema, classes, API details |
| [Coding standards](docs/CODING_STANDARDS.md) | How code is written here |
| [Milestones](docs/MILESTONES.md) | Delivery plan and progress |
| [Decisions](docs/DECISIONS.md) | Why things are the way they are |
