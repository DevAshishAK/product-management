#!/bin/bash
# Database-per-service: every service gets its OWN database and its OWN login role (no cross-access).
# For production use separate instances/clusters (or managed databases) per service.
set -euo pipefail
for svc in user product order inventory payment invoice notification token; do
  var="${svc^^}_DB_PASSWORD"
  pw="${!var}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-SQL
    CREATE ROLE ${svc}_svc LOGIN PASSWORD '${pw}';
    CREATE DATABASE ${svc}db OWNER ${svc}_svc;
    REVOKE ALL ON DATABASE ${svc}db FROM PUBLIC;
SQL
done
