#!/usr/bin/env bash
# Generates the self-signed TLS certificate for api.momknpay.local, the PKCS#12 keystore the API
# loads, and the SPKI SHA-256 pins both mobile clients embed (docs/LLD.md §13.3).
#
# Usage:  TLS_KEYSTORE_PASSWORD=... ./scripts/generate-certs.sh
# Output: certs/ (git-ignored). Never commit anything from certs/.
set -euo pipefail

CERT_DIR="${CERT_DIR:-certs}"
HOST="api.momknpay.local"
DAYS=365
: "${TLS_KEYSTORE_PASSWORD:?Set TLS_KEYSTORE_PASSWORD (same value as in .env)}"

# Git Bash on Windows rewrites "/CN=..." into a path unless told not to.
export MSYS_NO_PATHCONV=1

mkdir -p "$CERT_DIR"

spki_pin() { # $1 = public key in PEM
  openssl pkey -pubin -in "$1" -outform der | openssl dgst -sha256 -binary | openssl enc -base64
}

# 1. live key + self-signed certificate (EC P-256, SAN for the API host and localhost)
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes \
  -keyout "$CERT_DIR/key.pem" -out "$CERT_DIR/cert.pem" -days "$DAYS" \
  -subj "/CN=$HOST/O=Momkn Pay Capstone" \
  -addext "subjectAltName=DNS:$HOST,DNS:localhost,IP:127.0.0.1" \
  -addext "keyUsage=digitalSignature" \
  -addext "extendedKeyUsage=serverAuth" 2>/dev/null

# 2. PKCS#12 keystore loaded by Spring Boot (alias must match server.ssl.key-alias)
openssl pkcs12 -export -in "$CERT_DIR/cert.pem" -inkey "$CERT_DIR/key.pem" \
  -name momknpay -out "$CERT_DIR/keystore.p12" -passout "pass:$TLS_KEYSTORE_PASSWORD"

# 3. backup key for rotation: NOT deployed, keep it offline
if [[ ! -f "$CERT_DIR/backup-key.pem" ]]; then
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$CERT_DIR/backup-key.pem"
fi

openssl x509 -in "$CERT_DIR/cert.pem" -pubkey -noout > "$CERT_DIR/live-pub.pem"
openssl pkey -in "$CERT_DIR/backup-key.pem" -pubout -out "$CERT_DIR/backup-pub.pem"
LIVE_PIN="$(spki_pin "$CERT_DIR/live-pub.pem")"
BACKUP_PIN="$(spki_pin "$CERT_DIR/backup-pub.pem")"

cat > "$CERT_DIR/pins.txt" <<EOF
host:   $HOST
live:   sha256/$LIVE_PIN
backup: sha256/$BACKUP_PIN
EOF

echo "Keystore: $CERT_DIR/keystore.p12"
echo "SPKI pins (publish both to the iOS and Android tracks):"
cat "$CERT_DIR/pins.txt"
