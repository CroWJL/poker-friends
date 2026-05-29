#!/usr/bin/env bash
set -euo pipefail

BRANCH="${1:-master}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "[redeploy] repo: ${REPO_ROOT}"
echo "[redeploy] branch: ${BRANCH}"

cd "${REPO_ROOT}"

echo "[redeploy] cleaning accidental tsc outputs in src..."
git checkout -- apps/web/src/main.js 2>/dev/null || true
rm -f apps/web/src/main.js apps/web/src/resolveApiBaseUrl.js

echo "[redeploy] fetching latest code..."
git fetch origin
git checkout "${BRANCH}"
git pull --ff-only origin "${BRANCH}"

echo "[redeploy] installing dependencies..."
pnpm install

echo "[redeploy] building web assets..."
pnpm build:web

echo "[redeploy] restarting containers..."
DOCKER_BUILDKIT=1 COMPOSE_DOCKER_CLI_BUILD=1 docker compose -f deploy/docker-compose.yml up -d --build

echo "[redeploy] service status:"
docker compose -f deploy/docker-compose.yml ps

echo "[redeploy] done."
