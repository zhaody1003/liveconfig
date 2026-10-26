package com.z.liveconfig.vo;

import lombok.Data;

/** 角色卡片：权限点 + 使用人数 */
@Data
public class RoleVO {

    private Long id;
    private Long appId;
    private String roleKey;
    private String name;
    private Boolean builtin;
    /** XML 里 GROUP_CONCAT 出来的原始列，Service 再切成 perms */
    private String permCsv;
    private java.util.List<String> perms;
    private Integer usedBy;
}
