# Momkn Pay Backend — Release Notes and Checklist

| Item | Value |
|---|---|
| Document | Release record |
| Related | [MILESTONES.md](MILESTONES.md) (M4) · [README](../README.md) |

---

## 1. Clean-machine rehearsal (M4-S6)

**Goal:** someone who has never seen the project follows the README only, and `docker compose up` works the first time.

**Run on 2026-09-30** from a fresh `git clone` of `main` (commit `a50c8fc`) into an empty folder, with no `.env`, no `certs/` and no build output. Windows 11, Git Bash, Docker Desktop.

| Step (README → Quick start) | Result |
|---|---|
| `cp .env.example .env` and fill `DB_PASSWORD`, `APP_MASTER_KEY`, `TLS_KEYSTORE_PASSWORD` | ✅ |
| `TLS_KEYSTORE_PASSWORD=… ./scripts/generate-certs.sh` | ✅ keystore and live/backup pins written |
| `docker compose up --build -d` | ✅ `db` and `api` healthy after about 30 s |
| `curl -k https://localhost/actuator/health` | ✅ `UP` |
| `curl -k https://localhost/v1/services` with the three headers | ✅ catalogue returned |
| `curl --cacert certs/cert.pem --resolve …` against `api.momknpay.local` | ✅ the certificate validates against its own CA |
| `https://localhost/docs` | ✅ 302 to Swagger UI |
| `npx newman run postman/momknpay.postman_collection.json …` | ✅ 38/38 assertions |
| `./mvnw verify` in the clean clone | ✅ 88 unit + 110 integration tests, coverage gate met |

**Workarounds needed:** none.

**Not covered by this rehearsal:** a machine without Git Bash, where `generate-certs.sh` needs a POSIX shell with OpenSSL. On plain Windows `cmd`/PowerShell, use Git Bash or WSL; the README says so.

---

> **Superseded details:** this rehearsal ran on v1.0, which still had `APP_MASTER_KEY` and sessions. Since v2.0.0 (ADR-011) the variable is `APP_PAYLOAD_KEY` and Newman runs with `--ssl-extra-ca-certs certs/cert.pem` and a `payloadKey` variable; see §4.

## 2. Release checklist

| # | Item | Status |
|---|---|---|
| 1 | `./mvnw verify` green on `main` (formatting, tests, contract, architecture, coverage) | ✅ |
| 2 | Clean-machine rehearsal (§1) | ✅ |
| 3 | Newman run of the whole collection against Docker | ✅ 38/38 |
| 4 | Log audit: no PIN, key, payload or full subscriber number in real logs | ✅ (M4-S1) |
| 5 | gitleaks over the full history | ✅ no leaks outside the documented allowlist |
| 6 | Performance smoke test within budget | ✅ (M4-S3, ADR-010) |
| 7 | `docs/openapi.yaml` matches the code (`OpenApiContractIT`) and lints clean | ✅ |
| 8 | Version set to `1.0.0` and tag `v1.0` created | ✅ |
| 9 | Contract and Postman collection copied to `momknpay-contract` | ⏳ manual, needs the remote |
| 10 | 5-minute screen recording: happy path + three failure cases | ⏳ manual |
| 11 | SPKI pins posted to both client tracks | ⏳ manual |
| 12 | Branch protection on `main` and a green CI run on GitHub | ⏳ needs the remote |

---

## 3. Release v1.0 (M4-S7)

| Item | Value |
|---|---|
| Version | `1.0.0` (`pom.xml`, OpenAPI `info.version`) |
| Tag | `v1.0` (annotated) on `main` |
| Contract | `docs/openapi.yaml` v1.0.0, unchanged since the freeze |
| Artifacts | Docker image from `docker compose build`; `docs/openapi.yaml`; `postman/momknpay.postman_collection.json` |
| Changes | [CHANGELOG.md](../CHANGELOG.md) |

To publish once the remote exists:

```bash
git push origin main --follow-tags
# then copy docs/openapi.yaml and the Postman collection to momknpay-contract
```

---

## 4. Version 2.0.0 — sessions removed (M5)

| Check | Result |
|---|---|
| `./mvnw verify` | ✅ 87 unit + 99 integration tests, coverage gate met |
| Upgrade in place: migration V5 on an existing v1.0 database with data | ✅ applied, data kept |
| `POST /v1/sessions` | ✅ `404 NOT_FOUND` |
| Newman with certificate verification on and the shared key | ✅ 34/34 assertions |
| Newman without trusting `certs/cert.pem` | ✅ refused: "self-signed certificate" |
| Performance smoke test | ✅ within budget (confirm p95 405 ms of 500) |
| Contract `docs/openapi.yaml` v2.0.0 matches the code and lints clean | ✅ |

Still manual: telling both client tracks about the breaking change, giving them the shared key, and tagging `v2.0`.
