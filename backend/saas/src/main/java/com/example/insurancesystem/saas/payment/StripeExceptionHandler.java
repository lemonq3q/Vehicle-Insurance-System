package com.example.insurancesystem.saas.payment;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 仅处理Stripe支付和回调控制器未被显式HTTP业务异常处理器接管的失败。
 * 参数绑定错误为400、访问拒绝为403、历史业务错误保留错误码、未知故障为500；不会返回HTTP200失败外壳。
 * 作用域基于控制器类型而非URL判断，优先于历史通用异常处理器，但低于显式HTTP业务异常处理器。
 */
@RestControllerAdvice(assignableTypes = {StripeWebhookController.class, StripePaymentController.class})
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class StripeExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(StripeExceptionHandler.class);
    /**
     * 兼容共享财务及支付方法的既有业务异常，协议出口始终采用真实4xx/5xx；非法码回退500。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseResult> handleBusiness(BusinessException exception) {
        int code = exception.getCode() == null ? 500 : exception.getCode();
        if (code < 400 || code > 599) code = 500;
        log.warn("Stripe业务失败：code={}, msg={}", code, exception.getMsg());
        return ResponseEntity.status(code).body(new ResponseResult(code, exception.getMsg(), exception.getData()));
    }

    /**
     * Spring在方法调用前发现缺失或非法参数时，入口try/catch无法接住；在MVC异常层统一返回真实400。
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ResponseResult> handleParameters(Exception exception) {
        log.warn("Stripe请求参数无效：{}", exception.getMessage());
        return ResponseEntity.badRequest().body(new ResponseResult(400, "支付请求参数无效"));
    }

    /**
     * 方法级权限拒绝保持真实403，不泄露权限细节，也不能误报回调成功。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseResult> handleDenied(AccessDeniedException exception) {
        return ResponseEntity.status(403).body(new ResponseResult(403, "不允许访问"));
    }

    /**
     * 数据库、网络、空指针等未知故障记录服务端堆栈，对外只返回真实500和通用文案。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseResult> handleUnexpected(Exception exception) {
        log.error("Stripe支付处理失败", exception);
        return ResponseEntity.status(500).body(new ResponseResult(500, "支付处理失败，请稍后重试"));
    }
}
