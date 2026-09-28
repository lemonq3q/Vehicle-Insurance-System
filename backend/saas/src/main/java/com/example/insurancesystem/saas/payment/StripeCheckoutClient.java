package com.example.insurancesystem.saas.payment;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.stripe.StripeClient;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionListParams;
import org.springframework.stereotype.Component;

/**
 * Stripe Checkout Session 的窄接口客户端。
 * 统一封装查询和主动过期能力，供支付服务、人工取消流程和定时补偿复用；Secret Key
 * 始终只保留在服务端，Stripe 网络异常统一转换为可重试的业务异常。
 */
@Component
public class StripeCheckoutClient {
  private final StripePaymentProperties properties;

  public StripeCheckoutClient(StripePaymentProperties properties) {
    this.properties = properties;
  }

  /**
   * 查询 Stripe 中的 Checkout Session 最新状态。
   *
   * @param sessionId 本地充值订单已绑定的 Stripe Session ID
   * @return Stripe 返回的 Session 快照
   */
  public Session retrieve(String sessionId) {
    validateSecretKey();
    try {
      return client().v1().checkout().sessions().retrieve(sessionId);
    } catch (Exception exception) {
      throw new BusinessException(502, "Stripe 结账会话查询失败，请稍后重试");
    }
  }

  /**
   * 验签后查询退款当前状态，避免过期的 created 或 updated 通知覆盖已经失败的退款。
   * 调用方持有原充值订单锁，确保并发通知按锁内查询的最新快照串行记账。
   *
   * @param refundId 已验签事件中的 Refund ID
   * @return Stripe退款最新快照；网络故障抛出异常使 Webhook 事务回滚并等待重试
   */
  public Refund retrieveRefund(String refundId) {
    validateSecretKey();
    try {
      return client().v1().refunds().retrieve(refundId);
    } catch (Exception exception) {
      throw new BusinessException(502, "Stripe 退款查询失败，请稍后重试");
    }
  }

  /**
   * 根据退款原付款查找 Checkout，补偿退款通知早于支付通知的乱序情况。
   * 不根据退款 metadata 选择企业，只有本地已绑定 Session 的充值才允许记账。
   *
   * @param paymentIntentId 原付款标识
   * @return 对应Session，不属于Checkout付款时返回null
   */
  public Session findSessionByPaymentIntent(String paymentIntentId) {
    validateSecretKey();
    try {
      var sessions = client().v1().checkout().sessions().list(
          SessionListParams.builder().setPaymentIntent(paymentIntentId).setLimit(1L).build());
      return sessions.getData().isEmpty() ? null : sessions.getData().get(0);
    } catch (Exception exception) {
      throw new BusinessException(502, "Stripe 退款原结账会话查询失败，请稍后重试");
    }
  }

  /**
   * 少数退款负载仅含 Charge 时，查询原付款标识；旧式非PaymentIntent付款不属于本充值链路。
   *
   * @param chargeId 已验签退款中的Charge ID
   * @return 原PaymentIntent ID，旧式付款可能为空
   */
  public String findPaymentIntentByCharge(String chargeId) {
    validateSecretKey();
    try {
      return client().v1().charges().retrieve(chargeId).getPaymentIntent();
    } catch (Exception exception) {
      throw new BusinessException(502, "Stripe 退款原付款查询失败，请稍后重试");
    }
  }

  /**
   * 主动使仍为 open 的 Checkout Session 过期。
   * 调用方必须检查返回状态；当查询时 Session 已 complete 或 expired 时不再重复调用
   * expire，避免把已完成或异步处理中的付款错误当作可取消支付。
   *
   * @param sessionId Stripe Checkout Session ID
   * @return 主动过期后的状态，或查询到的既有终态
   */
  public Session expireIfOpen(String sessionId) {
    Session session = retrieve(sessionId);
    if (!"open".equals(session.getStatus())) return session;
    try {
      return client().v1().checkout().sessions().expire(sessionId);
    } catch (Exception exception) {
      /*
       * 查询与 expire 之间可能刚好完成支付。再次查询真实状态，让上层决定是拒绝取消、
       * 标记处理中还是过期，而不是在竞态中直接修改本地订单。
       */
      return retrieve(sessionId);
    }
  }

  private StripeClient client() {
    return new StripeClient(properties.getSecretKey());
  }

  private void validateSecretKey() {
    if (properties.getSecretKey() == null || properties.getSecretKey().isBlank())
      throw new BusinessException(503, "Stripe Secret Key 尚未配置");
  }
}
