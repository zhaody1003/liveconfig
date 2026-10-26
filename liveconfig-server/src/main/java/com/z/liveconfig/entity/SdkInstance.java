package com.z.liveconfig.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** SDK 每次 evaluate 顺带登记/刷新；按 IP 设值时的候选列表来自这里 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sdk_instance")
public class SdkInstance extends BaseEntity {

    private Long envId;
    private String privateIp;
    private String publicIp;
    private String appName;

    /** 心跳时间，语义不同于基类的 updatedAt */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastSeenAt;
}
