package com.z.liveconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** v1 用会话表做管理端认证，不引入 JWT 体系。按 token 读写，token 上有唯一键 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_session")
public class UserSession extends BaseEntity {

    private String token;
    private Long userId;
    private LocalDateTime expiresAt;
}
