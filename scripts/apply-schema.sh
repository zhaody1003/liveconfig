#!/usr/bin/env bash
# 对 liveconfig 库执行 sql/*.sql，幂等（脚本内全部 CREATE TABLE IF NOT EXISTS）
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/env.sh"

for f in "$ROOT_DIR"/sql/*.sql; do
  echo "==> $(basename "$f")"
  docker exec -i liveconfig-mysql sh -c \
    "exec mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" --default-character-set=utf8mb4 liveconfig" \
    < "$f"
done

echo "==> 表清单"
docker exec liveconfig-mysql sh -c \
  "exec mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -N -e \"SHOW TABLES\" liveconfig"
