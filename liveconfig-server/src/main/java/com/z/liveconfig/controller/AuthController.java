package com.z.liveconfig.controller;

import com.z.common.response.Result;
import com.z.liveconfig.dto.LoginDTO;
import com.z.liveconfig.security.SessionService;
import com.z.liveconfig.service.IAuthService;
import com.z.liveconfig.vo.LoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;
    private final SessionService sessionService;

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @PostMapping("/logout")
    public Result<Boolean> logout(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        sessionService.logout(header != null && header.startsWith("Bearer ")
                ? header.substring(7).trim() : null);
        return Result.success();
    }
}
