#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/env.sh"

# 先在根把依赖装齐，再进模块目录跑 run —— spring-boot:run 落到父工程会因没有 main class 失败
# 必须带 clean：target/classes 里的旧 resources/mapper/*.xml 不会随源文件删除而消失，
# 启动时 MyBatis 仍会加载它们，报 ClassNotFoundException
cd "$ROOT_DIR"
"$MVN" -q -DskipTests -pl liveconfig-server -am clean install

cd "$ROOT_DIR/liveconfig-server"
exec "$MVN" spring-boot:run
