# Liveconfig · 后端技术方案（最终版 v2）

> **项目名：Liveconfig**（live config —— 运行时可改，改完即生效，不用重新发版）。
> 定义句：*部署一次，功能是否生效随时改。*
> SDK 类名相应定为 `LiveconfigClient`，包名 `com.z.liveconfig.*`。
> 状态：**技术方案已定，等开工指令**。本文件确认后再写代码。
> 范围：A 开关核心 + B 灰度分流 + C SDK + D 控制台。E（A/B 实验）不做。

## 0. 已定结论（不再讨论）

| 项 | 结论 |
|---|---|
| 工程根目录 | `/Users/zdy/code/switch`（目录名保留，模块名用 liveconfig） |
| 前后端 | **分离**：`liveconfig-server`（Java）+ `liveconfig-web`（Vue3，独立工程，只调 REST/SSE） |
| Java | 21（本机 Microsoft OpenJDK 21.0.11） |
| 框架 | Spring Boot **3.5.15** + **MyBatis-Plus 3.5.7**（`mybatis-plus-spring-boot3-starter`，本机已缓存） |
| 分层规范 | `controller` / `service` / `service.impl` / `mapper` + `resources/mapper/*.xml` / `entity` / `dto` / `vo` |
| 数据库 | **MySQL 8.4**，docker compose 起（本机 Docker daemon 29.5.3 在跑；OmniGrowth 已用同版本） |
| 统一响应 | 复用 **`com.z:common:0.0.1-SNAPSHOT`** 的 `Result<T>`（`isSuccess/code/message/data`）与 `ResultEnum`，已装在本机仓库 |
| 其他依赖 | Lombok、Hutool 5.8.38（均缓存） |
| 分流计算 | 服务端；SDK 只在降级时用同算法本地兜底 |
| 配置推送 | SSE（MVC `SseEmitter`），不引 WebFlux |
| 环境 | 可创建资源，高危性用 `env.high_risk` 字段表达，不靠环境名叫 prod |
| Maven | 无系统 `mvn`，统一用绝对路径 `~/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16/bin/mvn` |

**H2 弃用理由**：H2 里 JSON 列只能退化成 VARCHAR、分页与 `ON DUPLICATE KEY` 等写法跟真实 MySQL 不一致，讲 SQL 时不诚实；本机 Docker 可用，MySQL 成本只是启动前一条 `docker compose up -d`。

---

## 1. 工程结构

```
/Users/zdy/code/switch
├── pom.xml                      # 父 POM：packaging=pom，管 Java 21 / Boot 3.5.15 / MP 3.5.7 版本
├── liveconfig-server/           # 控制台后端 + 分流引擎 + SSE，:8080
├── liveconfig-sdk/              # Java SDK（纯 Java，不依赖 Spring）
├── liveconfig-demo/             # 接入示例应用，:8081，演示 SDK
├── liveconfig-web/              # Vue3 + Vite + Element Plus，:5173，独立工程（不进 Maven）
├── infra/docker-compose.yml     # mysql:8.4 + 卷 + 初始化
├── sql/schema.sql               # 建表脚本（幂等，CREATE TABLE IF NOT EXISTS）
├── sql/seed.sql                 # 演示数据（可选，与原型对齐）
├── scripts/
│   ├── build.sh                 # 调 mvn 绝对路径打包
│   ├── e2e.sh                   # curl 全链路验收
│   └── run-all.sh               # compose up → server → demo → web
└── docs/TECH-DESIGN.md          # 本文件
```

### 1.1 liveconfig-server 包结构（严格按你的规范）

