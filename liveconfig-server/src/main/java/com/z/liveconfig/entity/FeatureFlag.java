package com.z.liveconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("feature_flag")
public class FeatureFlag extends BaseEntity {

    private Long envId;
    private String flagKey;
    private String name;

    /** BOOLEAN / STRING / NUMBER / JSON */
    private String valueType;

    /** 统一存字符串，写入前经 ValueTypes 校验 */
    private String defaultValue;

    private Boolean enabled;
    private Integer version;
}
