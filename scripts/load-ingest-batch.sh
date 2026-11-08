#!/usr/bin/env bash
# SC-211 UC-03: preview and apply a reviewed ingestion run.
#
# Reads runs/<run-id>/candidates.json (checksum-verified), prints the diff, and
# with --execute applies it in one transaction through the runtime repository,
# then marks the run loaded. Runs against the dev PostGIS on the compose network.
#
# Usage:
#   RUN_ID=sample-20261108T120000Z ./scripts/load-ingest-batch.sh
#   RUN_ID=... ./scripts/load-ingest-batch.sh --execute
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
if [ -z "${RUN_ID:-}" ]; then
  echo "RUN_ID is required (the run directory name under docs/research/ingest-sample/runs)" >&2
  exit 1
fi
out_dir="${INGEST_OUT_DIR:-$repo_root/docs/research/ingest-sample}"

docker run --rm \
  --memory=3g --cpus=2 \
  --network tahirs-list_default \
  -v "$repo_root/backend":/app \
  -w /app \
  -v tahirs-list-gradle-test-cache:/gradle-home \
  -v "$out_dir":/artifacts \
  -e GRADLE_USER_HOME=/gradle-home \
  -e APP_PROFILE=dev \
  -e APP_DB_URL="${APP_DB_URL:-jdbc:postgresql://postgis:5432/halal}" \
  -e APP_DB_USER="${APP_DB_USER:-halal}" \
  -e APP_DB_PASSWORD="${APP_DB_PASSWORD:-halal}" \
  eclipse-temurin:21-jdk \
  ./gradlew --no-daemon --project-cache-dir=/tmp/gradle-project-cache \
  :bootstrap:loadIngestBatch \
  -PingestArgs="--out-dir=/artifacts --run-id=$RUN_ID ${LOAD_ARGS:-}"
