package com.z.liveconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("env")
public class Env extends BaseEntity {

    private Long appId;
    private String envKey;
    private String color;

    /** 高危环境：写值需要 value:prod 权限。用字段表达，不靠环境名叫 prod */
    private Boolean highRisk;

    /** 环境级配置版本，任何变更 +1，SDK 据此判断本地快照是否过期 */
    private Long configVersion;
}
