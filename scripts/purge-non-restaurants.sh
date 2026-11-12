#!/usr/bin/env bash
# SC-231 D-12: remove catalogue rows the admission gate would never admit.
#
# The rule is the gate's own (a shop word with no premises word), not a name
# list. Dry run by default. --execute requires BACKUP=<pg_dump file> that exists
# and is not empty: nothing destructive happens without a dump.
#
# Usage:
#   ./scripts/purge-non-restaurants.sh
#   BACKUP=/path/to/halal.dump ./scripts/purge-non-restaurants.sh --execute
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
out_dir="${PURGE_OUT_DIR:-$repo_root/docs/research/ingest-sample}"

args="${PURGE_ARGS:-}"
if [[ " $* " == *" --execute "* ]]; then
  if [ -z "${BACKUP:-}" ]; then
    echo "BACKUP=<pg_dump file> is required with --execute" >&2
    exit 1
  fi
  # The CLI checks again; this only catches the obvious mistake before Docker runs.
  if [ ! -s "$BACKUP" ]; then
    echo "refusing: backup '$BACKUP' does not exist or is empty" >&2
    exit 1
  fi
  # Mount the dump directory so the container can see the same file.
  backup_dir="$(cd "$(dirname "$BACKUP")" && pwd)"
  backup_name="$(basename "$BACKUP")"
  args="$args --backup=/backup/$backup_name"
else
  backup_dir="$out_dir"
fi

env_file="$(mktemp)"
chmod 600 "$env_file"
trap 'rm -f "$env_file"' EXIT
{
  echo "APP_PROFILE=dev"
  echo "APP_DB_URL=${APP_DB_URL:-jdbc:postgresql://postgis:5432/halal}"
  echo "APP_DB_USER=${APP_DB_USER:-halal}"
  echo "APP_DB_PASSWORD=${APP_DB_PASSWORD:-halal}"
} > "$env_file"

docker run --rm \
  --memory=3g --cpus=2 \
  --network tahirs-list_default \
  -v "$repo_root/backend":/app \
  -w /app \
  -v tahirs-list-gradle-test-cache:/gradle-home \
  -v "$backup_dir":/backup:ro \
  -v "$out_dir":/artifacts \
  -e GRADLE_USER_HOME=/gradle-home \
  --env-file "$env_file" \
  eclipse-temurin:21-jdk \
  ./gradlew --no-daemon --project-cache-dir=/tmp/gradle-project-cache \
  :bootstrap:purgeNonRestaurants \
  -PingestArgs="--out-dir=/artifacts $args"
