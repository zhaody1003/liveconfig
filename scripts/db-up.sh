#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/env.sh"

docker compose -f "$ROOT_DIR/infra/docker-compose.yml" --env-file "$ROOT_DIR/infra/.env" up -d

echo -n "等待 MySQL 健康 …"
for _ in $(seq 1 40); do
  status=$(docker inspect -f '{{.State.Health.Status}}' liveconfig-mysql 2>/dev/null || echo starting)
  [ "$status" = "healthy" ] && { echo " OK"; exit 0; }
  printf "."; sleep 2
done
echo " 超时，请执行 docker logs liveconfig-mysql 查看"; exit 1
