package com.example.insurancesystem.handler;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.HttpBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局转换显式HTTP业务异常，不依赖URL或控制器判断；优先于协议兜底和历史业务外壳。
 * 只捕获HttpBusinessException，不接管普通BusinessException，保证现有接口兼容。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class HttpBusinessExceptionHandler {
    /**
     * 将业务指定错误码写入真实HTTP状态，同时保留JSON错误信息和附加数据供前端统一反馈。
     */
    @ExceptionHandler(HttpBusinessException.class)
    public ResponseEntity<ResponseResult> handle(HttpBusinessException exception) {
        int status = exception.getCode() == null ? 500 : exception.getCode();
        if (status < 400 || status > 599) status = 500;
        log.warn("HTTP业务失败：status={}, msg={}", status, exception.getMsg());
        return ResponseEntity.status(status).body(new ResponseResult(status, exception.getMsg(), exception.getData()));
    }
}
