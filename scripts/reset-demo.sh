#!/usr/bin/env bash
# 重置演示数据并启动：清空业务表 → 重新播种 → 前台运行 server
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/env.sh"

cd "$ROOT_DIR"
"$MVN" -q -DskipTests -pl liveconfig-server -am install

cd "$ROOT_DIR/liveconfig-server"
exec "$MVN" spring-boot:run -Dspring-boot.run.arguments=--liveconfig.demo.reset=true