```
com.z.liveconfig
├── LiveconfigServerApplication.java
├── config/         MybatisPlusConfig(分页插件+字段自动填充) · WebConfig(拦截器注册+CORS) · AsyncConfig(SSE 线程池)
├── common/         BizException · ErrorCode · ValueTypes(类型校验) · PageQuery
├── entity/         App · Env · SdkKey · FeatureFlag · FlagRule · FlagInstanceOverride
│                   AppRole · AppRolePerm · AppMember · FlagChangeLog · User · UserSession
├── mapper/         XxxMapper extends BaseMapper<Xxx>（自定义方法只在跨表聚合时加，实现在 XML）
├── service/        接口：IAppService · IEnvService · IFeatureFlagService · IFlagRuleService
│                   IInstanceOverrideService · IMemberService · IRoleService · IHistoryService
│                   ISdkEvaluateService · ISdkKeyService · IAuthService
├── service/impl/   实现类，**事务边界只在这一层**
├── controller/
│   ├── admin/      AuthController · AppController · EnvController · FlagController
│   │               RuleController · InstanceController · MemberController · RoleController
│   │               HistoryController · MonitorController
│   └── sdk/        SdkEvaluateController · SdkConfigController · SseStreamController
├── dto/            入参 + jakarta.validation 注解（CreateEnvDTO · UpdateDefaultDTO · EvaluateDTO …）
├── vo/             出参（FlagListVO · EnvCardVO · MemberMatrixVO · EvaluateResultVO …）
├── engine/         分流引擎（纯 POJO，不依赖 Spring / servlet，可单测）
│   ├── EvaluationEngine · EvalRequest · EvalResult · Decision · RulePipeline
│   └── rules/      InstanceOverrideRule · WhitelistRule · BlacklistRule · AttributeRule · RolloutRule · DefaultRule
├── security/       AuthInterceptor · SdkKeyInterceptor · SessionService
│                   @RequiresPerm · @TargetEnv · PermAspect · Perm(枚举，仅作校验目标，角色组合是数据)
└── push/           SseHub · ConfigChangePublisher · HeartbeatTask
```

Mapper XML 只放**真有聚合/多表**的语句，简单 CRUD 一律 `BaseMapper` + `LambdaQueryWrapper`，避免把 XML 写成形式：
`FlagMapper.xml`（开关列表带「N 台按 IP 设值」计数 + 规则汇总）、`AppMemberMapper.xml`（成员×环境×角色矩阵）、`FlagChangeLogMapper.xml`（分页倒序 + 按 flag 过滤）、`SdkKeyMapper.xml`（按环境取 masked 列表）。

**分层硬约束**：Controller 不写业务、不拼 SQL；`engine` 不碰 Web 与 DB；SDK 接口与管理接口前缀、认证方式、拦截器全部分开。

---

## 2. 数据库（MySQL 8.4）

`infra/docker-compose.yml`：`mysql:8.4`，`3306:3306`，库名 `liveconfig`，账号口令走 `infra/.env`（不进仓库）。
字符集统一 `utf8mb4 / utf8mb4_0900_ai_ci`，引擎 `InnoDB`。

