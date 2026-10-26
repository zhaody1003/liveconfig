package com.z.liveconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 变更历史：只增不删，回滚也是追加一条。操作人用基类的 operator */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flag_change_log")
public class FlagChangeLog extends BaseEntity {

    private Long envId;
    private String flagKey;

    /** CREATE / UPDATE_DEFAULT / SET_INSTANCE / CLEAR_INSTANCE / SAVE_RULES / ROLLBACK */
    private String action;

    private String beforeJson;
    private String afterJson;

    /** 变更后的环境配置版本，回滚按它定位 */
    private Long configVersion;
}
