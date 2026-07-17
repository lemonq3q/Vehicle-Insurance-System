package com.example.insurancesystem.handler;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获主动抛出的 BusinessException
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseResult handleBusinessException(BusinessException e) {
        log.error("业务异常：code={}, msg={}", e.getCode(), e.getMsg());
        return new ResponseResult(e.getCode(), e.getMsg(), e.getData());
    }

    /**
     * 捕获所有其他未知异常（防止前端500）
     */
    @ExceptionHandler(Exception.class)
    public ResponseResult handleException(Exception e) {
        log.error("系统异常", e);
        return new ResponseResult(500, "服务器异常，请稍后再试");
    }
}
