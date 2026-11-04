#!/usr/bin/env bash
# Containerized SvelteKit dev server for the Tahir's List frontend.
# Host requirements: Docker only. No Node/pnpm needed on the machine.
#
# Runs on http://localhost:5173 and proxies /v1/* to the backend via
# API_PROXY_TARGET (default inside the container: host.docker.internal:8080,
# so a backend running on the host or in a published container is reachable).
#
# Usage:
#   ./scripts/frontend-dev.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -d "$SCRIPT_DIR/../frontend" ]; then
  FRONTEND_DIR="$SCRIPT_DIR/../frontend"
elif [ -d "$SCRIPT_DIR/../../frontend" ]; then
  FRONTEND_DIR="$SCRIPT_DIR/../../frontend"
else
  echo "Cannot locate frontend/ directory relative to $SCRIPT_DIR" >&2
  exit 1
fi

cd "$FRONTEND_DIR"

# Git Bash (MSYS) rewrites `/app`-style arguments into Windows paths and maps
# `$(pwd)` to `/c/...`, which Docker rejects. Use the Windows path form and
# disable the rewrite for the rest of the script. Linux/macOS are unaffected.
HOST_DIR="$(pwd)"
case "$(uname -s)" in
  MINGW*|MSYS*)
    HOST_DIR="$(pwd -W)"
    export MSYS_NO_PATHCONV=1
    ;;
esac

IMAGE="${FRONTEND_IMAGE:-node:24-slim}"
PNPM_STORE_VOLUME="${FRONTEND_PNPM_STORE_VOLUME:-tahirs-list-pnpm-store}"
NODE_MODULES_VOLUME="${FRONTEND_NODE_MODULES_VOLUME:-tahirs-list-frontend-node_modules}"
# The self-hosted backend is published on host port 8081 (8080 inside its container).
API_TARGET="${API_PROXY_TARGET:-http://host.docker.internal:8081}"

docker run --rm -it \
  --memory="${FRONTEND_MEMORY_LIMIT:-1g}" \
  --cpus="${FRONTEND_CPU_LIMIT:-2}" \
  --pids-limit=512 \
  --add-host=host.docker.internal:host-gateway \
  -p 5173:5173 \
  -v "$HOST_DIR":/app \
  -w /app \
  -v "$NODE_MODULES_VOLUME":/app/node_modules \
  -v "$PNPM_STORE_VOLUME":/pnpm-store \
  -e API_PROXY_TARGET="$API_TARGET" \
  -e NODE_OPTIONS="--max-old-space-size=768" \
  "$IMAGE" \
  bash -lc "corepack enable >/dev/null 2>&1; pnpm config set store-dir /pnpm-store >/dev/null 2>&1; pnpm install --frozen-lockfile && pnpm dev --host 0.0.0.0"
