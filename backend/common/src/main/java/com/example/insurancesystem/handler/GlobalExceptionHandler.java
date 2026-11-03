package com.example.insurancesystem.handler;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import javax.servlet.http.HttpServletRequest;

@RestControllerAdvice
@Slf4j
/**
 * 将控制器和业务层异常统一转换为 ResponseResult，保证车险端与 SaaS 端获得稳定响应契约，
 * 同时按可预期业务异常、权限异常和未知系统异常采用不同日志级别及对外信息。
 * 仅作为历史业务外壳兜底；显式HTTP异常和Stripe作用域异常由更高优先级处理器负责，无URL判断。
 */
public class GlobalExceptionHandler {

    /**
     * 手工并发保存上下游时由数据库有效名称唯一索引兜底，转为可理解的业务冲突。
     * 其他唯一约束失败仍使用通用冲突提示，响应中不泄露 SQL、索引数据或租户信息。
     */
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<ResponseResult> handleDuplicateKey(org.springframework.dao.DuplicateKeyException e) {
        String message = e.getMostSpecificCause().getMessage();
        return ResponseEntity.ok(new ResponseResult(400,
                message != null && message.contains("uk_biz_merchant_active_name")
                        ? "当前企业已存在同名上游机构或下游商家" : "数据已存在，请勿重复提交"));
    }

    /**
     * 处理业务层主动抛出的可预期失败，保留业务码、提示和附加数据供前端采取针对性动作。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseResult> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.error("业务异常：code={}, msg={}", e.getCode(), e.getMsg());
        return ResponseEntity.ok(new ResponseResult(e.getCode(), e.getMsg(), e.getData()));
    }

    /**
     * 处理方法级鉴权等在控制器调用阶段抛出的 AccessDeniedException，返回不包含内部权限细节的 403 响应。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseResult> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        log.warn("访问被拒绝：{}", e.getMessage());
        return ResponseEntity.ok(new ResponseResult(403, "不允许访问"));
    }

    /**
     * 兜底记录完整未知异常堆栈，但只向客户端返回通用 500 文案，避免泄露数据库、文件路径等内部实现信息。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseResult> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常", e);
        return ResponseEntity.ok(new ResponseResult(500, "服务器异常，请稍后再试"));
    }

}
