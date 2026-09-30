## Slice / ticket

<!-- e.g. M3-S5 · Idempotency — link the ticket -->

## What and why

<!-- What changed and why. Keep the PR under ~400 changed lines; split it otherwise. -->

## Test evidence

<!-- Test names added/updated, `mvnw verify` result, Postman run or screenshot. -->

## Contract impact

- [ ] No change to paths, fields, status codes or error codes
- [ ] Contract changed — issue: #___, both client tracks signed off, version bumped in the changelog

## Review checklist (docs/CODING_STANDARDS.md §16)

**Contract and behaviour**
- [ ] Matches the frozen OpenAPI spec (paths, fields, status codes, error codes)
- [ ] Every new failure path has a distinct `ErrorCode` and sets `field` when relevant
- [ ] The slice's **Done when** is met

**Design**
- [ ] Controller → service → repository. No entity or repository in the web layer
- [ ] No duplicated mechanism (error, clock, crypto, identity)
- [ ] No single-use interfaces or speculative abstractions

**Correctness**
- [ ] Money is `long` piastres, with integer-only rounding up
- [ ] Time comes from the injected clock, in UTC
- [ ] Writes that must happen once are protected by DB constraints and locks
- [ ] User-owned data is always filtered by user ID

**Security**
- [ ] No secrets in code, config defaults or tests
- [ ] No PIN, key, payload or full subscriber number in logs or `toString()`
- [ ] Crypto only through `AesGcmCipher`, with a fresh IV

**Quality**
- [ ] Tests added or updated, named as behaviours, no `Thread.sleep`
- [ ] `mvnw verify` green
- [ ] Clear names, no dead or commented-out code, `TODO`s reference a ticket
