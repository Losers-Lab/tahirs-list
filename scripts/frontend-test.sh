#!/usr/bin/env bash
# Containerized build/test for the Tahir's List SvelteKit frontend.
# Host requirements: Docker only. No Node/pnpm needed on the machine.
#
# Usage:
#   ./scripts/frontend-test.sh              # frozen install + check + test + build
#   ./scripts/frontend-test.sh dev-install  # non-frozen install (lockfile drift)
#   ./scripts/frontend-test.sh test-only    # skipped install, runs check + test
#
# Resource guards (memory is a first-class requirement):
#   - jsdom + a bounded Vitest fork pool (maxWorkers=2) — never Playwright/Chromium
#   - Rolldown worker threads are capped; the pool otherwise sizes to host cores
#     and ignores the container CPU quota (rolldown issue #10812)
#   - hard --memory / --cpus ceilings on the container itself
set -euo pipefail

# Works from repo root OR from a worktree (e.g. .worktrees/...) — finds the nearest frontend/ dir.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -d "$SCRIPT_DIR/../frontend" ]; then
  FRONTEND_DIR="$SCRIPT_DIR/../frontend"
elif [ -d "$SCRIPT_DIR/../../frontend" ]; then
  FRONTEND_DIR="$SCRIPT_DIR/../../frontend"   # repo-root/scripts sibling
else
  echo "Cannot locate frontend/ directory relative to $SCRIPT_DIR" >&2
  exit 1
fi

MODE="${1:-ci}"
case "$MODE" in
  ci|dev-install|test-only) ;;
  *)
    echo "Unknown mode: $MODE (use 'ci', 'dev-install' or 'test-only')" >&2
    exit 1
    ;;
esac

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
MEMORY_LIMIT="${FRONTEND_MEMORY_LIMIT:-2g}"
CPU_LIMIT="${FRONTEND_CPU_LIMIT:-2}"
BUILD_HEAP_MB="${FRONTEND_BUILD_HEAP_MB:-1536}"

INSTALL_CMD="pnpm install --frozen-lockfile"
if [ "$MODE" = "dev-install" ]; then
  INSTALL_CMD="pnpm install --no-frozen-lockfile"
fi

if [ "$MODE" = "test-only" ]; then
  RUN_CMD="pnpm check && pnpm test"
else
  RUN_CMD="$INSTALL_CMD && pnpm check && pnpm test && pnpm build"
fi

# Mounts:
#   - frontend dir -> /app (source)
#   - named pnpm store -> /pnpm-store (persistent, kept OUT of the repo tree)
#   - named node_modules -> /app/node_modules (persistent + isolated from the host)
docker run --rm \
  --memory="$MEMORY_LIMIT" \
  --cpus="$CPU_LIMIT" \
  -v "$HOST_DIR":/app \
  -w /app \
  -v "$NODE_MODULES_VOLUME":/app/node_modules \
  -v "$PNPM_STORE_VOLUME":/pnpm-store \
  --pids-limit=512 \
  -e ROLLDOWN_WORKER_THREADS=2 \
  -e ROLLDOWN_MAX_BLOCKING_THREADS=2 \
  -e RAYON_NUM_THREADS=2 \
  -e NODE_OPTIONS="--max-old-space-size=${BUILD_HEAP_MB}" \
  "$IMAGE" \
  bash -lc "corepack enable >/dev/null 2>&1; pnpm config set store-dir /pnpm-store >/dev/null 2>&1; $RUN_CMD"
