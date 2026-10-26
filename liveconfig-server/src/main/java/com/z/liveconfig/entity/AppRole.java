package com.z.liveconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_role")
public class AppRole extends BaseEntity {

    private Long appId;
    private String roleKey;
    private String name;
    private Boolean builtin;
}
