package com.z.liveconfig.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.z.liveconfig.common.BizException;
import com.z.liveconfig.dto.LoginDTO;
import com.z.liveconfig.entity.PlatformUser;
import com.z.liveconfig.security.SessionService;
import com.z.liveconfig.service.IAuthService;
import com.z.liveconfig.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final SessionService sessionService;

    @Override
    public LoginVO login(LoginDTO dto) {
        PlatformUser user = sessionService.findByEmail(dto.getEmail());
        // 账号不存在与口令错误回同一句话，不告诉调用方哪个邮箱注册过
        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPasswordHash())) {
            throw BizException.unauthorized("邮箱或密码不正确");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw BizException.forbidden("账号已停用，请联系平台管理员");
        }
        return LoginVO.of(sessionService.login(user), user);
    }

    @Override
    public void logout(String token) {
        sessionService.logout(token);
    }
}
