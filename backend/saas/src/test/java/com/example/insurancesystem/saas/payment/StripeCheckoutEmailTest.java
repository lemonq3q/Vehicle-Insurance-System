package com.example.insurancesystem.saas.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.insurancesystem.saas.mapper.StripePaymentMapper;
import com.example.insurancesystem.saas.service.FinanceService;
import com.example.insurancesystem.saas.support.PortalContextService;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 验证付款默认邮箱来自当前账号，无邮箱允许手填，旧会话保留已有付款资料。
 * 外部 Stripe、数据库及登录上下文全部隔离，不创建真实订单或触发真实付款。
 */
class StripeCheckoutEmailTest {
  private StripePaymentMapper mapper;
  private StripeCheckoutClient client;
  private StripePaymentService service;
  private Session session;

  /**
   * 构造当前企业的待付款订单和 Stripe 返回快照，保留生产中的权限检查路径。
   */
  @BeforeEach
  void setUp() throws Exception {
    mapper = mock(StripePaymentMapper.class);
    client = mock(StripeCheckoutClient.class);
    PortalContextService context = mock(PortalContextService.class);
    when(context.requireRoles("OWNER", "ADMIN")).thenReturn(Map.of("enterpriseId", 9L));
    when(context.userId()).thenReturn(7L);
    when(mapper.findRechargeOrder(1L, 9L)).thenReturn(Map.of("status", 1, "amount", "10.00", "rechargeNo", "R001"));
    when(mapper.bindCheckoutSession(eq(1L), eq(9L), anyString(), anyLong())).thenReturn(1);
    StripePaymentProperties properties = new StripePaymentProperties();
    properties.setEnabled(true);
    properties.setSecretKey("sk_test_local");
    properties.setPublishableKey("pk_test_local");
    properties.setReturnUrl("https://example.com/orders/{orderId}");
    service = new StripePaymentService(properties, mapper, context, mock(FinanceService.class), client);
    session = new Session();
    session.setId("cs_test");
    session.setExpiresAt(2000000000L);
    session.setClientSecret("test_secret");
    when(client.create(any(), any())).thenReturn(session);
  }

  /**
   * 已绑定邮箱规范化后传给 Stripe；同时验证仍保留订单幂等键以避免重复付款。
   */
  @Test
  void newCheckoutUsesCurrentAccountEmail() throws Exception {
    when(mapper.findUserEmail(7L)).thenReturn(" User@Example.com ");
    session.setCustomerEmail("user@example.com");
    assertEquals("user@example.com", service.createCheckoutSession(1L).get("email"));
    ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
    ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
    verify(client).create(params.capture(), options.capture());
    assertEquals("user@example.com", params.getValue().getCustomerEmail());
    assertEquals("saas-recharge-R001", options.getValue().getIdempotencyKey());
  }

  /**
   * 当前账号没有邮箱时不伪造默认值，允许 Stripe 展示正常人工输入入口。
   */
  @Test
  void accountWithoutEmailCanStillPay() throws Exception {
    service.createCheckoutSession(1L);
    ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
    verify(client).create(params.capture(), any());
    assertNull(params.getValue().getCustomerEmail());
  }

  /**
   * 已有付款会话复用其邮箱，不因用户随后绑定邮箱而覆盖付款人输入。
   */
  @Test
  void reusedCheckoutPreservesItsEmail() throws Exception {
    when(mapper.findRechargeOrder(1L, 9L)).thenReturn(Map.of("status", 1, "amount", "10.00", "rechargeNo", "R001", "stripeCheckoutSessionId", "cs_test"));
    session.setStatus("open");
    session.setPaymentStatus("unpaid");
    session.setCustomerEmail("payer@example.com");
    when(client.retrieve("cs_test")).thenReturn(session);
    assertEquals("payer@example.com", service.createCheckoutSession(1L).get("email"));
    verify(client, never()).create(any(), any());
    verify(mapper, never()).findUserEmail(anyLong());
  }
}
