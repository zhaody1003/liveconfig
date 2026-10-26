package com.z.liveconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("flag_instance_override")
public class FlagInstanceOverride extends BaseEntity {

    private Long flagId;

    /** 内网 IP：VPC 内唯一，作设值主键 */
    private String privateIp;

    /** 公网 IP：NAT 后可能多机共享，仅展示不作主键 */
    private String publicIp;

    private String value;
}
