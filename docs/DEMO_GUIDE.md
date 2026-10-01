# Momkn Pay Backend — Demo Guide

| Item | Value |
|---|---|
| Document | Demo runbook, security Q&A and reflection template (M4-S8) |
| Used for | The 5-minute screen recording and the final 90-minute demo and review (day 20) |
| Related | [README](../README.md) · [SRS](SRS.md) · [DECISIONS](DECISIONS.md) |

---

## 1. Before the demo (T − 30 min)

```bash
docker compose down -v && docker compose up --build -d   # fresh, re-seeded database
docker compose ps                                        # api and db "healthy"
curl -k https://localhost/actuator/health                # {"status":"UP"}
```

- **Phones:** connect them to the same Wi-Fi as the laptop, and point `api.momknpay.local` at the laptop's IP.
- **Certificate:** check the apps pin the SPKI hashes from `certs/pins.txt`. If you regenerated the certificate, both apps need the new pins.
- **Payload key:** check both apps are built with the same key as `APP_PAYLOAD_KEY` in `.env`. A mismatch shows up as `DECRYPTION_FAILED` on every inquiry.
- **Open windows:** a terminal with `docker compose logs -f api`, Swagger (`https://localhost/docs`) and Postman.
- **Test accounts:** `usr_01` / PIN 1234 has history, `usr_02` / 1234 is fresh, `usr_03` / 9999 is for the wrong-PIN path.
- **Rate limit:** 5 confirms per minute per user. Rehearse with different users, or wait a minute between runs.

---

## 2. The 5-minute recording (happy path + three failures)

| Time | Show | Say |
|---|---|---|
| 0:00 | Services list on the phone; airplane mode, then a cold start | "The list renders from the local database; the backend only feeds it through `/services` and `/services/sync`." |
| 0:40 | Search "kahraba" | "Search is on the device; the backend provides both Arabic and English names." |
| 1:00 | Cairo Electricity, subscriber `1024750891` → quote | "The subscriber number leaves the phone AES-GCM-encrypted. The server returns 24750 + 500 + 70 = 25320 piastres, valid for 5 minutes." |
| 1:40 | Enter PIN `1234` → receipt `MP-…` | "The PIN is encrypted too, and checked against a bcrypt hash. The receipt is stored with its own reference." |
| 2:20 | Retry the same confirmation (or kill the app mid-request and retry) | "Same Idempotency-Key, same transaction. Only one row exists; we look the key up before decrypting, so a byte-identical retry still gets its receipt." |
| 3:00 | **Failure 1:** subscriber ending in `0` → "No bill found" | "Every error is a code from the contract: `SUBSCRIBER_NOT_FOUND`, 404." |
| 3:30 | **Failure 2:** ending in `7` → "Payment declined" | "`INSUFFICIENT_BALANCE` is recorded as a declined payment and shows in Activity; the quote stays open for a retry." |
| 4:00 | **Failure 3:** `usr_03` with PIN `1234` three times → "Too many wrong PIN attempts" | "The third wrong PIN invalidates the inquiry: `INQUIRY_INVALIDATED`." |
| 4:30 | The API log window | "Look for a PIN, a key or a subscriber number: only `******0891`. `LogHygieneIT` enforces that on every build." |

**Backup if the phones fail:** the Postman collection runs the same flow. Folders 03–05 cover every rule. Run it in Postman (trust `certs/cert.pem` and set `payloadKey`), or with the Newman command in the README.

---

## 3. Security questions: know your own code

Each intern answers one of these at the final review. The answer is shown in the code, not described from memory.

| Question | Short answer | Where to point |
|---|---|---|
| Why encrypt the payload when we already use TLS? | Defence in depth. Anything that terminates TLS (a proxy, a load balancer, an APM agent logging bodies) would otherwise see the PIN and subscriber number. | `PayloadDecryptor`, HLD §10 |
| Why must the IV be fresh for every message? | GCM with a repeated (key, IV) pair leaks the XOR of the plaintexts and lets an attacker forge tags. It breaks completely, not partially. | `AesGcmCipher.encrypt`, `AesGcmCipherTest.ivIsFreshPerMessage` |
| What stops someone replaying a captured payload? | `ts` must be within ±120 s, and each `nonce` is accepted once (a primary key in `used_nonces`). The nonce stays consumed even if the payment then fails. | `ReplayGuard` (`REQUIRES_NEW`), `PayloadDecryptorIT` |
| Why is the idempotency key checked before decryption? | A genuine retry resends the same bytes, so the same nonce. Decrypting first would reject it as a replay, and the user would never get the receipt. | `ConfirmService` step 1, ADR-008 |
| How do we know two simultaneous retries can't pay twice? | Row lock on the inquiry, a re-check of the key under the lock, and the `ux_txn_idempotency` unique constraint as the backstop. | `ConcurrencyIT` (10 parallel requests → 1 row) |
| Where does the encryption key come from, and who has it? | One static AES-256 key in `APP_PAYLOAD_KEY`, validated at startup and never stored in the database or logged. The same key is built into both apps, so anyone who extracts it from an app build can read and forge payloads (ADR-011). | `PayloadKey`, `StartupValidationTest` |
| Why bcrypt cost 12 when it makes confirm slower? | The cost is what makes a leaked hash expensive to brute-force, which matters most for a 4-digit PIN. It is about 230 ms per check, accepted in ADR-010. | `CoreConfig.pinEncoder`, ADR-010 |
| Is this API secure? | **No.** There is no authentication, so anyone can send any `X-User-Id`, and every client shares one payload key. The encryption protects data in transit and in logs, not against a malicious caller. A real product needs real auth (OAuth2/OIDC), device binding and more. | README "Known limitations", ADR-001 |
| What does certificate pinning protect against, and how do you prove it works? | A trusted-but-wrong CA or an intercepting proxy. It's proven only by running the app through mitmproxy/Charles with its CA trusted and watching the connection fail. | Client apps; `certs/pins.txt` |
| Why integer piastres? | Floating point can't represent 0.1 exactly; 45.50 could become 45.499999. Every amount is a `long`, and rounding uses `Math.ceilDiv`. | `FeeCalculator`, `ArchitectureTest.noFloatingPointFields` |

---

## 4. Reflection template (one page per intern)

Each intern writes their own reflection. This is only the structure:

1. **What I built:** the slices and pull requests I owned, and one piece of code I'm proud of, with a link.
2. **What broke:** one bug or wrong assumption, how it was found (test, review, integration session) and how it was fixed.
3. **What I'd do differently:** one technical decision and one process habit.
4. **What a real product would add:** one security control from the "Known limitations" list and why it matters.
