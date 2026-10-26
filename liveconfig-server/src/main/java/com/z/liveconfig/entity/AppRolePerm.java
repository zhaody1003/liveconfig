package com.z.liveconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 角色的权限点集合。唯一性靠 (role_id, perm)，代理主键见 BaseEntity */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_role_perm")
public class AppRolePerm extends BaseEntity {

    private Long roleId;
    private String perm;
}
