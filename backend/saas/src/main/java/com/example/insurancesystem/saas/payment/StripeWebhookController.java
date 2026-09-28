package com.example.insurancesystem.saas.payment;

import com.example.insurancesystem.handler.exception.HttpBusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe服务器回调的独立入口，沿用原始请求体和签名验证，不要求用户JWT。
 * 仅成功处理或幂等忽略后返回200，所有失败交给HTTP异常处理器及Stripe专属兜底。
 */
@RestController
@RequestMapping("/portal/payment/stripe")
public class StripeWebhookController {
    private final StripePaymentService service;

    /**
     * 注入现有支付服务，控制器不重复实现验签、事件幂等或财务事务。
     */
    public StripeWebhookController(StripePaymentService service) { this.service = service; }

    /**
     * 签名缺失主动抛出HTTP400，业务失败直接向异常处理链传播；不在入口吞掉异常或返回成功外壳。
     * payload来自原始body，signature来自Stripe-Signature，成功返回空body的HTTP200。
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (signature == null || signature.isBlank()) throw new HttpBusinessException(400, "Stripe Webhook 签名缺失");
        service.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
