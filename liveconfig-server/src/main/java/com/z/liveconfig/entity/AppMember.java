package com.z.liveconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 成员 = 用户 × 应用 × 环境 → 角色，即成员矩阵的一格 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_member")
public class AppMember extends BaseEntity {

    private Long appId;
    private Long userId;
    private Long envId;
    private Long roleId;
}
