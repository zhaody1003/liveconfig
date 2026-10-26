package com.z.liveconfig.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.z.liveconfig.common.BizException;
import com.z.liveconfig.entity.PlatformUser;
import com.z.liveconfig.entity.UserSession;
import com.z.liveconfig.mapper.PlatformUserMapper;
import com.z.liveconfig.mapper.UserSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserSessionMapper sessionMapper;
    private final PlatformUserMapper userMapper;

    @Value("${liveconfig.session.hours:12}")
    private long sessionHours;

    public String login(PlatformUser user) {
        UserSession s = new UserSession();
        s.setToken(UUID.randomUUID().toString().replace("-", ""));
        s.setUserId(user.getId());
        // 登录请求还没有会话，自动填充拿不到登录用户，这里显式写明是谁的会话
        s.setOperator(user.getEmail());
        s.setExpiresAt(LocalDateTime.now().plusHours(sessionHours));
        sessionMapper.insert(s);
        return s.getToken();
    }

    public void logout(String token) {
        if (token != null) {
            sessionMapper.delete(byToken(token));
        }
    }

    public PlatformUser resolve(String token) {
        if (token == null || token.isBlank()) {
            throw BizException.unauthorized("未登录");
        }
        UserSession s = sessionMapper.selectOne(byToken(token));
        if (s == null || s.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw BizException.unauthorized("登录已过期，请重新登录");
        }
        PlatformUser user = userMapper.selectById(s.getUserId());
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw BizException.unauthorized("账号不可用");
        }
        return user;
    }

    public PlatformUser findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<PlatformUser>()
                .eq(PlatformUser::getEmail, email)
                .last("LIMIT 1"));
    }

    /** token 上有唯一键，但不再是主键，所以按条件查删 */
    private static LambdaQueryWrapper<UserSession> byToken(String token) {
        return new LambdaQueryWrapper<UserSession>().eq(UserSession::getToken, token);
    }
}
