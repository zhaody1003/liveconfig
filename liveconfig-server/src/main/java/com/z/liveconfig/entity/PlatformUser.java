package com.z.liveconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("platform_user")
public class PlatformUser extends BaseEntity {

    private String email;
    private String name;
    private String passwordHash;
    private String platformRole;
    private String status;
}
