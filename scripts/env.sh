#!/usr/bin/env bash
# 共用：加载 infra/.env 并导出为环境变量
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/infra/.env}"

if [ ! -f "$ENV_FILE" ]; then
  echo "缺少 $ENV_FILE —— 请先执行: cp infra/.env.example infra/.env 并填口令" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

export DB_URL="${DB_URL:-jdbc:mysql://localhost:${DB_PORT:-3306}/liveconfig?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai}"
export DB_USERNAME DB_PASSWORD

# 本机没有系统 mvn，统一用 Maven Wrapper 已下载的发行版
MVN_HOME_DEFAULT="$HOME/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16"
export MVN="${MVN:-$MVN_HOME_DEFAULT/bin/mvn}"
[ -x "$MVN" ] || { echo "找不到 mvn：$MVN（可用 MVN=/path/to/mvn 覆盖）" >&2; exit 1; }
