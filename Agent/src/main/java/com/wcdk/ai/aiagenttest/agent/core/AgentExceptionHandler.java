package com.wcdk.ai.aiagenttest.agent.core;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public class AgentExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse badRequest(IllegalArgumentException exception) {
        log.error("请求参数异常。", exception);
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse validationFailed(MethodArgumentNotValidException exception) {
        log.error("请求参数校验失败。", exception);
        var message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "请求参数校验失败。" : error.getDefaultMessage())
                .orElse("请求参数校验失败。");
        return ErrorResponse.of(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse serviceUnavailable(IllegalStateException exception) {
        log.error("服务不可用。", exception);
        return ErrorResponse.of(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse internalServerError(Exception exception) {
        log.error("服务端异常。", exception);
        return ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    public record ErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message
    ) {
        static ErrorResponse of(HttpStatus status, String message) {
            return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message);
        }
    }
}
