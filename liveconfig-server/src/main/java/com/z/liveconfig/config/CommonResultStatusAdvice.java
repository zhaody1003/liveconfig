package com.z.liveconfig.config;

import com.z.common.response.Result;
import com.z.liveconfig.common.BizException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * com.z:common 的 ResultEnum 把「未登录」这类错误码定为 500/501，
 * 直接透传会让前端以为服务器炸了。这里按语义把 HTTP 状态改对，
 * 响应体仍用你公共库的 Result 结构（message 保持不变）。
 */
@RestControllerAdvice
@Order(1)
public class CommonResultStatusAdvice {

    /** 公共库只有 Result.error（固定 code 500），未登录/无权限这类语义只能我们自己补 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        HttpStatus status = switch (e.getCode()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        Result<Void> body = Result.error(e.getMessage());
        body.setCode(e.getCode());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoRoute(NoResourceFoundException e) {
        Result<Void> body = Result.error("接口不存在：" + e.getResourcePath());
        body.setCode(404);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
