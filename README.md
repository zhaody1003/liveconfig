# Liveconfig

功能开关系统。**部署一次，功能是否生效随时改** —— 代码里两条路都在，走哪条由运行时配置决定，改配置不用重新发版、不用重启。

技术方案（数据模型、接口契约、分流算法、SSE 协议、里程碑）见 [`docs/TECH-DESIGN.md`](docs/TECH-DESIGN.md)。

## 模块

| 目录 | 端口 | 作用 |
|---|---|---|
| `liveconfig-server` | 8080 | 开关系统本身：控制台接口 + 服务端分流 + SSE 推送 + RBAC |
| `liveconfig-sdk` | — | 业务应用接入用的 Java 库，不依赖 Spring |
| `liveconfig-demo` | 8081 | 接入示例：一个极薄业务应用，用来演示「改配置即生效」 |
| `liveconfig-web` | 5173 | 控制台前端（Vue3 + Vite + Element Plus，独立工程，已跑通 → 代理 → 后端） |
| `infra` / `sql` | 3306 | MySQL 8.4 容器与建表脚本 |

## 前端开发

需要 Node（本机装在 `~/.local/opt/node`，已在 `~/.zshrc` 加 PATH）。

```bash
cd liveconfig-web
npm install            # 依赖源固定为 npmmirror，见 liveconfig-web/.npmrc
npm run dev            # http://127.0.0.1:5173，/api 与 /sdk 代理到 :8080
```

## 运行环境备注（本机踩过的坑）

- 没有系统 `mvn`，脚本统一用 Maven Wrapper 已下载的发行版；可用 `MVN=/path/to/mvn` 覆盖。
- Docker Hub 直连超时，镜像走 `docker.m.daocloud.io` 拉取后 `docker tag` 回标准名（见下）。
- MyBatis-Plus 3.5.7 在 Spring Boot 3.5 下必须把 `mybatis-spring` 抬到 3.0.6，父 POM 已覆盖。

## 首次运行

```bash
cp infra/.env.example infra/.env      # 填入本机口令（infra/.env 已被 git-ignore）
scripts/run-all.sh                    # 起 MySQL → 建表 → 构建 → 前台启动 server
curl http://localhost:8080/api/ping   # → data.db 返回 MySQL 版本即通
```

镜像从 Docker Hub 拉不动时：

```bash
docker pull docker.m.daocloud.io/library/mysql:8.4
docker tag docker.m.daocloud.io/library/mysql:8.4 mysql:8.4
```

构建走本机 Maven Wrapper 发行版（没有系统 `mvn`），可用 `MVN=/path/to/mvn` 覆盖。

## 当前进度

## 进度：M0–M7 全部完成（2026-10-25）

| 里程碑 | 内容 | 验收 |
|---|---|---|
| M0 | 父 POM + 四工程骨架 + MySQL 8.4 容器 + 建表 | `/api/ping` 经 MyBatis-Plus 返回 `8.4.13` |
| M1 | 13 个 MP 实体、Mapper、3 个聚合 XML、字段自动填充、幂等种子 | 3 应用 / 10 环境 / 22 开关 / 8 规则 / 27 成员格 |
| M2 | 会话登录 + 应用/环境/开关/规则/实例 CRUD | 未登录 401、口令错 401、JSON 填标量 400 |
| M3 | 服务端分流引擎（纯 POJO） | 14 个单测，含黄金桶号与「放量单调不跳变」 |
| M4 | `/sdk/v1/*` + SDK Key 认证 + 应用×环境 RBAC | 错 Key 401、SDK 调管理口 401、只读角色改 prod 403 |
| M5 | SSE 推送 + 变更历史 + 回滚 | 改值后 `config-change` 到达订阅者；回滚追加记录 |
| M6 | liveconfig-sdk + demo | 白名单→新页、默认→老页；停 server 走 `SOURCE_FALLBACK` |
| M7 | Vue3 控制台接真接口 | 10 个页面真数据，UI 改值 → 表格/历史/SSE 同步更新 |

**回归入口**：`./scripts/build.sh test`（20 个单测）+ `./scripts/e2e.sh`（15 项端到端断言）。

**演示动线**：`scripts/run-all.sh` 起后端 → 浏览器开 `http://127.0.0.1:5173`（zhangsan@example.com / demo1234）→ 开关管理改 `new_checkout` 默认值 → 实时推送监控页看到 `config-change` → 另开 `http://localhost:8081/checkout?userId=2088` 刷新即变，**demo 进程全程不重启**。
