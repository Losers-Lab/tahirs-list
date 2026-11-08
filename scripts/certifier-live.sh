#!/usr/bin/env bash
# SC-211 ad-hoc live probe: one polite pass over HMS, HFSAA and HMA.
# Container-only (the host has no JDK/Gradle). Never part of CI.
set -euo pipefail
if [ -d "$(dirname "$0")/../backend" ]; then
  cd "$(dirname "$0")/../backend"
elif [ -d "$(dirname "$0")/../../backend" ]; then
  cd "$(dirname "$0")/../../backend"
else
  echo "Cannot locate backend/ directory relative to $(dirname "$0")" >&2
  exit 1
fi

docker run --rm \
  --memory=2g --cpus=2 \
  -v "$PWD":/app \
  -w /app \
  -v tahirs-list-gradle-test-cache:/gradle-home \
  -e GRADLE_USER_HOME=/gradle-home \
  -e CERTIFIER_CACHE_DIR=/tmp/tahirs-list-certifier-cache \
  eclipse-temurin:21-jdk \
  ./gradlew --no-daemon --project-cache-dir=/tmp/gradle-project-cache :ingestion:liveProbe
