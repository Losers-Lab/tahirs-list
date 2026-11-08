#!/usr/bin/env bash
# SC-211 UC-03: review, resolve and apply a reviewed ingestion run.
#
# Reads runs/<run-id>/candidates.json (checksum-verified) and prints the diff.
# Any claim conflict or held duplicate becomes an entry in the run's generated
# resolutions.json; on a terminal you answer one question per finding, off a
# terminal the CLI prints exactly what is undecided. With --execute the resolved
# batch is applied in ONE transaction (including curator-approved merges) and
# the run is marked loaded. Runs against the dev PostGIS on the compose network.
#
# Usage:
#   RUN_ID=sample-20261108T120000Z ./scripts/load-ingest-batch.sh
#   RUN_ID=... ./scripts/load-ingest-batch.sh --execute
#   RUN_ID=... LOAD_ARGS="--no-interactive" ./scripts/load-ingest-batch.sh
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"
if [ -z "${RUN_ID:-}" ]; then
  echo "RUN_ID is required (the run directory name under docs/research/ingest-sample/runs)" >&2
  exit 1
fi
# The run id is interpolated into Gradle's -P property and a container path, so
# it must be exactly the shape the artifact store accepts.
if ! [[ "$RUN_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]]; then
  echo "RUN_ID must be 1-64 characters of letters, digits, '.', '_' or '-' (got '$RUN_ID')" >&2
  exit 1
fi
out_dir="${INGEST_OUT_DIR:-$repo_root/docs/research/ingest-sample}"

# Interactive decisions need a TTY; a piped/CI run stays non-interactive (and
# fails instead of blocking on a prompt it cannot answer).
tty_flags=""
interactive_arg=""
if [ -t 0 ]; then
  tty_flags="-it"
  interactive_arg="--interactive"
fi

# DB credentials go through an env file, not `-e`, so the password never
# appears in the docker command line (and therefore in `docker inspect`).
env_file="$(mktemp)"
chmod 600 "$env_file"
trap 'rm -f "$env_file"' EXIT
{
  echo "APP_PROFILE=dev"
  echo "APP_DB_URL=${APP_DB_URL:-jdbc:postgresql://postgis:5432/halal}"
  echo "APP_DB_USER=${APP_DB_USER:-halal}"
  echo "APP_DB_PASSWORD=${APP_DB_PASSWORD:-halal}"
} > "$env_file"

# shellcheck disable=SC2086 # tty_flags is intentionally a flag list
docker run --rm $tty_flags \
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
  :bootstrap:loadIngestBatch \
  -PingestArgs="--out-dir=/artifacts --run-id=$RUN_ID $interactive_arg ${LOAD_ARGS:-}"
