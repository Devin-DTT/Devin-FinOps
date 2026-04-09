#!/usr/bin/env bash
# dump-raw-data.sh - Dumps all cached endpoint data from Redis to a JSON file
#
# Usage:
#   ./scripts/dump-raw-data.sh [--output <path>] [--filter <pattern>]
#
# Options:
#   --output <path>     Output file path (default: ./raw-endpoint-data.json)
#   --filter <pattern>  Filter endpoints by pattern (e.g. "list_sessions*")
#
set -euo pipefail

# ---------------------------------------------------------------------------
# Defaults
# ---------------------------------------------------------------------------
OUTPUT_FILE="./raw-endpoint-data.json"
FILTER=""
KEY_PREFIX="finops:endpoint:"
COMPOSE_PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output)
      OUTPUT_FILE="$2"
      shift 2
      ;;
    --filter)
      FILTER="$2"
      shift 2
      ;;
    *)
      # Legacy positional: first arg = output file
      OUTPUT_FILE="$1"
      shift
      ;;
  esac
done

# ---------------------------------------------------------------------------
# Helper: print coloured messages
# ---------------------------------------------------------------------------
info()  { echo -e "\033[1;34m[INFO]\033[0m  $*"; }
warn()  { echo -e "\033[1;33m[WARN]\033[0m  $*"; }
error() { echo -e "\033[1;31m[ERROR]\033[0m $*"; }

# ---------------------------------------------------------------------------
# Pre-flight: verify Redis container is running
# ---------------------------------------------------------------------------
info "Checking Redis container status..."
if ! docker compose -f "$COMPOSE_PROJECT_DIR/docker-compose.yml" ps redis 2>/dev/null | grep -q "running"; then
  error "Redis container is not running. Start services first: docker compose up -d"
  exit 1
fi
info "Redis container is running."

# ---------------------------------------------------------------------------
# Try REST endpoint first (more reliable than parsing redis-cli)
# ---------------------------------------------------------------------------
REST_URL="http://localhost:8080/api/dump"
if [ -n "$FILTER" ]; then
  REST_URL="${REST_URL}?filter=${FILTER}"
fi

if curl -sf "$REST_URL" > /dev/null 2>&1; then
  info "Using REST endpoint at $REST_URL ..."
  if command -v jq &> /dev/null; then
    curl -s "$REST_URL" | jq '.' > "$OUTPUT_FILE"
  else
    curl -s "$REST_URL" > "$OUTPUT_FILE"
  fi

  # Summary
  if command -v jq &> /dev/null; then
    TOTAL=$(jq '.total_endpoints' "$OUTPUT_FILE")
    KEYS_WITH_DATA=$(jq '[.endpoints | to_entries[] | select(.value.raw_data != null)] | length' "$OUTPUT_FILE")
  else
    TOTAL="(install jq to see)"
    KEYS_WITH_DATA="(install jq to see)"
  fi
  FILE_SIZE=$(du -h "$OUTPUT_FILE" | cut -f1)
  info "Summary:"
  info "  Total endpoints:       $TOTAL"
  info "  Endpoints with data:   $KEYS_WITH_DATA"
  info "  Output file:           $OUTPUT_FILE"
  info "  File size:             $FILE_SIZE"
  exit 0
fi

warn "REST endpoint not available, falling back to redis-cli method..."

# ---------------------------------------------------------------------------
# Fallback: direct Redis access via docker compose exec
# ---------------------------------------------------------------------------
KEY_PATTERN="${KEY_PREFIX}*"
if [ -n "$FILTER" ]; then
  KEY_PATTERN="${KEY_PREFIX}${FILTER}"
fi

info "Fetching keys matching: $KEY_PATTERN"
KEYS=$(docker compose -f "$COMPOSE_PROJECT_DIR/docker-compose.yml" exec -T redis redis-cli KEYS "$KEY_PATTERN" | tr -d '\r')

if [ -z "$KEYS" ]; then
  warn "No keys found matching pattern: $KEY_PATTERN"
  KEYS=""
fi

# Build JSON using a temp file
TMPFILE=$(mktemp)
trap 'rm -f "$TMPFILE"' EXIT

echo "{" > "$TMPFILE"
echo "  \"generated_at\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"," >> "$TMPFILE"

FIRST=true
ENDPOINT_JSON=""
COUNT=0
DATA_COUNT=0

for KEY in $KEYS; do
  # Skip empty lines
  [ -z "$KEY" ] && continue

  VALUE=$(docker compose -f "$COMPOSE_PROJECT_DIR/docker-compose.yml" exec -T redis redis-cli GET "$KEY" | tr -d '\r')
  ENDPOINT_NAME="${KEY#$KEY_PREFIX}"

  if [ "$FIRST" = true ]; then
    FIRST=false
  else
    ENDPOINT_JSON="${ENDPOINT_JSON},"
  fi

  # Try to include value as raw JSON; fallback to null
  if [ -n "$VALUE" ] && [ "$VALUE" != "" ]; then
    ENDPOINT_JSON="${ENDPOINT_JSON}
    \"${ENDPOINT_NAME}\": {
      \"redis_key\": \"${KEY}\",
      \"raw_data\": ${VALUE}
    }"
    DATA_COUNT=$((DATA_COUNT + 1))
  else
    ENDPOINT_JSON="${ENDPOINT_JSON}
    \"${ENDPOINT_NAME}\": {
      \"redis_key\": \"${KEY}\",
      \"raw_data\": null
    }"
  fi
  COUNT=$((COUNT + 1))
done

echo "  \"total_endpoints\": ${COUNT}," >> "$TMPFILE"
echo "  \"endpoints\": {${ENDPOINT_JSON}" >> "$TMPFILE"
echo "  }" >> "$TMPFILE"
echo "}" >> "$TMPFILE"

# Format with jq if available
if command -v jq &> /dev/null; then
  jq '.' "$TMPFILE" > "$OUTPUT_FILE"
else
  warn "jq not installed - output will not be pretty-printed"
  mv "$TMPFILE" "$OUTPUT_FILE"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
FILE_SIZE=$(du -h "$OUTPUT_FILE" | cut -f1)
info "Summary:"
info "  Keys found:            $COUNT"
info "  Keys with data:        $DATA_COUNT"
info "  Output file:           $OUTPUT_FILE"
info "  File size:             $FILE_SIZE"
info "Done."
