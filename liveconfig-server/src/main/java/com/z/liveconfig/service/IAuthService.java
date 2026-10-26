package com.z.liveconfig.service;

import com.z.liveconfig.dto.LoginDTO;
import com.z.liveconfig.vo.LoginVO;

public interface IAuthService {

    LoginVO login(LoginDTO dto);

    void logout(String token);
}
