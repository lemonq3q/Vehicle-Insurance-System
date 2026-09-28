package com.example.insurancesystem.saas.payment;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.handler.exception.HttpBusinessException;
import com.example.insurancesystem.saas.mapper.StripePaymentMapper;
import com.example.insurancesystem.saas.service.FinanceService;
import com.example.insurancesystem.saas.support.PortalContextService;
import com.example.insurancesystem.saas.support.PortalMaps;
import com.example.insurancesystem.saas.support.RechargeOrderStatus;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stripe Embedded Checkout 的应用服务。
 * 登录用户只能为当前企业的待支付充值单创建 Session；匿名 Webhook 必须先通过 Stripe
 * 签名验证，再进行事件幂等、金额与币种复核，最终委托财务服务完成钱包入账或退款回撤。
 */
@Service
public class StripePaymentService {
  private final StripePaymentProperties properties;
  private final StripePaymentMapper mapper;
  private final PortalContextService context;
  private final FinanceService financeService;
  private final StripeCheckoutClient checkoutClient;
  private static final Duration CHECKOUT_SESSION_TTL = Duration.ofMinutes(30);

  public StripePaymentService(
      StripePaymentProperties properties,
      StripePaymentMapper mapper,
      PortalContextService context,
      FinanceService financeService,
      StripeCheckoutClient checkoutClient) {
    this.properties = properties;
    this.mapper = mapper;
    this.context = context;
    this.financeService = financeService;
    this.checkoutClient = checkoutClient;
  }