```sql
CREATE TABLE IF NOT EXISTS platform_user (                 -- 平台账号（与“某应用的成员”是两回事）；避开 MySQL 保留字 user
  id             BIGINT NOT NULL AUTO_INCREMENT,
  email          VARCHAR(200) NOT NULL,
  name           VARCHAR(100) NOT NULL,
  password_hash  VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  platform_role  VARCHAR(20)  NOT NULL COMMENT 'USER / ADMIN',
  status         VARCHAR(20)  NOT NULL COMMENT 'ACTIVE / DISABLED',
  created_at     DATETIME(3)  NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  app_key    VARCHAR(100) NOT NULL COMMENT 'SDK 用的应用标识，如 order-service',
  name       VARCHAR(100) NOT NULL,
  owner      VARCHAR(100),
  color      VARCHAR(20),
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_app_key (app_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS env (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  app_id         BIGINT NOT NULL,
  env_key        VARCHAR(100) NOT NULL COMMENT 'production / gray / eu-prod，自由创建',
  color          VARCHAR(20) NOT NULL,
  high_risk      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1 → 写值需 value:prod 权限',
  config_version BIGINT NOT NULL DEFAULT 0 COMMENT '环境级配置版本，任何变更 +1',
  created_at     DATETIME(3) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_app_env (app_id, env_key),
  CONSTRAINT fk_env_app FOREIGN KEY (app_id) REFERENCES app(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sdk_key (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  env_id     BIGINT NOT NULL,
  scope      VARCHAR(20) NOT NULL COMMENT 'SERVER / CLIENT',
  key_hash   CHAR(64) NOT NULL COMMENT 'SHA-256，明文只在创建时返回一次',
  key_masked VARCHAR(60) NOT NULL COMMENT 'srv-prod-8f3a…c21d，列表展示用',
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_key_hash (key_hash), KEY idx_env (env_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS feature_flag (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  env_id        BIGINT NOT NULL,
  flag_key      VARCHAR(150) NOT NULL,
  name          VARCHAR(150),
  value_type    VARCHAR(20)  NOT NULL COMMENT 'BOOLEAN / STRING / NUMBER / JSON',
  default_value VARCHAR(4000) NOT NULL COMMENT '统一存字符串，写入前按类型校验',
  enabled       TINYINT(1) NOT NULL,
  version       INT NOT NULL DEFAULT 1,
  updated_at    DATETIME(3) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_env_flag (env_id, flag_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS flag_rule (
  id        BIGINT NOT NULL AUTO_INCREMENT,
  flag_id   BIGINT NOT NULL,
  kind      VARCHAR(20) NOT NULL COMMENT 'WHITELIST/BLACKLIST/ATTRIBUTE/ROLLOUT',
  priority  INT NOT NULL,
  config    JSON NOT NULL,
  value     VARCHAR(4000) NOT NULL COMMENT '命中后返回的值',
  PRIMARY KEY (id), KEY idx_flag (flag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 服务器定向：内网 IP 是主键的一部分（公网 IP 可能 NAT 共享，只展示）
CREATE TABLE IF NOT EXISTS flag_instance_override (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  flag_id    BIGINT NOT NULL,
  private_ip VARCHAR(50) NOT NULL,
  public_ip  VARCHAR(50),
  value      VARCHAR(4000) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_flag_ip (flag_id, private_ip)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_role (
  id       BIGINT NOT NULL AUTO_INCREMENT,
  app_id   BIGINT NOT NULL,
  role_key VARCHAR(100) NOT NULL,
  name     VARCHAR(100) NOT NULL,
  builtin  TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_app_role (app_id, role_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS app_role_perm (
  role_id BIGINT NOT NULL,
  perm    VARCHAR(40) NOT NULL COMMENT 'flag:read / value:prod / …',
  PRIMARY KEY (role_id, perm)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 成员 = 用户 × 应用 × 环境 → 角色（矩阵的一格）
CREATE TABLE IF NOT EXISTS app_member (
  id      BIGINT NOT NULL AUTO_INCREMENT,
  app_id  BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  env_id  BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_member (app_id, user_id, env_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS flag_change_log (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  env_id         BIGINT NOT NULL,
  flag_key       VARCHAR(150) NOT NULL,
  actor          VARCHAR(200) NOT NULL COMMENT 'email',
  action         VARCHAR(30)  NOT NULL COMMENT 'CREATE/UPDATE_DEFAULT/SET_INSTANCE/CLEAR_INSTANCE/SAVE_RULES/ROLLBACK',
  before_json    JSON NULL,
  after_json     JSON NULL,
  config_version BIGINT NOT NULL,
  created_at     DATETIME(3) NOT NULL,
  PRIMARY KEY (id), KEY idx_env_flag_time (env_id, flag_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_session (
  token      CHAR(32) PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

建表方式：**手工维护 `sql/schema.sql`**（幂等），启动脚本负责执行；不用 `ddl-auto`，MyBatis-Plus 不做建表。Flyway 留到讲"迁移"时再加。

### 2.1 `flag_rule.config` JSON

| kind | 示例 |
|---|---|
| `WHITELIST` | `{"userIds":["1001","test_admin"]}` |
| `BLACKLIST` | `{"userIds":["9527"]}` |
| `ATTRIBUTE` | `{"conditions":[{"attr":"region","op":"EQ","value":"hangzhou"}]}` |
| `ROLLOUT` | `{"percent":30,"bucketBy":"USER_ID"}` |

优先级顺序固定为引擎流水线顺序，`priority` 仅在同 kind 多条时生效：
`INSTANCE_OVERRIDE → WHITELIST → BLACKLIST → ATTRIBUTE → ROLLOUT → DEFAULT`

### 2.2 MP 实体约定

- `@TableName("feature_flag")`、`@TableId(type = IdType.AUTO)`（MySQL 自增）
- `created_at / updated_at` 由 `MetaObjectHandler` 自动填充，不在 Service 里手撒
- JSON 列在实体里映射 `String`，序列化/校验在 `ValueTypes` 与 engine 层做，MP 不引 JSON TypeHandler（少一个变量）
- 分页：`PaginationInnerInterceptor(DbType.MYSQL)`，列表接口一律 `PageVO`，禁止全表 selectList 给前端

---

## 3. 接口契约

响应统一 `Result<T>`（`com.z.common.response.Result`）：成功 `{isSuccess:true, code:200, message:"success", data:{…}}`；失败 `{isSuccess:false, code:403, message:"无该环境的写值权限", data:null}`。
HTTP 状态：200 正常、400 参数/类型校验失败、401 未认证、403 无权限、404 不存在、409 唯一冲突。

### 3.1 管理侧（`Authorization: Bearer <sessionToken>`）

| 方法 | 路径 | 说明 | 权限点 |
|---|---|---|---|
| POST | `/api/auth/login` | email+password → token | — |
| POST | `/api/auth/logout` | 删会话 | 登录 |
| GET | `/api/apps` | 应用列表（含开关数、团队） | `flag:read` |
| POST | `/api/apps` | 接入应用 | `env:manage` |
| GET | `/api/apps/{appKey}/envs` | 环境列表 | `flag:read` |
| POST | `/api/apps/{appKey}/envs` | **创建环境**，同时生成 SERVER/CLIENT 两把 Key（明文仅此一次） | `env:manage` |
| DELETE | `/api/apps/{appKey}/envs/{envKey}` | 删环境（有开关则 409） | `env:manage` |
| GET | `/api/flags?appKey&envKey&q&pageNum&pageSize` | 开关列表：默认值、灰度比例、**N 台按 IP 设值** | `flag:read` |
| POST | `/api/flags` | 新建开关 | `flag:write` |
| PUT | `/api/flags/{flagId}/default` | **编辑默认值 + 全量推送**（原型铅笔按钮） | `value:*` |
| GET | `/api/flags/{flagId}/instances` | 展开行：服务器 + 当前值 | `flag:read` |
| PUT | `/api/flags/{flagId}/instances` | 按内网 IP 设值 | `instance:write` |
| DELETE | `/api/flags/{flagId}/instances/{privateIp}` | 清除（跟随默认） | `instance:write` |
| GET/PUT | `/api/flags/{flagId}/rules` | 规则列表 / 整体保存并推送 | `flag:write` |
| GET | `/api/history?appKey&envKey&flagKey&pageNum` | 变更历史倒序 | `flag:read` |
| POST | `/api/history/{logId}/rollback` | 回滚（追加新记录，历史不删） | `value:*` |
| GET | `/api/members?appKey` | 成员×环境×角色矩阵 | `member:manage` |
| POST/DELETE | `/api/members` | 邀请（平台账号 / 新建账号）/ 移除 | `member:manage` |
| GET/POST/PUT | `/api/roles?appKey` | 角色列表（含使用人数）/ 新建自定义角色 | `member:manage` |
| GET | `/api/monitor/stream` | 浏览器监控页 SSE（复用 push 通道，浏览器用 EventSource 带 token query） | `flag:read` |

### 3.2 SDK 侧（`X-SDK-Key: <明文key>`，只读）

**POST `/sdk/v1/evaluate`**
```json
{"flagKey":"new_checkout",
 "userId":"2088",
 "instance":{"privateIp":"10.0.0.11","publicIp":"47.96.12.31","appName":"liveconfig-demo"},
 "attributes":{"region":"beijing","appVersion":"3.2.0"}}
