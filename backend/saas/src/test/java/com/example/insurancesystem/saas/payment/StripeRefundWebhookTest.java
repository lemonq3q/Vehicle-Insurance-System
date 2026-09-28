package com.example.insurancesystem.saas.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.saas.mapper.StripePaymentMapper;
import com.example.insurancesystem.saas.service.FinanceService;
import com.example.insurancesystem.saas.support.PortalContextService;
import com.stripe.Stripe;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 使用真实Stripe签名验证覆盖退款事件路由，远程查询和财务记账均由mock隔离。
 * 重点验证不同事件的同一Refund读取最新状态、重复事件短路、验签失败和乱序补偿入账，
 * 不触发任何真实退款或真实钱包变更。
 */
class StripeRefundWebhookTest {
  private static final String SECRET = "whsec_refund_test";
  private StripePaymentMapper mapper;
  private StripeCheckoutClient client;
  private FinanceService finance;
  private StripePaymentService service;
  private Refund latest;

  /** 构造本地已支付充值及最新退款快照，不需要登录用户或退款metadata。 */
  @BeforeEach
  void setUp() {
    mapper = mock(StripePaymentMapper.class);
    client = mock(StripeCheckoutClient.class);
    finance = mock(FinanceService.class);
    StripePaymentProperties properties = new StripePaymentProperties();
    properties.setEnabled(true);
    properties.setWebhookSecret(SECRET);
    properties.setCurrency("cny");
    service = new StripePaymentService(properties, mapper, mock(PortalContextService.class), finance, client);
    Map<String, Object> order = Map.of("id", 1L, "enterpriseId", 9L, "status", 2);
    when(mapper.findRechargeOrderByPaymentIntent("pi_test")).thenReturn(order);
    when(mapper.lockRechargeOrder(1L, 9L)).thenReturn(order);
    when(mapper.insertWebhookEvent(anyString(), anyString(), isNull(), anyString())).thenReturn(1);
    latest = new Refund();
    latest.setId("re_test");
    latest.setPaymentIntent("pi_test");
    latest.setAmount(3000L);
    latest.setCurrency("cny");
    latest.setStatus("succeeded");
    when(client.retrieveRefund("re_test")).thenReturn(latest);
  }

  /** created事件也可能已成功，必须按最新退款快照而不是仅按事件名称决定记账。 */
  @Test
  void createdEventRoutesToRefundAccounting() throws Exception {
    String payload = payload("refund.created", "pending");
    service.handleWebhook(payload, signature(payload));
    verify(finance).reconcileStripeRefund("re_test", "pi_test", "cny", 3000L, "succeeded");
    verify(finance, never()).completeStripeRecharge(anyString(), anyString());
  }

  /** 同一evt_已成功处理时短路，不再查询Stripe或再次执行财务业务。 */
  @Test
  void duplicateEventDoesNotCallRemoteOrFinance() throws Exception {
    when(mapper.insertWebhookEvent(anyString(), anyString(), isNull(), anyString())).thenReturn(0);
    String payload = payload("refund.updated", "succeeded");
    service.handleWebhook(payload, signature(payload));
    verifyNoInteractions(client, finance);
  }

  /** 迟到的成功事件不能覆盖最新失败状态，财务按最新失败快照补回此前回撤额。 */
  @Test
  void staleEventUsesCurrentStripeStatus() throws Exception {
    latest.setStatus("failed");
    String payload = payload("refund.updated", "succeeded");
    service.handleWebhook(payload, signature(payload));
    verify(finance).reconcileStripeRefund("re_test", "pi_test", "cny", 3000L, "failed");
  }

  /** 签名错误的请求在写入幂等审计和查询钱包之前被拒绝。 */
  @Test
  void invalidSignatureIsRejectedBeforeAnyMutation() {
    assertThrows(BusinessException.class,
        () -> service.handleWebhook(payload("refund.failed", "failed"), "t=1,v1=invalid"));
    verifyNoInteractions(mapper, client, finance);
  }

  /** 共享Stripe账号下与门户充值无关的退款只审计，不根据可修改的metadata扣错企业。 */
  @Test
  void unrelatedPaymentIsIgnored() throws Exception {
    when(mapper.findRechargeOrderByPaymentIntent("pi_test")).thenReturn(null);
    when(client.findSessionByPaymentIntent("pi_test")).thenReturn(null);
    String payload = payload("refund.created", "succeeded");
    service.handleWebhook(payload, signature(payload));
    verifyNoInteractions(finance);
    verify(client, never()).retrieveRefund(anyString());
  }

  /** 退款通知先到时先补偿已确认付款的充值，再回撤，不能造成只扣未入账资金。 */
  @Test
  void outOfOrderRefundRecoversThePaidRechargeFirst() throws Exception {
    Map<String, Object> pending = Map.of("id", 1L, "enterpriseId", 9L, "status", 1,
        "amount", "100.00", "stripeCheckoutSessionId", "cs_test");
    when(mapper.findRechargeOrderByPaymentIntent("pi_test")).thenReturn(null);
    when(mapper.findRechargeOrderBySession("cs_test")).thenReturn(pending);
    when(mapper.lockRechargeOrder(1L, 9L)).thenReturn(pending);
    Session session = new Session();
    session.setId("cs_test");
    session.setPaymentIntent("pi_test");
    session.setStatus("complete");
    session.setPaymentStatus("paid");
    session.setAmountTotal(10000L);
    session.setCurrency("cny");
    when(client.findSessionByPaymentIntent("pi_test")).thenReturn(session);
    String payload = payload("refund.updated", "succeeded");
    service.handleWebhook(payload, signature(payload));
    var sequence = inOrder(finance);
    sequence.verify(finance).completeStripeRecharge("cs_test", "pi_test");
    sequence.verify(finance).reconcileStripeRefund("re_test", "pi_test", "cny", 3000L, "succeeded");
  }

  /** 模拟与SDK一致API版本的最小Refund事件，故意不提供metadata以测试原付款关联。 */
  private String payload(String type, String status) {
    return "{\"id\":\"evt_refund_test\",\"object\":\"event\",\"api_version\":\"" + Stripe.API_VERSION
        + "\",\"type\":\"" + type + "\",\"data\":{\"object\":{\"id\":\"re_test\",\"object\":\"refund\","
        + "\"payment_intent\":\"pi_test\",\"amount\":3000,\"currency\":\"cny\",\"status\":\"" + status + "\"}}}";
  }

  /** 按Stripe的时间戳加原始负载生成HMAC，测试真实验签路径而不绕过安全边界。 */
  private String signature(String payload) throws Exception {
    long timestamp = Instant.now().getEpochSecond();
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder();
    for (byte value : digest) hex.append(String.format("%02x", value & 255));
    return "t=" + timestamp + ",v1=" + hex;
  }
}
