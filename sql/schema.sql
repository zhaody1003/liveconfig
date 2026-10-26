-- Liveconfig 建表脚本（MySQL 8.4，幂等）
-- 用法：scripts/apply-schema.sh
-- 命名：平台账号表用 platform_user，避开 MySQL 保留字 user
--
-- 公共列（对应 entity/BaseEntity.java，每张表都有）：
--   id / created_at / updated_at / operator
-- 时间列由 MetaFieldHandler 填，DB 侧的 DEFAULT / ON UPDATE 只兜住裸 SQL 路径（如 env 版本自增）。
-- operator 是"这行最后一次是谁写的"，不是变更历史；要看历史查 flag_change_log。

CREATE TABLE IF NOT EXISTS platform_user (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  email          VARCHAR(200) NOT NULL,
  name           VARCHAR(100) NOT NULL,
  password_hash  VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  platform_role  VARCHAR(20)  NOT NULL COMMENT 'USER / ADMIN',
  status         VARCHAR(20)  NOT NULL COMMENT 'ACTIVE / DISABLED',
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  operator       VARCHAR(200) NOT NULL DEFAULT 'system' COMMENT '最后写入者 email',
  PRIMARY KEY (id),
  UNIQUE KEY uk_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '平台账号（全局）';

CREATE TABLE IF NOT EXISTS app (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  app_key    VARCHAR(100) NOT NULL COMMENT 'SDK 用的应用标识，如 order-service',
  name       VARCHAR(100) NOT NULL,
  owner      VARCHAR(100)          COMMENT '负责团队',
  color      VARCHAR(20),
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  operator   VARCHAR(200) NOT NULL DEFAULT 'system' COMMENT '最后写入者 email',
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_key (app_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '应用（微服务）';

CREATE TABLE IF NOT EXISTS env (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  app_id         BIGINT       NOT NULL,
  env_key        VARCHAR(100) NOT NULL COMMENT 'production / gray / eu-prod，可自由创建',
  color          VARCHAR(20)  NOT NULL,
  high_risk      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1 → 写值需 value:prod 权限',
  config_version BIGINT       NOT NULL DEFAULT 0 COMMENT '环境级配置版本，任何变更 +1',
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  operator       VARCHAR(200) NOT NULL DEFAULT 'system' COMMENT '最后写入者 email',
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_env (app_id, env_key),
  CONSTRAINT fk_env_app FOREIGN KEY (app_id) REFERENCES app (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '环境（可创建资源）';

CREATE TABLE IF NOT EXISTS sdk_key (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  env_id     BIGINT      NOT NULL,
  scope      VARCHAR(20) NOT NULL COMMENT 'SERVER / CLIENT',
  key_hash   CHAR(64)    NOT NULL COMMENT 'SHA-256，明文只在创建时返回一次',
  key_masked VARCHAR(60) NOT NULL COMMENT 'srv-prod-8f3a…c21d，列表展示',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  operator   VARCHAR(200) NOT NULL DEFAULT 'system' COMMENT '发 key 的人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_key_hash (key_hash),
  KEY idx_env (env_id),
  CONSTRAINT fk_sdkkey_env FOREIGN KEY (env_id) REFERENCES env (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'SDK Key，按应用×环境×类型发放，只读';

CREATE TABLE IF NOT EXISTS feature_flag (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  env_id        BIGINT       NOT NULL,
  flag_key      VARCHAR(150) NOT NULL,
  name          VARCHAR(150),
  value_type    VARCHAR(20)  NOT NULL COMMENT 'BOOLEAN / STRING / NUMBER / JSON',
  default_value VARCHAR(4000) NOT NULL COMMENT '统一存字符串，写入前按类型校验',
  enabled       TINYINT(1)   NOT NULL,
  version       INT          NOT NULL DEFAULT 1,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  operator      VARCHAR(200) NOT NULL DEFAULT 'system' COMMENT '最后改默认值/开关状态的人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_env_flag (env_id, flag_key),
  CONSTRAINT fk_flag_env FOREIGN KEY (env_id) REFERENCES env (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '功能开关';

CREATE TABLE IF NOT EXISTS flag_rule (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  flag_id    BIGINT      NOT NULL,
  kind       VARCHAR(20) NOT NULL COMMENT 'WHITELIST / BLACKLIST / ATTRIBUTE / ROLLOUT',
  priority   INT         NOT NULL COMMENT '同 kind 内生效顺序；跨 kind 由引擎流水线固定',
  config     JSON        NOT NULL,
  value      VARCHAR(4000) NOT NULL COMMENT '命中后返回的值',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  operator   VARCHAR(200) NOT NULL DEFAULT 'system' COMMENT '保存这套规则的人',
  PRIMARY KEY (id),
  KEY idx_flag (flag_id),
  CONSTRAINT fk_rule_flag FOREIGN KEY (flag_id) REFERENCES feature_flag (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '灰度规则';

CREATE TABLE IF NOT EXISTS flag_instance_override (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  flag_id    BIGINT       NOT NULL,
  private_ip VARCHAR(50)  NOT NULL COMMENT '内网 IP，VPC 内唯一，作设值主键',
  public_ip  VARCHAR(50)  COMMENT '公网 IP，NAT 后可能共享，仅展示',
  value      VARCHAR(4000) NOT NULL,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  operator   VARCHAR(200) NOT NULL DEFAULT 'system' COMMENT '最后一次设值/清值的人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_flag_ip (flag_id, private_ip),
  CONSTRAINT fk_override_flag FOREIGN KEY (flag_id) REFERENCES feature_flag (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '服务器定向：按 IP 单独设值，优先级最高';

CREATE TABLE IF NOT EXISTS app_role (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  app_id     BIGINT       NOT NULL,
  role_key   VARCHAR(100) NOT NULL,
  name       VARCHAR(100) NOT NULL,
  builtin    TINYINT(1)   NOT NULL DEFAULT 0,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  operator   VARCHAR(200) NOT NULL DEFAULT 'system' COMMENT '创建/改名的人；内置角色为 system',
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_role (app_id, role_key),
  CONSTRAINT fk_role_app FOREIGN KEY (app_id) REFERENCES app (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '应用角色：内置 + 自定义';

-- 原来主键是 (role_id, perm)，没有 id。为对齐 BaseEntity 换成代理主键 + 唯一键，
-- 权限点的读写仍按 role_id 条件走，不依赖 selectById。
CREATE TABLE IF NOT EXISTS app_role_perm (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  role_id    BIGINT      NOT NULL,
  perm       VARCHAR(40) NOT NULL COMMENT 'flag:read / flag:write / value:nonprod / value:prod / instance:write / env:manage / member:manage',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  operator   VARCHAR(200) NOT NULL DEFAULT 'system' COMMENT '配这个权限点的人；内置角色为 system',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_perm (role_id, perm),
  CONSTRAINT fk_perm_role FOREIGN KEY (role_id) REFERENCES app_role (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '角色的权限点集合';

CREATE TABLE IF NOT EXISTS app_member (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  app_id     BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  env_id     BIGINT      NOT NULL,
  role_id    BIGINT      NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  operator   VARCHAR(200) NOT NULL DEFAULT 'system' COMMENT '最后一次分配角色的人',
  PRIMARY KEY (id),
  UNIQUE KEY uk_member (app_id, user_id, env_id),
  KEY idx_member_app (app_id),
  CONSTRAINT fk_member_app  FOREIGN KEY (app_id)  REFERENCES app (id),
  CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES platform_user (id),
  CONSTRAINT fk_member_env  FOREIGN KEY (env_id)  REFERENCES env (id),
  CONSTRAINT fk_member_role FOREIGN KEY (role_id) REFERENCES app_role (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '成员 = 用户 × 应用 × 环境 → 角色';

CREATE TABLE IF NOT EXISTS sdk_instance (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  env_id       BIGINT      NOT NULL,
  private_ip   VARCHAR(50) NOT NULL COMMENT '内网 IP，VPC 内唯一',
  public_ip    VARCHAR(50) COMMENT '公网 IP，NAT 后可能共享，仅展示',
  app_name     VARCHAR(100) COMMENT '进程自报的应用名/主机名',
  last_seen_at DATETIME(3) NOT NULL COMMENT '心跳时间，语义不同于 updated_at',
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  operator     VARCHAR(200) NOT NULL DEFAULT 'system' COMMENT '由 SDK 登记，恒为 system',
  PRIMARY KEY (id),
  UNIQUE KEY uk_env_private_ip (env_id, private_ip),
  CONSTRAINT fk_instance_env FOREIGN KEY (env_id) REFERENCES env (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT 'SDK 上报过的实例，作为按 IP 设值的候选列表';

-- actor 改名 operator，与其余表统一。这张表只追加不更新，updated_at 恒等于 created_at。
CREATE TABLE IF NOT EXISTS flag_change_log (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  env_id         BIGINT       NOT NULL,
  flag_key       VARCHAR(150) NOT NULL,
  operator       VARCHAR(200) NOT NULL COMMENT '操作人 email',
  action         VARCHAR(30)  NOT NULL COMMENT 'CREATE / UPDATE_DEFAULT / SET_INSTANCE / CLEAR_INSTANCE / SAVE_RULES / ROLLBACK',
  before_json    JSON         NULL,
  after_json     JSON         NULL,
  config_version BIGINT       NOT NULL,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_env_flag_time (env_id, flag_key, created_at),
  CONSTRAINT fk_log_env FOREIGN KEY (env_id) REFERENCES env (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '变更历史：只增不删，回滚也是追加';

-- 原来主键是 token。为对齐 BaseEntity 换成代理主键，token 降级为唯一键；
-- 会话的查删按 token 条件走，不再用 selectById。
CREATE TABLE IF NOT EXISTS user_session (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  token      CHAR(32)    NOT NULL,
  user_id    BIGINT      NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  operator   VARCHAR(200) NOT NULL DEFAULT 'system' COMMENT '登录者 email',
  PRIMARY KEY (id),
  UNIQUE KEY uk_token (token),
  KEY idx_user (user_id),
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES platform_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '管理端会话（v1 不引 JWT）';
