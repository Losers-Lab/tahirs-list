#!/usr/bin/env bash
# SC-211 UC-01: run a sample directory ingestion in the container.
#
# Fetches HMS, HFSAA and HMA once each (cached), geocodes the selected metro
# rows, and writes an immutable reviewed run under docs/research/ingest-sample/.
# It never writes to the catalogue.
#
# Usage:
#   ./scripts/ingest-sample.sh
#   INGEST_ARGS="--metros=Toronto --caps=20 --on-pending=archive" ./scripts/ingest-sample.sh
#   METROS_FILE=./my-metros.json ./scripts/ingest-sample.sh   # alternative catalogue
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
out_dir="${INGEST_OUT_DIR:-$repo_root/docs/research/ingest-sample}"
mkdir -p "$out_dir"

# An alternative metro catalogue is a mounted data file, never a code change.
metros_mount=()
metros_args=""
if [ -n "${METROS_FILE:-}" ]; then
  metros_mount=(-v "$(cd "$(dirname "$METROS_FILE")" && pwd)/$(basename "$METROS_FILE"):/metros.json:ro")
  metros_args="--metros-file=/metros.json"
fi

docker run --rm \
  --memory=3g --cpus=2 \
  -v "$repo_root/backend":/app \
  -w /app \
  -v tahirs-list-gradle-test-cache:/gradle-home \
  -v tahirs-list-certifier-cache:/certifier-cache \
  -v "$out_dir":/artifacts \
  "${metros_mount[@]}" \
  -e GRADLE_USER_HOME=/gradle-home \
  eclipse-temurin:21-jdk \
  ./gradlew --no-daemon --project-cache-dir=/tmp/gradle-project-cache \
  :bootstrap:ingestSample \
  -PingestArgs="--cache-dir=/certifier-cache --out-dir=/artifacts $metros_args ${INGEST_ARGS:-}"
