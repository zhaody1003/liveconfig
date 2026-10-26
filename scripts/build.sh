#!/usr/bin/env bash
# 在工程根执行完整构建；单个模块内跑 spring-boot:run 请直接用 scripts/run-*.sh
set -euo pipefail

MVN_HOME_DEFAULT="$HOME/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16"
MVN="${MVN:-$MVN_HOME_DEFAULT/bin/mvn}"
[ -x "$MVN" ] || { echo "找不到 mvn：$MVN（可用 MVN=/path/to/mvn 覆盖）"; exit 1; }

cd "$(dirname "$0")/.."
exec "$MVN" "$@"
