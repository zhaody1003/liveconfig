#!/usr/bin/env bash
# 启动接入示例应用：读 liveconfig-demo/.env 里的 SDK Key，前台运行（Ctrl-C 停）
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/env.sh"

DEMO_ENV="$ROOT_DIR/liveconfig-demo/.env"
if [ -f "$DEMO_ENV" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$DEMO_ENV"
  set +a
fi

if [ -z "${LIVECONFIG_SDK_KEY:-}" ]; then
  echo "缺少 LIVECONFIG_SDK_KEY —— 到控制台「环境管理」创建环境时复制 Server SDK Key，写进 liveconfig-demo/.env" >&2
  exit 1
fi

cd "$ROOT_DIR"
"$MVN" -q -DskipTests -pl liveconfig-demo -am install

cd "$ROOT_DIR/liveconfig-demo"
exec "$MVN" spring-boot:run