```
```json
{"isSuccess":true,"code":200,"message":"success","data":{
  "key":"new_checkout","type":"BOOLEAN","value":"false",
  "hitRule":"DEFAULT","reason":"bucket 73 >= 30","bucket":73,
  "flagVersion":3,"configVersion":42,"source":"SERVER"}}
```
`hitRule ∈ INSTANCE_OVERRIDE/WHITELIST/BLACKLIST/ATTRIBUTE/ROLLOUT/DEFAULT/FLAG_DISABLED/FLAG_NOT_FOUND`；
开关不存在 → `value=null` + `FLAG_NOT_FOUND`，返回 200 不抛错（SDK 用调用方默认值兜底）。

**POST `/sdk/v1/evaluate/batch`** — `{flagKeys:[…], userId, instance, attributes}`，一次算多个。
**GET `/sdk/v1/config`** — 该 app+env 全量快照（SDK 兜底缓存来源）。
**GET `/sdk/v1/stream`** — SSE。

SDK Key 无效/过期 → 401；SDK Key 调管理侧接口 → 403（两条拦截器互斥）。

---

## 4. 分流引擎

```java
public EvalResult evaluate(EvalRequest req, EnvConfigSnapshot snapshot) {
    FlagConfig flag = snapshot.find(req.flagKey());
    if (flag == null)     return EvalResult.notFound(req.flagKey());
    if (!flag.enabled())  return EvalResult.disabled(flag);
    for (RuleEvaluator rule : pipeline) {              // 顺序固定，加手段=加类
        Optional<Decision> d = rule.evaluate(req, flag);
        if (d.isPresent()) return EvalResult.of(flag, d.get());
    }
    return EvalResult.of(flag, Decision.defaultValue(flag));
}
```

一致性哈希放量：
```java
static int bucket(String flagKey, String userId) {
    byte[] md5 = MessageDigest.getInstance("MD5")
        .digest((flagKey + ":" + userId).getBytes(StandardCharsets.UTF_8));
    int h = ((md5[0] & 0xFF) << 24) | ((md5[1] & 0xFF) << 16)
          | ((md5[2] & 0xFF) << 8)  | (md5[3] & 0xFF);
    return Math.floorMod(h, 100);
}
// 命中条件：bucket(flagKey, userId) < percent
```
两个必讲的点：**掺 flagKey** → 不同开关分桶相互独立（否则放量永远是同一批人先命中）；**单调 `<` 比较** → 30%→50% 时已命中用户不会跳走。`bucketBy` 支持 `USER_ID` / `INSTANCE`（按内网 IP 分桶）。

属性定向：`op ∈ EQ/NE/IN/GT/LT`，`conditions` 全满足才命中（AND）；非法条件保存时就拒，不留到运行时。

类型校验唯一入口 `ValueTypes.validate(type, raw)`：BOOLEAN 只收 `true/false`；NUMBER 走 `new BigDecimal(raw)`；JSON 走 `mapper.readTree(raw)`；STRING 任意。默认值、实例设值、规则返回值三条写路径共用它——杜绝"JSON 开关被强制 true/false"。

---

## 5. SSE 推送协议

| event | id | data |
|---|---|---|
| `hello` | 当前 configVersion | `{"appKey":"","envKey":"","configVersion":42}` |
| `config-change` | 新 configVersion | `{"appKey":"","envKey":"","configVersion":43,"changedKeys":["new_checkout"]}` |
| `heartbeat` | — | `{"ts":1697…}` 每 15s |

- `SseHub` 维护 `ConcurrentHashMap<String /*appKey:envKey*/, CopyOnWriteArrayList<EmitterEntry>>`，注册/注销/广播都在 hub 内；业务只调 `ConfigChangePublisher.published(envId, changedKeys)`。
- **关键坑：DB 事务提交后再广播**（`TransactionSynchronizationManager` 的 `afterCommit`，或 Service 拆两个方法）。反了的话 SDK 收到事件回头拉的是旧配置。
- 慢客户端：`emitter.send()` 抛 `IOException` 立即摘除，不拖垮整批广播。
- 在线 SDK 数 = 该环境活跃 `SseEmitter` 数（连接 +1、断开/超时 −1），口径是**实例/连接数**不是机器数。
- SDK 收到变更后：拉 `/sdk/v1/config` 更新兜底快照。配置推送不改变服务端分流结果（每次实时算），这条通道是为了让**兜底快照**不过期。
- v1 单机部署；多实例广播需 Redis pub/sub，写进后续迭代，不在 v1 假造。

---

## 6. liveconfig-sdk

```java
public final class LiveconfigClient implements AutoCloseable {
    public static Builder builder();      // endpoint / sdkKey / appName / timeout / fallbackDir
    public boolean getBoolean(String key, String userId, boolean def);
    public String  getString(String key, String userId, String def);
    public int     getInt(String key, String userId, int def);
    public JsonNode getJson(String key, String userId, String def);
    public <T> T evaluate(String key, FlagContext ctx, T def, ValueType type);
    public FlagDecision explain(String key, FlagContext ctx);   // demo 打印命中链路
}
```
- **实例身份**：启动遍历 `NetworkInterface`，取首个 `site-local && !loopback` 作 `privateIp`；`publicIp` 走可配回显服务（超时 1s，失败留空）。公网 IP **只展示、不作主键**。
- **三层取值**：① HTTP `/evaluate`（默认 300ms 超时）→ ② 失败用本地快照按**同一哈希算法**兜底算 → ③ 快照也没有则返回调用方 `def`。日志区分 `SOURCE_SERVER / SOURCE_FALLBACK / SOURCE_DEFAULT`。
- 兜底缓存：内存 + `~/.liveconfig/{app}-{env}.json` 落盘，进程重启仍有兜底。
- 订阅线程：守护线程跑 SSE，指数退避重连 1s→2s→…→30s 封顶。

---

## 7. 权限落地

权限点存 `app_role_perm`（角色是数据，可自定义）：
`flag:read` `flag:write` `value:nonprod` `value:prod` `instance:write` `env:manage` `member:manage`

```java
@RequiresPerm(Perm.VALUE_WRITE)          // 只声明“要写值”
@TargetEnv("#req.envKey")                // 环境由参数定位
```
`PermAspect`：取 (当前用户, app, env) → `app_member` → 角色 → 权限集合。
`VALUE_WRITE` 展开：目标 `env.high_risk=1` 时要求实际持有 `value:prod`，否则 `value:nonprod` 即可 —— prod 的特殊性是**环境的属性**，不是硬编码环境名。
平台 `ADMIN` 短路放行；SDK Key 只能走只读接口。

---

## 8. 变更历史与回滚

写操作在**同事务**内写 `flag_change_log`，`before_json/after_json` 存该 flag 完整配置片段（默认值 + 规则 + 实例设值）。
**回滚 = 取目标记录的 `after_json` 再执行一次写操作**（`action=ROLLBACK`，`config_version` 前进），历史只增不删；回滚权限与改值同权。

---

## 9. 前端 liveconfig-web（Vue3 + Vite + Element Plus）

```
liveconfig-web/
├── src/api/          request.ts(axios 实例，统一解 Result) · auth.ts · flag.ts · env.ts …
├── src/views/        Apps(落地页) · Flags · Rules · Debug · History · Monitor · Envs · Members · Roles · Login
├── src/components/   FlagExpandRow · ValueEditor(按 value_type 切控件) · PermChips …
├── src/composables/  useSse(EventSource) · useApp · useEnv
└── vite.config.ts    server.proxy['/api'] → http://localhost:8080（开发期免 CORS）
```
- token 存 `localStorage`，axios 拦截器注入 `Authorization`；401 → 跳登录。
- 已定稿的 `console-prototype.html` 视觉与交互作为 1:1 还原基准：落地页无侧栏 + 通栏头部 + 居中搜索、右上应用/环境/个人三下拉、铅笔=默认值弹窗、箭头=行内展开实例、成员×环境矩阵。
- 后端仍配 CORS（`WebConfig`）以便非代理场景。

---

## 10. 配置与凭证

```
liveconfig-server/src/main/resources/application.yml     # 端口、数据源、SSE 心跳、会话时长
liveconfig-server/src/main/resources/application-local.yml   # git-ignore：本机 MySQL 口令
infra/.env                                               # git-ignore：compose 用的 MYSQL_ROOT_PASSWORD
liveconfig-demo/.env                                     # git-ignore：SDK Key 明文
```
SDK Key 明文只在创建响应里出现一次，UI 只显示 masked；口令与 Key 一律不进仓库、不进日志。

---

## 11. 测试与验收

| 层 | 内容 |
|---|---|
| 单元 | `bucket()` 黄金向量（5 组 key/userId → 期望桶号，锁死算法）；流水线优先级；`ValueTypes` 四类校验；回滚产生新版本 |
| 集成 | `@SpringBootTest` + MockMvc + Testcontainers 或本机 MySQL：登录→建应用/环境→建开关→设实例值→evaluate 断 `hitRule`；viewer 写值 403；SSE 异步断言收到 `config-change` |
| E2E | `scripts/e2e.sh`：curl 全链路，输出与 §3.2 示例逐字一致 |
| 手工 | `run-all.sh` 起 compose + server + demo + web；改 prod 默认值 → 观察 SDK 日志 300ms 内 configVersion 前进且 `SOURCE_SERVER`；杀掉 server → demo 变 `SOURCE_FALLBACK` |

**每个里程碑的完成定义 = 本机真跑起来并看到输出**，不接受"写完未运行"。

---

## 12. 里程碑

| # | 内容 | 验收 |
|---|---|---|
| M0 | 父 POM + 4 模块骨架 + compose 起 MySQL + mvn 绝对路径脚本 | `build.sh` 通过，server 起 :8080，`/actuator/health` 200（或健康检查页） |
| M1 | `schema.sql` + 12 个实体 + Mapper + MP 配置（分页、自动填充） | DBeaver/命令行能查到种子数据：3 应用 3 环境 7 开关 |
| M2 | 登录会话 + apps/envs/flags CRUD（含类型校验、SDK Key 明文一次性返回） | curl 建环境拿到两把 Key；JSON 开关填 `true` 被 400 拒 |
| M3 | 分流引擎 + 单元测试 | `mvn test` 绿，黄金向量不变 |
| M4 | `/sdk/v1/evaluate(+batch)` + SdkKey 拦截器 + `@RequiresPerm` 切面 | 带 Key evaluate 返回 `hitRule`；viewer 调写接口 403 |
| M5 | SSE 推送 + 变更历史 + 回滚 | 改默认值后 curl 监听 `/sdk/v1/stream` 打印 `config-change`；回滚使 configVersion +1 |
| M6 | liveconfig-sdk + liveconfig-demo 端到端 | demo 的 `/checkout?userId=2088` 返回 true/false；停 server 走 `SOURCE_FALLBACK` |
| M7 | liveconfig-web 逐页替换 mock 为真接口 | 登录、应用列表、开关表格、行内展开实例、成员矩阵全部真数据 |

---

## 13. 开工前要补的最后两个小确认

1. ~~MySQL 口令/端口~~ → 已核：本机 **3306 空闲**，无 mysql 容器在跑。默认用 `liveconfig / liveconfig` + 3306，compose 放 `switch/infra/`，风格照抄 OmniGrowth 那份。
2. **端口占用（2026-10-18 实测，待用户决定）**：
   - `:8080` = PID 62110，**mingyou-service**，cwd 已在 `/Users/zdy/.Trash/mingyou-service`（工程已进废纸篓、进程仍存活），classpath `/Users/zdy/code/mingyou/mingyou-service/target/classes`，Boot 4.0.8 + MyBatis + mysql-connector-j 9.7.0 → 判定为可停的遗留进程。
   - `:8081` = PID 68505，**ai-fullstack-course/java-service**，cwd `/Users/zdy/Documents/Qoder/2026-10-09/75de475a/ai-fullstack-course/java-service`，Boot 3.3.5 + H2 + WebFlux/SSE → 课程示例服务，可能还要演示。
   - 结论待定：用户停掉这两个进程沿用 8080/8081，或本项目改用 **18080（server）/ 18081（demo）**、web 仍 5173。

## 14. 代码位置约定

工程根 = `/Users/zdy/code/switch`（用户指定，全部代码放这里；该目录在本会话工作区之外，写文件需逐次授权）。
本方案文档定稿后复制一份到 `switch/docs/TECH-DESIGN.md` 与工程同仓保存。

---

## 15. M0 落地记录（已完成，2026-10-25）

**验收结果**：`GET http://localhost:8080/api/ping` →
`{"isSuccess":true,"code":200,"message":"SUCCESS","data":{"app":"liveconfig-server","db":"8.4.13"},"success":true}`
即 Spring MVC + MyBatis-Plus + MySQL 8.4.13 + `com.z:common` 的 `Result` 四件同时走通。

