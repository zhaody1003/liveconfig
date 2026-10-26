package com.z.liveconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flag_rule")
public class FlagRule extends BaseEntity {

    private Long flagId;

    /** WHITELIST / BLACKLIST / ATTRIBUTE / ROLLOUT */
    private String kind;

    /** 同 kind 内生效顺序；跨 kind 由引擎流水线固定 */
    private Integer priority;

    /** JSON 文本，形态见 docs/TECH-DESIGN.md §2.1 */
    private String config;

    /** 命中后返回的值 */
    private String value;
}
