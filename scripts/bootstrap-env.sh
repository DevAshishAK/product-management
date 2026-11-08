#!/usr/bin/env bash
# Generates .env with random secrets and a fresh RSA key pair for the token service.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] && { echo ".env already exists - refusing to overwrite"; exit 1; }
rnd() { openssl rand -base64 24 | tr -d '/+=' | cut -c1-24; }
tmp=$(mktemp -d)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmp/priv.pem" 2>/dev/null
openssl rsa -in "$tmp/priv.pem" -pubout -out "$tmp/pub.pem" 2>/dev/null
strip() { grep -v '^-----' "$1" | tr -d '\n'; }
OPS=$(rnd); EUREKA=$(rnd); CONFIG=$(rnd)
cat > .env <<EOF
POSTGRES_PASSWORD=$(rnd)
INVENTORY_DB_PASSWORD=$(rnd)
INVOICE_DB_PASSWORD=$(rnd)
NOTIFICATION_DB_PASSWORD=$(rnd)
ORDER_DB_PASSWORD=$(rnd)
PAYMENT_DB_PASSWORD=$(rnd)
PRODUCT_DB_PASSWORD=$(rnd)
TOKEN_DB_PASSWORD=$(rnd)
USER_DB_PASSWORD=$(rnd)
MONGO_USER=pcom
MONGO_PASSWORD=$(rnd)
CONFIG_USER=config
CONFIG_PASSWORD=$CONFIG
EUREKA_USER=eureka
EUREKA_PASSWORD=$EUREKA
OPS_USER=ops
OPS_PASSWORD=$OPS
ADMIN_UI_PASSWORD=$(rnd)
GRAFANA_PASSWORD=$(rnd)
BOOTSTRAP_ADMIN_EMAIL=admin@pcom.local
BOOTSTRAP_ADMIN_PASSWORD=$(rnd)
JWT_PRIVATE_KEY=$(strip "$tmp/priv.pem")
JWT_PUBLIC_KEY=$(strip "$tmp/pub.pem")
API_GATEWAY_SECRET=$(rnd)
ORDER_SERVICE_SECRET=$(rnd)
INVOICE_SERVICE_SECRET=$(rnd)
NOTIFICATION_SERVICE_SECRET=$(rnd)
USER_SERVICE_SECRET=$(rnd)
PRODUCT_SERVICE_SECRET=$(rnd)
INVENTORY_SERVICE_SECRET=$(rnd)
PAYMENT_SERVICE_SECRET=$(rnd)
AUDIT_SERVICE_SECRET=$(rnd)
TOKEN_SERVICE_SECRET=$(rnd)
MONITORING_SERVICE_SECRET=$(rnd)
PARTNER_CLIENT_SECRET=$(rnd)
EOF
# Prometheus reads credentials from files
printf '%s' "$OPS" > docker/prometheus/secrets/ops_password
printf '%s' "$EUREKA" > docker/prometheus/secrets/eureka_password
printf '%s' "$CONFIG" > docker/prometheus/secrets/config_password
rm -rf "$tmp"
echo ".env created. Admin login: admin@pcom.local / see BOOTSTRAP_ADMIN_PASSWORD in .env"
