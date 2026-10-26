package com.z.liveconfig.security;

/**
 * 权限点。枚举只是"校验目标"的清单，角色由哪几个权限点组成是数据库里的数据（支持自定义角色）。
 */
public enum Perm {

    FLAG_READ("flag:read", "查看开关"),
    FLAG_WRITE("flag:write", "创建/编辑开关与规则"),
    VALUE_NONPROD("value:nonprod", "改非高危环境的值"),
    VALUE_PROD("value:prod", "改高危环境的值 / 放量 / 回滚"),
    INSTANCE_WRITE("instance:write", "按服务器 IP 设值"),
    ENV_MANAGE("env:manage", "环境与 SDK Key"),
    MEMBER_MANAGE("member:manage", "成员与角色");

    private final String code;
    private final String label;

    Perm(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static Perm ofCode(String code) {
        for (Perm p : values()) {
            if (p.code.equals(code)) {
                return p;
            }
        }
        throw new IllegalArgumentException("未知权限点：" + code);
    }
}
