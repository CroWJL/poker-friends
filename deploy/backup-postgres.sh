#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"

docker exec poker-postgres pg_dump -U poker poker_friends > "${BACKUP_DIR}/poker_friends_${TIMESTAMP}.sql"
echo "Backup created: ${BACKUP_DIR}/poker_friends_${TIMESTAMP}.sql"