搭骨架过程中撞到的四个真实问题，后续 M1 起要遵守：

1. **Docker Hub 拉不动**：`registry-1.docker.io` 超时（与 Gradle 官方源被限速同源）。做法：`docker pull docker.m.daocloud.io/library/mysql:8.4` 后 `docker tag` 回 `mysql:8.4`，compose 文件保持标准镜像名不动，也不改 Docker 全局配置。`node:22-alpine` 等后续镜像同法处理。
2. **MyBatis-Plus 3.5.7 + Spring Boot 3.5 需抬 mybatis-spring**：MP 传递的是 3.0.3，在 Spring 6.2 下 `factoryBeanObjectType` 报错；父 POM 的 `dependencyManagement` 里显式覆盖为 **3.0.6**（已加注释说明原因）。
3. **`spring-boot:run` 不能在根 reactor 上用 `-pl … -am` 跑**：会落到父工程（无 main class）直接失败。`scripts/run-server.sh` 改成两步：根目录 `-pl liveconfig-server -am install`，再 `cd liveconfig-server` 执行 `mvn spring-boot:run`。
4. **表名 `user` 撞 MySQL 保留字** → 改为 **`platform_user`**（§2 的 DDL 已同步）。

**Result 序列化注意**：`com.z.common.response.Result` 同时有 Lombok 的 `getIsSuccess()` 和手写的 `isSuccess()`，Jackson 会输出 `isSuccess` 与 `success` 两个字段。**前端统一只认 `isSuccess`**，`success` 当冗余忽略（不改你的公共库）。

