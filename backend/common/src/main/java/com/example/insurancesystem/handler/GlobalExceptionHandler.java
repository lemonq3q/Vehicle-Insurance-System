package com.example.insurancesystem.handler;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@RestControllerAdvice
@Slf4j
/**
 * 将控制器和业务层异常统一转换为 ResponseResult，保证车险端与 SaaS 端获得稳定响应契约，
 * 同时按可预期业务异常、权限异常和未知系统异常采用不同日志级别及对外信息。
 */
public class GlobalExceptionHandler {

    /**
     * 处理业务层主动抛出的可预期失败，保留业务码、提示和附加数据供前端采取针对性动作。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseResult handleBusinessException(BusinessException e) {
        log.error("业务异常：code={}, msg={}", e.getCode(), e.getMsg());
        return new ResponseResult(e.getCode(), e.getMsg(), e.getData());
    }

    /**
     * 处理方法级鉴权等在控制器调用阶段抛出的 AccessDeniedException，返回不包含内部权限细节的 403 响应。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseResult handleAccessDeniedException(AccessDeniedException e) {
        log.warn("访问被拒绝：{}", e.getMessage());
        return new ResponseResult(403, "不允许访问");
    }

    /**
     * 兜底记录完整未知异常堆栈，但只向客户端返回通用 500 文案，避免泄露数据库、文件路径等内部实现信息。
     */
    @ExceptionHandler(Exception.class)
    public ResponseResult handleException(Exception e) {
        log.error("系统异常", e);
        return new ResponseResult(500, "服务器异常，请稍后再试");
    }
}
