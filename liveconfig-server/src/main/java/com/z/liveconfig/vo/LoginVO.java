package com.z.liveconfig.vo;

import com.z.liveconfig.entity.PlatformUser;
import lombok.Data;

@Data
public class LoginVO {

    private String token;
    private String email;
    private String name;
    private String platformRole;

    public static LoginVO of(String token, PlatformUser user) {
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setEmail(user.getEmail());
        vo.setName(user.getName());
        vo.setPlatformRole(user.getPlatformRole());
        return vo;
    }
}
