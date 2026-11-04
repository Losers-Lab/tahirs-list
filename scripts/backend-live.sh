#!/usr/bin/env bash
# Launch the Tahir's List live dev-stack backend (Ktor) with real MinIO + seed ingest.
#
# Configuration is environment-variable based (no Spring profiles):
#   APP_DB_URL / APP_DB_USER / APP_DB_PASSWORD  -> Postgres/PostGIS connection
#   APP_S3_*                                    -> real MinIO-backed ImagePort (not the in-memory stub)
#   APP_SEED_INGEST_ENABLED                     -> seed-photo ingest runner runs on boot
#
# WITHOUT APP_S3_ENDPOINT the app boots with InMemoryImagePort and seed-ingest OFF,
# which is why GET /v1/listings/{id}/image 404s in that configuration.
#
# Usage:
#   ./scripts/backend-live.sh     # ensure compose up, (re)start tahirs-list-backend
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"

# 1. Backing services (PostGIS + MinIO) from the backend compose file.
docker compose -f "$repo_root/backend/docker-compose.yml" up -d

# 2. (Re)start the backend container, compiled against the repo mount so `run`
#    picks up live source + resources.
docker rm -f tahirs-list-backend >/dev/null 2>&1 || true
docker run -d \
  --name tahirs-list-backend \
  --restart unless-stopped \
  --network tahirs-list_default \
  -p 8080:8080 \
  -v "$repo_root/backend":/workspace \
  -v tahirs-list-gradle-cache:/root/.gradle \
  -w /workspace \
  -e APP_PROFILE=live \
  -e APP_DB_URL=jdbc:postgresql://postgis:5432/halal \
  -e APP_DB_USER=halal \
  -e APP_DB_PASSWORD=halal \
  -e APP_S3_ENDPOINT="${APP_S3_ENDPOINT:-http://minio:9000}" \
  -e APP_S3_ACCESS_KEY="${APP_S3_ACCESS_KEY:-minioadmin}" \
  -e APP_S3_SECRET_KEY="${APP_S3_SECRET_KEY:-minioadmin}" \
  -e APP_S3_REGION="${APP_S3_REGION:-us-east-1}" \
  -e APP_S3_BUCKET="${APP_S3_BUCKET:-halal}" \
  -e APP_SEED_INGEST_ENABLED="${APP_SEED_INGEST_ENABLED:-true}" \
  eclipse-temurin:21-jdk \
  ./gradlew --no-daemon --project-cache-dir=/tmp/gradle-project-cache :bootstrap:run

echo "tahirs-list-backend started (Ktor, APP_PROFILE=live, seed-photo ingest enabled)."
echo "curl http://localhost:8080/v1/health to poll readiness."
