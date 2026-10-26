# Liveconfig

[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)](https://spring.io/projects/spring-boot)
[![Tests](https://img.shields.io/badge/tests-20%20passing-brightgreen)]()

**功能开关系统 — 部署一次，功能是否生效随时改。**

代码里两条路都在，走哪条由运行时配置决定。改配置不用重新发版、不用重启进程。

---

## 为什么需要功能开关

- **灰度发布**：新功能先放 10% 用户，观察数据再全量
- **紧急回滚**：线上出问题，秒级关闭功能，不用等发版
- **环境隔离**：同一套代码，dev/staging/prod 配置不同
- **A/B 实验**：不同用户看到不同策略，数据驱动决策

## 特性

| 能力 | 说明 |
|---|---|
| **实时推送** | SSE 长连接，改配置后毫秒级推送到所有实例 |
| **多维分流** | 服务器定向 > 白名单 > 黑名单 > 属性定向 > 百分比灰度 > 默认值 |
| **断网兜底** | SDK 本地缓存快照 + 同一哈希算法，断网期间灰度方向不跳变 |
| **类型安全** | BOOLEAN / STRING / NUMBER / JSON 四种类型，写入时强校验 |
| **权限矩阵** | 用户 × 应用 × 环境 → 角色 → 权限点，高危环境独立管控 |
| **变更审计** | 所有修改留痕，支持一键回滚到任意历史版本 |
| **零 Spring 引擎** | 分流引擎纯 POJO，14 个单元测试不起容器 |

## 快速开始

### 1. 启动基础设施

```bash
cp infra/.env.example infra/.env   # 填写 MySQL 口令
scripts/db-up.sh                    # 启动 MySQL 8.4 容器
scripts/apply-schema.sh             # 建 13 张表 + 种子数据
```

### 2. 启动后端

```bash
scripts/run-server.sh               # 启动 liveconfig-server (:8080)
```

### 3. 启动前端

```bash
cd liveconfig-web
npm install
npm run dev                         # http://127.0.0.1:5173
```

### 4. 登录控制台

- 地址：http://127.0.0.1:5173
- 账号：`zhangsan@example.com` / `demo1234`

## SDK 接入

### Maven

```xml
<dependency>
    <groupId>com.z</groupId>
    <artifactId>liveconfig-sdk</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 初始化

```java
LiveconfigClient client = LiveconfigClient.builder()
        .endpoint("http://localhost:8080")
        .sdkKey("srv-prod-xxxxxxxx")   // 控制台创建环境时生成
        .appName("order-service")
        .fallbackFile(Path.of(System.getProperty("user.home"),
                ".liveconfig/order-prod.json"))
        .build();
```

### 使用

```java
// 布尔开关
if (client.getBoolean("new_checkout", userId, false)) {
    return "新结算页";
}
return "老结算页";

// 查看判定过程（调试用）
FlagDecision decision = client.explain("new_checkout",
        FlagContext.ofUser(userId, Map.of("region", "hangzhou")));
// decision.hitRule()  → "WHITELIST" / "ROLLOUT" / "DEFAULT" ...
// decision.source()   → "SERVER" / "FALLBACK" / "CALLER_DEFAULT"
```

### 取值路径

```
─────────────┐     ┌──────────────┐     ┌───────────────┐
│  ① SERVER   │ ──→ │  FALLBACK   │ ──→ │ ③ CALLER_DEF  │
│  HTTP 调用   │ 失败 │ 本地快照兜底  │ 没有  │ 调用方默认值   │
│  300ms 超时  │     │ 同算法重算    │     │               │
└─────────────┘     └──────────────     └───────────────┘
```

## 架构

```
┌─────────────────────────────────────────────────────────┐
│                   liveconfig-web (:5173)                 │
│              Vue3 + Element Plus 控制台                   │
└──────────────────────┬──────────────────────────────────┘
                       │ REST / SSE
┌──────────────────────▼──────────────────────────────────┐
│                  liveconfig-server (:8080)                │
│  ┌──────────┐  ┌──────────┐  ┌────────  ┌──────────┐  │
│  │ Controller│  │ Service  │  │ Engine │  │ SseHub   │  │
│  │  /api/**  │  │  + MP    │  │ 纯 POJO │  │ 推送广播  │  │
│  └──────────┘  └──────────┘  └────────┘  └──────────┘  │
│  ┌──────────────────────────────────────────────────┐   │
│  │              MySQL 8.4 (13 张表)                   │   │
│  └──────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────┘
                       │ X-SDK-Key
┌──────────────────────▼──────────────────────────────────┐
│                   liveconfig-sdk                         │
│  ┌─────────┐  ──────────┐  ┌──────────────────────┐   │
│  │ HTTP    │  │ Snapshot │  │ SseSubscriber        │   │
│  │ Client  │  │  + Disk  │  │ 指数退避重连 1s→30s   │   │
│  └─────────┘  └──────────┘  ──────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

## 模块

| 模块 | 说明 |
|---|---|
| `liveconfig-server` | 控制台后端 + 分流引擎 + SSE 推送 + RBAC 权限 |
| `liveconfig-sdk` | Java 客户端库，纯 Java 不依赖 Spring |
| `liveconfig-demo` | 接入示例，演示「改配置即生效」 |
| `liveconfig-web` | Vue3 控制台前端 |
| `infra/` + `sql/` | MySQL 8.4 容器 + 建表脚本 |

## 技术栈

- **后端**: Java 21 · Spring Boot 3.5 · MyBatis-Plus 3.5 · MySQL 8.4
- **前端**: Vue 3.5 · Vite 8 · Element Plus 2.14 · Pinia 4
- **推送**: SSE (Server-Sent Events)
- **测试**: JUnit 5 · AssertJ · 20 单元测试 + 15 E2E 断言

## 演示

完整演示流程：

```bash
scripts/run-all.sh    # 一键启动 MySQL + Server + Demo
```

1. 浏览器打开 http://127.0.0.1:5173（`zhangsan@example.com` / `demo1234`）
2. 在「开关管理」修改 `new_checkout` 的默认值
3. 在「监控」页面实时看到 `config-change` 推送
4. 另开 http://localhost:8081/checkout?userId=2088，刷新即变
5. **demo 进程全程不重启**

## 开发

```bash
# 构建 + 测试
./scripts/build.sh test

# E2E 验收
./scripts/e2e.sh

# 重置演示数据
./scripts/reset-demo.sh
```

## License

Apache-2.0
