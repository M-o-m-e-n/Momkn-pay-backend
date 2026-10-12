#!/usr/bin/env bash
# Generates the server's long-term P-256 key pair for the session handshake (ADR-013, LLD §8.3).
#
# Usage:  ./scripts/generate-session-keypair.sh
# Output: certs/session-private.pem  secret: its base64 value goes into .env, never into git
#         certs/session-public.pem   NOT secret: give it to the iOS and Android tracks
#
# An existing key is kept, so re-running only prints the values again. Delete
# certs/session-private.pem to make a new key; every app then needs the new public key.
set -euo pipefail

CERT_DIR="${CERT_DIR:-certs}"
mkdir -p "$CERT_DIR"

if [[ ! -f "$CERT_DIR/session-private.pem" ]]; then
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$CERT_DIR/session-private.pem"
fi
openssl pkey -in "$CERT_DIR/session-private.pem" -pubout -out "$CERT_DIR/session-public.pem"

PRIVATE_B64="$(openssl pkcs8 -topk8 -nocrypt -in "$CERT_DIR/session-private.pem" -outform der | openssl enc -base64 -A)"
PUBLIC_B64="$(openssl pkey -in "$CERT_DIR/session-private.pem" -pubout -outform der | openssl enc -base64 -A)"

echo "Put this line into .env (secret):"
echo "APP_SESSION_PRIVATE_KEY=$PRIVATE_B64"
echo
echo "Server public key for the apps (not secret; also at GET /v1/sessions/public-key):"
echo "$PUBLIC_B64"