**脚本入口**（都在 `switch/scripts/`）：`db-up.sh`（起容器并等 healthy）→ `apply-schema.sh`（幂等建 12 表并列表）→ `run-server.sh` → `run-all.sh`（前三步串起来）；`build.sh` 是构建入口，内部固定 `cd` 到工程根，真实 mvn 路径可用环境变量 `MVN` 覆盖。
口令在 `infra/.env`（已 git-ignore，`.env.example` 是模板），由 `scripts/env.sh` 导出成 `DB_URL/DB_USERNAME/DB_PASSWORD` 供 Spring 读取。

**端口**：8080/8081 原占用进程（废纸篓里的 mingyou-service、旧课 java-service）经用户确认后已停，现由 server/demo 使用；MySQL 3306。


---

## 16. M1–M7 实现记录（2026-10-25 完成）

**回归结果**：`mvn test` 20/20（引擎 14 + SDK 兜底 6）；`scripts/e2e.sh` 15/15；demo 端到端 `SOURCE_SERVER` 与 `SOURCE_FALLBACK` 均实测。

实现过程中改动的原设计，以及踩到的真问题：

1. **新增两张表**：`sdk_instance`（SDK 上报的实例，按 IP 设值的候选来源）、`user_session` 保留。表数从 12 增至 13。
2. **`EnvVO` 增加 `id`、`FlagRowVO` 增加 `envId`**：成员矩阵前端要按 envId 对齐角色格，原来只回 envKey 不够。
3. **管理侧新增 `POST /api/flags/debug`**：控制台的「分流调试」原本打算直接调 `/sdk/v1/evaluate`，但那条通道按 SDK Key 认证，浏览器没有 Key → 返回 401 → 前端拦截器误判「登录过期」把会话清了。改为管理身份 + `flag:read` 权限走同一引擎，且**不登记实例**（避免调试时乱填的 IP 污染候选列表）。
4. **SSE 客户端断开的异常处理**：浏览器关页面时容器抛 `AsyncRequestNotUsableException`，被 `Exception.class` 兜底 handler 接住后又往已断开的响应写数据，二次异常导致后续请求 500、整个控制台被踢回登录页。必须单独 `@ExceptionHandler` 返回 null（不再处理）。
5. **`@RestControllerAdvice` 顺序**：带 `Exception.class` 兜底的 advice 若优先级更高，会把 `BizException` 吞成 500。自定义 advice 用 `@Order(1)`，兜底 advice 用 `@Order(10)`。
6. **`Result` 双字段**：`com.z:common` 的 `Result` 同时有 Lombok 的 `getIsSuccess()` 与手写的 `isSuccess()`，Jackson 输出 `isSuccess` 与 `success` 两个键。前端只认 `isSuccess`；未登录/无权限的 code 在公共库里是 500，已在 advice 里按语义改写成 401/403 并回填 `code`。
7. **`Result.success()` 返回 `Result<Boolean>`**：无返回值接口签名统一用 `Result<Boolean>`，否则与 `Result<Void>` 不兼容编译失败。
8. **JSON 类型校验收紧**：`readTree("true")` 是合法 JSON 标量，会让 BOOLEAN 值混进 JSON 开关。改为只接受对象或数组。
9. **MP 自动填充的坑**：`strictInsertFill` 只填 `@TableField(fill=INSERT)` 的字段，`last_seen_at` 标的是 `INSERT_UPDATE` 因此没被填 → 插入报 `Column cannot be null`。已在 handler 里补 `lastSeenAt`。
10. **引擎保持零 Spring**：`@Component` 全部摘掉，改由 `EngineConfig` 手工装配流水线，顺序即优先级；因此 14 个引擎单测不需要起容器。
11. **SDK 兜底与服务端同算法**：`SdkBucket` 与 `HashBucket` 逐字节一致，SDK 单测里用同一批黄金值锁死，防止断网期间灰度方向跳变。
12. **演示数据**：`reset-demo.sh`（`-Dliveconfig.demo.reset=true`）清空业务表后重新播种；删除顺序按外键依赖，`user_session` 必须先于 `platform_user`。

**前端**：Vue3 + Vite + Element Plus + Pinia + vue-router（hash 模式）。样式直接移植已定稿原型的 CSS 变量与组件类，保证视觉一致；`vue-tsc` 需锁 `typescript@5.8`（TS 6 移除了 `lib/tsc` 子路径导出）。