  /**
   * 为本地充值订单创建或复用一个 Stripe Checkout Session。
   * Session 使用动态金额 PriceData 和 embedded_page UI；不指定 payment_method_types，
   * 由 Stripe Dashboard、币种、地区与设备共同决定可用支付方式。
   *
   * @param orderId 当前企业的本地充值订单 ID
   * @return 前端初始化 Stripe.js 所需的发布密钥和 client secret
   */
  public Map<String, Object> createCheckoutSession(Long orderId) {
    validateCheckoutConfiguration();
    Long enterpriseId = ((Number) context.requireRoles("OWNER", "ADMIN").get("enterpriseId")).longValue();
    Map<String, Object> order = PortalMaps.camel(mapper.findRechargeOrder(orderId, enterpriseId));
    if (order == null) throw new BusinessException(404, "充值订单不存在");
    if (((Number) order.get("status")).intValue() != 1)
      throw new BusinessException(409, "仅待支付充值订单可以发起 Stripe 支付");

    BigDecimal amount = money(order.get("amount"));
    validateAmount(amount);
    String rechargeNo = String.valueOf(order.get("rechargeNo"));

    try {
      StripeClient client = new StripeClient(properties.getSecretKey());
      Object existingSessionId = order.get("stripeCheckoutSessionId");
      Session session;
      if (existingSessionId != null && !existingSessionId.toString().isBlank()) {
        session = checkoutClient.retrieve(existingSessionId.toString());
        validateReusableSession(session);
      } else {
        long expiresAt = Instant.now().plus(CHECKOUT_SESSION_TTL).getEpochSecond();
        SessionCreateParams params =
            SessionCreateParams.builder()
                .setUiMode(SessionCreateParams.UiMode.EMBEDDED_PAGE)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setReturnUrl(properties.getReturnUrl().replace("{orderId}", orderId.toString()))
                .setExpiresAt(expiresAt)
                .setClientReferenceId(rechargeNo)
                .putMetadata("business_type", "SAAS_BALANCE_RECHARGE")
                .putMetadata("order_id", orderId.toString())
                .putMetadata("enterprise_id", enterpriseId.toString())
                .putMetadata("recharge_no", rechargeNo)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(properties.getCurrency().toLowerCase(Locale.ROOT))
                                .setUnitAmount(amount.movePointRight(2).longValueExact())
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("iDatag 企业余额充值")
                                        .setDescription("充值单号：" + rechargeNo)
                                        .build())
                                .build())
                        .build())
                .build();
        RequestOptions requestOptions =
            RequestOptions.builder().setIdempotencyKey("saas-recharge-" + rechargeNo).build();
        session = client.v1().checkout().sessions().create(params, requestOptions);
        if (mapper.bindCheckoutSession(orderId, enterpriseId, session.getId(), session.getExpiresAt()) == 0)
          throw new BusinessException(409, "充值订单状态已变化，请刷新后重试");
      }
      if (session.getClientSecret() == null)
        throw new BusinessException(502, "Stripe 未返回嵌入式结账凭证");

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("orderId", orderId);
      result.put("sessionId", session.getId());
      result.put("uiMode", session.getUiMode());
      result.put("publishableKey", properties.getPublishableKey());
      result.put("clientSecret", session.getClientSecret());
      return result;
    } catch (BusinessException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BusinessException(502, "Stripe 结账会话创建失败，请稍后重试");
    }
  }

  /**
   * 用户主动取消充值订单时同步关闭 Stripe Session。
   * 本地订单行锁覆盖“查询 Stripe、主动过期、写入取消状态”全过程；若 Stripe 已完成
   * Checkout，则拒绝取消，防止出现外部已扣款但本地订单被取消而无法入账的情况。
   *
   * @param orderId 当前企业的充值订单 ID
   * @return 已取消的订单快照
   */
  @Transactional
  public Map<String, Object> cancelRecharge(Long orderId) {
    Long enterpriseId = ((Number) context.requireRoles("OWNER", "ADMIN").get("enterpriseId")).longValue();
    Map<String, Object> order = PortalMaps.camel(mapper.lockRechargeOrder(orderId, enterpriseId));
    if (order == null) throw new BusinessException(404, "充值订单不存在");
    int status = ((Number) order.get("status")).intValue();
    if (status == 3) return order;
    if (status != 1) throw new BusinessException(409, "只有待支付订单可以取消");

    Object sessionId = order.get("stripeCheckoutSessionId");
    if (sessionId != null && !sessionId.toString().isBlank()) {
      Session session = checkoutClient.expireIfOpen(sessionId.toString());
      if ("complete".equals(session.getStatus()))
        throw new BusinessException(409, "Stripe 结账已完成或正在确认付款，当前订单不能取消");
      if (!"expired".equals(session.getStatus()))
        throw new BusinessException(502, "Stripe 结账会话未能关闭，请稍后重试");
    }

    if (mapper.cancelRechargeOrder(orderId, enterpriseId) == 0)
      throw new BusinessException(409, "充值订单状态已变化，请刷新后重试");
    order.put("status", 3);
    return order;
  }

  /**
   * 验证并处理 Stripe Webhook。
   * 每个事件先写入带唯一索引的审计表；重复投递会直接成功返回。支付成功事件还会复核
   * 本地订单、总金额与币种，整个方法的事务保证事件记录与钱包入账同时提交或回滚。
   *
   * @param payload Stripe 发送的原始请求体，验签前不得重新序列化
   * @param signature Stripe-Signature 请求头
   */
  @Transactional
  public void handleWebhook(String payload, String signature) {
    /*
     * 保留公共方法事务边界，将共享财务服务主动抛出的业务失败转换为协议级HTTP异常。
     * 未知数据库等异常不包装，由Stripe专属兜底返回500；运行时异常继续触发整体回滚。
     */
    try {
      processWebhook(payload, signature);
    } catch (HttpBusinessException exception) {
      throw exception;
    } catch (BusinessException exception) {
      Integer code = exception.getCode();
      throw new HttpBusinessException(code != null && code >= 400 && code <= 599 ? code : 500,
          exception.getMsg(), exception.getData());
    }
  }

  /**
   * 在调用方事务内验签、解析、记录事件并更新财务状态；该内部步骤不能绕过公共入口独立调用。
   * 重复事件正常结束，已知协议错误主动抛出HTTP异常，资金与事件审计在失败时一起回滚。
   */
  private void processWebhook(String payload, String signature) {
    validateWebhookConfiguration();
    final Event event;
    try {
      event = Webhook.constructEvent(payload, signature, properties.getWebhookSecret());
    } catch (SignatureVerificationException | IllegalArgumentException exception) {
      throw new HttpBusinessException(400, "Stripe Webhook 签名无效");
    }

    boolean checkoutSessionEvent = event.getType().startsWith("checkout.session.");
    boolean refundEvent = java.util.Set.of("refund.created", "refund.updated", "refund.failed").contains(event.getType());
    Session session = null;
    Refund refund = null;
    if (checkoutSessionEvent || refundEvent) {
      Object object = event.getDataObjectDeserializer().getObject().orElse(null);
      if (object instanceof Session) session = (Session) object;
      if (object instanceof Refund) refund = (Refund) object;
    }
    if (checkoutSessionEvent && session == null)
      throw new HttpBusinessException(500, "Stripe Checkout Session 事件解析失败");
    if (refundEvent && refund == null)
      throw new HttpBusinessException(500, "Stripe Refund 事件解析失败");
    String sessionId = session == null ? null : session.getId();
    if (mapper.insertWebhookEvent(event.getId(), event.getType(), sessionId, payload) == 0) return;
    /*
     * 退款数据对象不是 Session，必须在原 session==null 的结束分支之前处理。
     * 原始事件幂等只去重 evt_，财务服务再以 Refund ID 保证跨事件不重复扣款。
     */
    if (refundEvent) { reconcileRefund(refund); return; }
    if (session == null) return;

    switch (event.getType()) {
      case "checkout.session.completed":
      case "checkout.session.async_payment_succeeded":
        if ("paid".equals(session.getPaymentStatus())) completePaidSession(session);
        else if ("checkout.session.completed".equals(event.getType()))
          mapper.markPaymentProcessing(session.getId());
        break;
      case "checkout.session.async_payment_failed":
        mapper.markPaymentFailed(session.getId(), "Stripe 异步支付失败");
        break;
      case "checkout.session.expired":
        mapper.markSessionExpired(session.getId(), "Stripe 结账会话已过期");
        break;
      default:
        break;
    }
  }

  /**
   * 将外部退款路由至原充值的企业钱包，不要求用户登录，不信任 metadata 中的企业信息。
   * 退款可能早于支付通知到达：先从 Stripe 找回本地绑定 Session 并补偿原充值入账；
   * 共享 Stripe 账号中不属于本系统的付款只保留事件审计，不扣除任何企业余额。
   * 原充值锁内重新查询退款快照，旧事件重放也不会把退款失败状态错误改回成功。
   *
   * @param notification 验签后的退款事件对象，仅用于定位退款和原付款
   */
  private void reconcileRefund(Refund notification) {
    String paymentIntentId = notification.getPaymentIntent();
    if (blank(paymentIntentId) && !blank(notification.getCharge()))
      paymentIntentId = checkoutClient.findPaymentIntentByCharge(notification.getCharge());
    if (blank(paymentIntentId)) return;
    Map<String, Object> order = PortalMaps.camel(mapper.findRechargeOrderByPaymentIntent(paymentIntentId));
    Session recovered = null;
    if (order == null) {
      recovered = checkoutClient.findSessionByPaymentIntent(paymentIntentId);
      if (recovered == null) return;
      order = PortalMaps.camel(mapper.findRechargeOrderBySession(recovered.getId()));
      if (order == null) return;
    }
    Long orderId = ((Number) order.get("id")).longValue();
    Long enterpriseId = ((Number) order.get("enterpriseId")).longValue();
    order = PortalMaps.camel(mapper.lockRechargeOrder(orderId, enterpriseId));
    if (order == null) throw new BusinessException(409, "Stripe 退款原充值订单已变化");
    if (!RechargeOrderStatus.isPaid(((Number) order.get("status")).intValue())) {
      if (recovered == null) recovered = checkoutClient.retrieve(String.valueOf(order.get("stripeCheckoutSessionId")));
      if (!"complete".equals(recovered.getStatus()) || !"paid".equals(recovered.getPaymentStatus())
          || !paymentIntentId.equals(recovered.getPaymentIntent()))
        throw new BusinessException(409, "Stripe 退款原付款尚未确认，请重试");
      completePaidSession(recovered);
    }
    Refund latest = checkoutClient.retrieveRefund(notification.getId());
    if (!notification.getId().equals(latest.getId()) || !paymentIntentId.equals(latest.getPaymentIntent())
        || latest.getAmount() == null)
      throw new BusinessException(409, "Stripe 退款原付款关联不一致");
    financeService.reconcileStripeRefund(latest.getId(), paymentIntentId, latest.getCurrency(),
        latest.getAmount(), latest.getStatus());
  }

  /**
   * 定时补偿一笔已到本地 Session 截止时间的待支付订单。
   * 以 Stripe 实时状态为准：expired 才标记过期；complete+unpaid 标记处理中；
   * complete+paid 补偿入账；仍为 open 时主动 expire，避免只依赖可能丢失的 Webhook。
   */
  public void reconcileExpiredSession(String sessionId) {
    Session session = checkoutClient.retrieve(sessionId);
    if ("open".equals(session.getStatus())) session = checkoutClient.expireIfOpen(sessionId);
    if ("expired".equals(session.getStatus())) {
      mapper.markSessionExpired(sessionId, "Stripe 结账会话已过期");
      return;
    }
    if ("complete".equals(session.getStatus())) {
      if ("paid".equals(session.getPaymentStatus())) completePaidSession(session);
      else mapper.markPaymentProcessing(sessionId);
    }
  }

  /**
   * 对 Stripe 成功事件执行服务端金额校验并触发财务入账。
   * 校验使用本地已绑定 Session 的订单，不信任前端金额或仅凭 metadata 入账。
   */
  private void completePaidSession(Session session) {
    Map<String, Object> order = PortalMaps.camel(mapper.findRechargeOrderBySession(session.getId()));
    if (order == null) throw new BusinessException(404, "Stripe Session 未绑定充值订单");
    long expectedAmount = money(order.get("amount")).movePointRight(2).longValueExact();
    if (session.getAmountTotal() == null || session.getAmountTotal() != expectedAmount)
      throw new BusinessException(409, "Stripe 支付金额与充值订单不一致");
    if (session.getCurrency() == null
        || !properties.getCurrency().equalsIgnoreCase(session.getCurrency()))
      throw new BusinessException(409, "Stripe 支付币种与充值订单不一致");
    financeService.completeStripeRecharge(session.getId(), session.getPaymentIntent());
  }

  /** 仅允许仍 open 且 unpaid 的既有 Session 再次交给前端挂载。 */
  private void validateReusableSession(Session session) {
    if ("expired".equals(session.getStatus())) {
      mapper.markSessionExpired(session.getId(), "Stripe 结账会话已过期");
      throw new BusinessException(409, "Stripe 结账会话已过期，请重新创建充值订单");
    }
    if ("complete".equals(session.getStatus())) {
      if ("paid".equals(session.getPaymentStatus())) completePaidSession(session);
      else mapper.markPaymentProcessing(session.getId());
      throw new BusinessException(409, "Stripe 结账已完成，正在确认到账结果");
    }
    if (!"open".equals(session.getStatus()) || !"unpaid".equals(session.getPaymentStatus()))
      throw new BusinessException(409, "当前 Stripe 结账会话状态不可用");
  }

  private void validateCheckoutConfiguration() {
    if (!properties.isEnabled()) throw new BusinessException(503, "Stripe 支付尚未启用");
    if (blank(properties.getSecretKey()) || blank(properties.getPublishableKey()) || blank(properties.getReturnUrl()))
      throw new BusinessException(503, "Stripe 支付配置不完整");
  }

  private void validateWebhookConfiguration() {
    if (!properties.isEnabled() || blank(properties.getWebhookSecret()))
      throw new HttpBusinessException(503, "Stripe Webhook 尚未配置");
  }

  private void validateAmount(BigDecimal amount) {
    if (amount.compareTo(properties.getMinimumAmount()) < 0
        || amount.compareTo(properties.getMaximumAmount()) > 0)
      throw new BusinessException(400, "充值金额超出 Stripe 允许范围");
  }

  private BigDecimal money(Object value) {
    return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.UNNECESSARY);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
