#!/usr/bin/env bash
# SC-231 UC-03: the cover-photo pass.
#
# Reports, per listing, what the pass would do; with --execute it fetches bytes
# (Wikimedia Commons first, Google Places only when nothing free was accepted),
# stores the variants in object storage and writes the one credit row.
#
# The pass is deliberately separate from the ingestion load: a photo source
# outage must not fail a batch. An owner photo is never touched, and a Places
# photo is stored as a cache that expires (30 days), never as a permanent copy.
#
# Usage:
#   ./scripts/attach-cover-photos.sh                       # preview, all listings
#   ./scripts/attach-cover-photos.sh --execute             # fetch and store
#   ./scripts/attach-cover-photos.sh --execute --replace   # refresh even a live cache
#   PHOTO_ARGS="--limit=20 --provenance=certifier-hms" ./scripts/attach-cover-photos.sh
#
# GOOGLE_MAPS_API_KEY is read from the caller's environment and passed through an
# env file (never `-e`), so it does not appear in `docker inspect`.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
out_dir="${PHOTO_OUT_DIR:-$repo_root/docs/research/ingest-sample}"

env_file="$(mktemp)"
chmod 600 "$env_file"
trap 'rm -f "$env_file"' EXIT
{
  echo "APP_PROFILE=dev"
  echo "APP_DB_URL=${APP_DB_URL:-jdbc:postgresql://postgis:5432/halal}"
  echo "APP_DB_USER=${APP_DB_USER:-halal}"
  echo "APP_DB_PASSWORD=${APP_DB_PASSWORD:-halal}"
  # Object storage must be reachable: the pass cannot store bytes without it.
  echo "APP_S3_ENDPOINT=${APP_S3_ENDPOINT:-http://minio:9000}"
  echo "APP_S3_REGION=${APP_S3_REGION:-us-east-1}"
  echo "APP_S3_ACCESS_KEY=${APP_S3_ACCESS_KEY:-minioadmin}"
  echo "APP_S3_SECRET_KEY=${APP_S3_SECRET_KEY:-minioadmin}"
  echo "APP_S3_BUCKET=${APP_S3_BUCKET:-halal}"
  if [ -n "${GOOGLE_MAPS_API_KEY:-}" ]; then
    echo "GOOGLE_MAPS_API_KEY=${GOOGLE_MAPS_API_KEY}"
  fi
} > "$env_file"

docker run --rm \
  --memory=3g --cpus=2 \
  --network tahirs-list_default \
  -v "$repo_root/backend":/app \
  -w /app \
  -v tahirs-list-gradle-test-cache:/gradle-home \
  -v "$out_dir":/artifacts \
  -e GRADLE_USER_HOME=/gradle-home \
  --env-file "$env_file" \
  eclipse-temurin:21-jdk \
  ./gradlew --no-daemon --project-cache-dir=/tmp/gradle-project-cache \
  :bootstrap:attachCoverPhotos \
  -PingestArgs="--out-dir=/artifacts ${PHOTO_ARGS:-}"
