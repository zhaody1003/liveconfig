package com.z.liveconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sdk_key")
public class SdkKey extends BaseEntity {

    private Long envId;

    /** SERVER / CLIENT */
    private String scope;

    /** 只存 SHA-256，明文仅在创建响应里出现一次 */
    private String keyHash;
    private String keyMasked;
}
