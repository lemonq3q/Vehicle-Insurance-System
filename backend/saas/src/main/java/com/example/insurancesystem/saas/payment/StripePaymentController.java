package com.example.insurancesystem.saas.payment;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * SaaS 门户 Stripe 支付接口。
 * Checkout Session接口沿用登录鉴权和租户隔离；失败交由Stripe专属异常处理器返回真实HTTP状态。
 * 服务器回调独立由StripeWebhookController处理，避免回调协议与门户接口职责混合。
 */
@RestController
@RequestMapping("/portal/payment/stripe")
public class StripePaymentController {
  private final StripePaymentService service;

  public StripePaymentController(StripePaymentService service) {
    this.service = service;
  }

  /** 为指定本地充值单创建或复用 Stripe Embedded Checkout Session。 */
  @PostMapping("/recharge-orders/{orderId}/checkout-session")
  public ResponseResult<Map<String, Object>> createCheckoutSession(@PathVariable Long orderId) {
    return new ResponseResult<>(200, "Stripe 结账会话已就绪", service.createCheckoutSession(orderId));
  }

}
