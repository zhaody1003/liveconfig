HELP / 使用说明
==============

infra/.env 是真实口令文件，已被 .gitignore 忽略，不要提交。
复制 .env.example 为 .env 后改成自己的口令：

  cp infra/.env.example infra/.env

需要设置的键：
  MYSQL_ROOT_PASSWORD  MySQL root 口令（仅本机容器用）
  DB_USERNAME          应用连接账号
  DB_PASSWORD          应用连接口令
  DB_PORT              宿主机映射端口，默认 3306；被占用时改这里，同时把 server 的 DB_URL 端口对齐

起库 + 建表：
  docker compose -f infra/docker-compose.yml --env-file infra/.env up -d
  scripts/apply-schema.sh
