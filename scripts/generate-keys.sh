#!/usr/bin/env bash
# Prints JWT_PRIVATE_KEY / JWT_PUBLIC_KEY (base64 PKCS#8 / X.509, single line) for the token service.
set -euo pipefail
tmp=$(mktemp -d)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/priv.pem" 2>/dev/null
openssl rsa -in "$tmp/priv.pem" -pubout -out "$tmp/pub.pem" 2>/dev/null
echo "JWT_PRIVATE_KEY=$(grep -v '^-----' "$tmp/priv.pem" | tr -d '\n')"
echo "JWT_PUBLIC_KEY=$(grep -v '^-----' "$tmp/pub.pem" | tr -d '\n')"
rm -rf "$tmp"
