package com.z.liveconfig.common;

import com.z.common.response.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 参数校验与兜底异常。
 *
 * 顺序必须比 {@link com.z.liveconfig.config.CommonResultStatusAdvice} 靠后：
 * 这里有一个 Exception.class 的兜底处理器，若它先命中，BizException 会被当成 500。
 */
@Slf4j
@RestControllerAdvice
@Order(10)
public class GlobalExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<Void>> handleBinding(BindException e) {
        FieldError first = e.getBindingResult().getFieldError();
        String message = first == null ? "参数校验失败" : first.getField() + " " + first.getDefaultMessage();
        return badRequest(message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("参数校验失败");
        return badRequest(message);
    }

    /**
     * SSE 客户端关页面时，容器会抛这类「连接已不可用」异常。
     * 它不是服务端故障，而且此时响应通道已经断了 —— 再往 response 里写结果只会二次抛错，
     * 所以返回 null 表示「不再处理」，同时不打 error 日志。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Result<Void>> handleClientGone(AsyncRequestNotUsableException e) {
        log.debug("客户端已断开，忽略：{}", e.getMessage());
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("未预期异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error("服务端异常：" + e.getMessage()));
    }

    private ResponseEntity<Result<Void>> badRequest(String message) {
        Result<Void> body = Result.error(message);
        body.setCode(400);
        return ResponseEntity.badRequest().body(body);
    }
}
