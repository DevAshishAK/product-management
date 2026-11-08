#!/usr/bin/env bash
# Event replay helpers.
#  A) Rebuild a read model / new consumer: give the consumer a NEW group id (or reset an existing one) and re-read the topic:
#       ./scripts/replay-events.sh reset <group> <topic> [--to-earliest | --to-datetime 2026-01-01T00:00:00.000]
#  B) Re-publish retained outbox rows of a producer for a time window (needs an ADMIN token):
#       ./scripts/replay-events.sh outbox <service-url> <admin-token> <from-iso> <to-iso>
#  Consumers are idempotent (processed_event table); delete the rows for that consumer first when replaying into the SAME group.
set -euo pipefail
BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:29092}"
case "${1:-}" in
  reset)
    shift; group="$1"; topic="$2"; shift 2
    kafka-consumer-groups.sh --bootstrap-server "$BOOTSTRAP" --group "$group" --topic "$topic" --reset-offsets "${@:---to-earliest}" --execute ;;
  outbox)
    curl -fsS -X POST -H "Authorization: Bearer $3" "$2/api/v1/admin/outbox/replay?from=$4&to=$5" ;;
  *) sed -n '2,9p' "$0"; exit 1 ;;
esac
