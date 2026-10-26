#!/usr/bin/env bash
# 一键：起库 → 建表 → 装依赖 → 前台启动 server（Ctrl-C 停）
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"$ROOT_DIR/scripts/db-up.sh"
"$ROOT_DIR/scripts/apply-schema.sh"
exec "$ROOT_DIR/scripts/run-server.sh"
