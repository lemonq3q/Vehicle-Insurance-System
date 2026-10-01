package com.example.insurancesystem.saas.service.impl;

import com.example.insurancesystem.domain.encapsulate.TableData;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.saas.config.WorkorderOverageBillingProperties;
import com.example.insurancesystem.saas.mapper.EnterpriseMapper;
import com.example.insurancesystem.saas.mapper.FinanceMapper;
import com.example.insurancesystem.saas.mapper.WorkorderOverageBillingMapper;
import com.example.insurancesystem.saas.payment.StripePaymentProperties;
import com.example.insurancesystem.saas.service.FinanceService;
import com.example.insurancesystem.saas.service.MemberSeatService;
import com.example.insurancesystem.saas.service.WalletBalanceService;
import com.example.insurancesystem.saas.service.WalletBalanceService.BalanceChangeResult;
import com.example.insurancesystem.saas.support.*;
import com.example.insurancesystem.utils.UniqueCodeRetryUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceServiceImpl implements FinanceService {
  private final FinanceMapper mapper;
  private final EnterpriseMapper enterprises;
  private final PortalContextService context;
  private final BusinessCodeGenerator codes;
  private final ObjectMapper objectMapper;
  private final MemberSeatService seats;
  private final WorkorderOverageBillingMapper workorderBillingMapper;
  private final WorkorderOverageBillingProperties workorderBillingProperties;
  private final WalletBalanceService balances;
  private final StripePaymentProperties stripePaymentProperties;
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

  public FinanceServiceImpl(
      FinanceMapper mapper,
      EnterpriseMapper enterprises,
      PortalContextService context,
      BusinessCodeGenerator codes,
      ObjectMapper objectMapper,
      MemberSeatService seats,
      WorkorderOverageBillingMapper workorderBillingMapper,
      WorkorderOverageBillingProperties workorderBillingProperties,
      WalletBalanceService balances,
      StripePaymentProperties stripePaymentProperties) {
    this.mapper = mapper;
    this.enterprises = enterprises;
    this.context = context;
    this.codes = codes;
    this.objectMapper = objectMapper;
    this.seats = seats;
    this.workorderBillingMapper = workorderBillingMapper;
    this.workorderBillingProperties = workorderBillingProperties;
    this.balances = balances;
    this.stripePaymentProperties = stripePaymentProperties;
  }

  /**
   * 返回当前企业的财务概览以及服务器实际生效的充值金额边界。
   *
   * <p>充值边界来自部署环境中的 Stripe 支付配置，前端使用该值进行即时校验和提示；创建订单时仍会在
   * 服务端重复校验，避免调用方绕过页面限制。返回配置不包含任何 Stripe 密钥或其他敏感信息。
   *
   * @return 钱包、订阅、成员数量以及充值金额上下限
   */
  public Map<String, Object> overview() {
    Long enterpriseId = context.enterpriseId();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("wallet", walletView(enterpriseId));
    data.put("subscription", subscriptionView(enterpriseId));
    data.put("currentMemberCount", enterprises.countActiveMembers(enterpriseId));
    Map<String, Object> rechargeLimits = new LinkedHashMap<>();
    rechargeLimits.put("minimumAmount", stripePaymentProperties.getMinimumAmount());
    rechargeLimits.put("maximumAmount", stripePaymentProperties.getMaximumAmount());
    rechargeLimits.put("currency", stripePaymentProperties.getCurrency());
    data.put("rechargeLimits", rechargeLimits);
    return data;
  }

  public List<Map<String, Object>> plans() {
    return PortalMaps.camel(mapper.findPlans());
  }

  /**
   * 创建一笔待支付充值订单。
   *
   * <p>金额来自门户充值表单，必须精确到分且位于服务器配置的充值区间内。校验在任何数据库写入之前
   * 完成，因此超限请求不会生成无法支付的本地订单；该服务端规则独立于前端和 Stripe Session 校验。
   *
   * @param body 请求体，amount 为用户提交的充值金额
   * @return 新建的待支付充值订单
   * @throws BusinessException 金额格式错误、精度超过两位或超出配置范围时抛出
   */
  @Transactional
  public Map<String, Object> createRecharge(Map<String, Object> body) {
    Long enterpriseId =
        ((Number) context.requireRoles("OWNER", "ADMIN").get("enterpriseId")).longValue();
    final BigDecimal amount;
    try {
      amount = decimal(body, "amount").setScale(2, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException exception) {
      throw new BusinessException(400, "充值金额最多保留两位小数");
    }
    validateRechargeAmount(amount);
    ensureWallet(enterpriseId, context.userId());
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("enterpriseId", enterpriseId);
    order.put("userId", context.userId());
    order.put("amount", amount);
    order.put("refundAmount", BigDecimal.ZERO);
    order.put("payChannel", "STRIPE");
    order.put("status", 1);
    order.put("createdAt", LocalDateTime.now());
    UniqueCodeRetryUtil.insertWithGeneratedCode(
        SaasCodeConstraints.RECHARGE_NO,
        codes::rechargeNo,
        rechargeNo -> order.put("rechargeNo", rechargeNo),
        () -> mapper.insertRecharge(order));
    return PortalMaps.camel(order);
  }

  /**
   * 使用部署环境中实际生效的上下限校验本地充值订单金额。
   *
   * @param amount 已规范为两位小数的充值金额
   * @throws BusinessException 金额小于最小值或大于单笔最大值时抛出
   */
  private void validateRechargeAmount(BigDecimal amount) {
    BigDecimal minimumAmount = stripePaymentProperties.getMinimumAmount();
    BigDecimal maximumAmount = stripePaymentProperties.getMaximumAmount();
    if (amount.compareTo(minimumAmount) < 0)
      throw new BusinessException(400, "单笔充值金额不能低于 " + minimumAmount.toPlainString() + " 元");
    if (amount.compareTo(maximumAmount) > 0)
      throw new BusinessException(400, "单笔充值金额不能超过 " + maximumAmount.toPlainString() + " 元");
  }

  public Map<String, Object> rechargeDetail(Long id) {
    context.requireRoles("OWNER", "ADMIN");
    Map<String, Object> order = PortalMaps.camel(mapper.findRechargeOrder(id, context.enterpriseId()));
    if (order == null) throw new BusinessException(404, "充值订单不存在");
    return order;
  }

  @Transactional
  public Map<String, Object> completeStripeRecharge(
      String checkoutSessionId, String paymentIntentId) {
    Map<String, Object> order =
        PortalMaps.camel(mapper.lockRechargeOrderByStripeSession(checkoutSessionId));
    if (order == null) throw new BusinessException(404, "Stripe 充值订单不存在");

    Long orderId = ((Number) order.get("id")).longValue();
    Long enterpriseId = ((Number) order.get("enterpriseId")).longValue();
    Long userId = ((Number) order.get("userId")).longValue();

    int status = ((Number) order.get("status")).intValue();
    /* 退款状态仍代表原付款已经入账，迟到的付款通知不得重复增加余额。 */
    if (RechargeOrderStatus.isPaid(status)) return order;
    if (status != 1 && status != 7)
      throw new BusinessException(409, "当前充值订单状态不可支付");

    ensureWallet(enterpriseId, userId);
    BigDecimal amount = money(order.get("amount"));
    /*
     * 外部退款可能已使钱包为负；后续小额充值仍应正常冲抵欠款，不能要求一次补足所有欠款。
     * 本方法只增加已验证的正金额，允许充值后仍为负不等于允许用户主动透支购买套餐。
     */
    BalanceChangeResult balanceChange =
        balances.changeBalance(enterpriseId, amount, userId, true);
    if (mapper.completeStripeRechargeOrder(orderId, enterpriseId, paymentIntentId) == 0)
      throw new BusinessException(409, "充值订单状态已变化，请刷新后重试");

    Map<String, Object> transaction = new LinkedHashMap<>();
    transaction.put("enterpriseId", enterpriseId);
    transaction.put("walletId", balanceChange.walletId());
    transaction.put("userId", userId);
    transaction.put("direction", "IN");
    transaction.put("transactionType", "RECHARGE");
    transaction.put("amount", amount);
    transaction.put("balanceBefore", balanceChange.balanceBefore());
    transaction.put("balanceAfter", balanceChange.balanceAfter());
    transaction.put("rechargeOrderId", orderId);
    transaction.put("remark", "余额充值 " + order.get("rechargeNo"));
    UniqueCodeRetryUtil.insertWithGeneratedCode(
        SaasCodeConstraints.WALLET_TRANSACTION_NO,
        codes::transactionNo,
        transactionNo -> transaction.put("transactionNo", transactionNo),
        () -> mapper.insertTransaction(transaction));

    order.put("status", 2);
    order.put("paidAt", LocalDateTime.now());
    order.put("balanceAmount", balanceChange.balanceAfter());
    order.put("transactionNo", transaction.get("transactionNo"));
    return order;
  }

  /**
   * 外部退款的财务记账入口，不依赖浏览器登录或用户主动申请退款。
   * 原充值订单锁串行化多笔退款，Refund 已回撤金额用于计算本次差额；钱包、流水和
   * 退款记录及订单累计退款摘要同一事务提交。退款已经实际发生时允许形成负余额，复用钱包的欠费暂停规则。
   * 订单根据当前累计回撤金额更新为已支付、已部分退款或已完全退款；退款失败补回时同步恢复摘要。
   *
   * @param refundId Stripe退款唯一标识
   * @param paymentIntentId 原充值付款标识
   * @param currency 经 Stripe 查询确认的币种
   * @param minorAmount 单笔退款的最小货币单位金额
   * @param status Stripe最新退款状态，非客户端状态
   */
  @Transactional
  public void reconcileStripeRefund(String refundId, String paymentIntentId, String currency,
      long minorAmount, String status) {
    if (refundId == null || refundId.isBlank() || paymentIntentId == null || paymentIntentId.isBlank()
        || minorAmount <= 0 || status == null || !Set.of("pending", "requires_action", "succeeded", "failed", "canceled").contains(status))
      throw new BusinessException(400, "Stripe 退款数据无效");
    if (currency == null || !stripePaymentProperties.getCurrency().equalsIgnoreCase(currency))
      throw new BusinessException(409, "Stripe 退款币种与充值币种不一致");
    Map<String, Object> order = PortalMaps.camel(mapper.lockRechargeOrderByPaymentIntent(paymentIntentId));
    if (order == null || !RechargeOrderStatus.isPaid(intValue(order.get("status"))))
      throw new BusinessException(409, "Stripe 原充值尚未入账，请重试退款通知");
    Long orderId = ((Number) order.get("id")).longValue();
    Long enterpriseId = ((Number) order.get("enterpriseId")).longValue();
    BigDecimal amount = BigDecimal.valueOf(minorAmount, 2);
    if (amount.compareTo(money(order.get("amount"))) > 0)
      throw new BusinessException(409, "Stripe 退款金额超过原充值金额");
    String stateKey = "saas-refund-state:" + refundId;
    String stored = mapper.lockStripeRefundState(stateKey);
    Map<String, Object> existing = stored == null ? null : parseRefundState(stored);
    BigDecimal applied = existing == null ? BigDecimal.ZERO : money(existing.get("appliedAmount"));
    /*
     * 同一 Refund 的金额、原支付和订单不可变；避免错误关联导致扣错企业钱包。
     * 不按 event.id 判断财务是否完成，因为 created、updated 等不同事件可能对应同一退款。
     */
    if (existing != null && (!paymentIntentId.equals(existing.get("paymentIntentId"))
        || ((Number) existing.get("rechargeOrderId")).longValue() != orderId.longValue()
        || money(existing.get("amount")).compareTo(amount) != 0
        || !currency.equalsIgnoreCase(String.valueOf(existing.get("currency")))))
      throw new BusinessException(409, "Stripe 退款关联数据发生变化");
    BigDecimal target = "succeeded".equals(status) ? amount : BigDecimal.ZERO;
    BigDecimal delta = applied.subtract(target);
    /* 汇总所有 Refund 的实际回撤金额，而非单条 webhook 金额，支持多次部分退款及补回。 */
    BigDecimal total = BigDecimal.ZERO;
    for (String state : mapper.findStripeRefundStates(String.valueOf(order.get("stripeCheckoutSessionId"))))
      total = total.add(money(parseRefundState(state).get("appliedAmount")));
    total = total.subtract(applied).add(target);
    if (total.signum() < 0 || total.compareTo(money(order.get("amount"))) > 0)
      throw new BusinessException(409, "Stripe 累计退款超过原充值金额");
    int orderStatus = total.signum() == 0 ? RechargeOrderStatus.PAID
        : total.compareTo(money(order.get("amount"))) == 0 ? RechargeOrderStatus.FULLY_REFUNDED
        : RechargeOrderStatus.PARTIALLY_REFUNDED;
    boolean summaryChanged = intValue(order.get("status")) != orderStatus
        || money(order.get("refundAmount")).compareTo(total) != 0;
    if (existing != null && delta.signum() == 0 && status.equals(existing.get("status"))
        && !summaryChanged) return;
    Map<String, Object> refund = new LinkedHashMap<>();
    refund.put("refundId", refundId);
    refund.put("rechargeOrderId", orderId);
    refund.put("paymentIntentId", paymentIntentId);
    refund.put("currency", currency.toLowerCase(Locale.ROOT));
    refund.put("amount", amount);
    refund.put("status", status);
    refund.put("appliedAmount", target);
    /*
     * 仅差额非零时修改余额：退款成功扣除，后续失败或撤回则补回。
     * 系统事件操作人为空，不把 Stripe 后台退款伪装为原充值用户操作；流水仍关联原充值单。
     */
    if (delta.signum() != 0) {
      BalanceChangeResult change = balances.changeBalance(enterpriseId, delta, null, true);
      Map<String, Object> transaction = new LinkedHashMap<>();
      transaction.put("enterpriseId", enterpriseId);
      transaction.put("walletId", change.walletId());
      transaction.put("userId", null);
      transaction.put("direction", delta.signum() < 0 ? "OUT" : "IN");
      transaction.put("transactionType", "REFUND");
      transaction.put("amount", delta.abs());
      transaction.put("balanceBefore", change.balanceBefore());
      transaction.put("balanceAfter", change.balanceAfter());
      transaction.put("rechargeOrderId", orderId);
      transaction.put("remark", (delta.signum() < 0 ? "Stripe退款余额回撤 " : "Stripe退款撤回余额恢复 ") + refundId);
      UniqueCodeRetryUtil.insertWithGeneratedCode(
          SaasCodeConstraints.WALLET_TRANSACTION_NO, codes::transactionNo,
          transactionNo -> transaction.put("transactionNo", transactionNo),
          () -> mapper.insertTransaction(transaction));
    }
    int changed = existing == null
        ? mapper.insertStripeRefundState(stateKey, String.valueOf(order.get("stripeCheckoutSessionId")), json(refund))
        : mapper.updateStripeRefundState(stateKey, json(refund));
    if (changed != 1)
      throw new BusinessException(409, "Stripe 退款记账状态更新失败");
    /* 订单摘要与钱包、退款幂等记录同一事务提交；摘要修复本身不会再次扣款。 */
    if (summaryChanged && mapper.updateRechargeRefundSummary(orderId, enterpriseId, total, orderStatus) != 1)
      throw new BusinessException(409, "Stripe 充值退款摘要更新失败");
  }

  /**
   * 解析服务器写入审计表的退款记账状态，不读取客户端 metadata。
   * 损坏的数据必须中止事务并重试，不能当作首次退款再次扣款。
   *
   * @param payload 本地 saas.refund.state 的 JSON
   * @return 原付款、退款额、已回撤金额及状态快照
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> parseRefundState(String payload) {
    try {
      Map<String, Object> state = objectMapper.readValue(payload, Map.class);
      /*
       * 公共 Jackson 配置可能把 Long 订单 ID 写成字符串，历史记录也可能保存数字。
       * 在读取边界统一为 Long，精确转换防止小数或溢出 ID 被截断后关联到错误订单；
       * 无效快照中止事务，保留通知重试机会，不能误当首次退款重复扣款。
       */
      long orderId = new BigDecimal(String.valueOf(state.get("rechargeOrderId"))).longValueExact();
      if (orderId <= 0) throw new IllegalArgumentException("Invalid recharge order ID");
      state.put("rechargeOrderId", orderId);
      return state;
    } catch (JsonProcessingException | IllegalArgumentException | ArithmeticException exception) {
      throw new BusinessException(500, "Stripe 退款记账记录损坏");
    }
  }

  public TableData<Map<String, Object>> recharges(
      int pageNum,
      int pageSize,
      String rechargeNo,
      Integer status,
      String startTime,
      String endTime) {
    Map<String, Object> q = query(pageNum, pageSize);
    q.put("enterpriseId", context.enterpriseId());
    q.put("rechargeNo", rechargeNo);
    q.put("status", status);
    putTimeRange(q, startTime, endTime);
    return new TableData<>(mapper.countRecharges(q), PortalMaps.camel(mapper.findRecharges(q)));
  }

  public Map<String, Object> preview(Map<String, Object> body) {
    context.requireRoles("OWNER", "ADMIN");
    Map<String, Object> preview =
        calculate(context.enterpriseId(), number(body, "planId"), integer(body, "periodCount"), null);
    preview.remove("workorderOverageIds");
    return preview;
  }

  @Transactional
  public Map<String, Object> subscribe(Map<String, Object> body) {
    context.requireRoles("OWNER", "ADMIN");
    Long enterpriseId = context.enterpriseId();
    Long planId = number(body, "planId");
    int periods = integer(body, "periodCount");
    boolean autoRenew = Boolean.parseBoolean(String.valueOf(body.getOrDefault("autoRenew", false)));
    Map<String, Object> currentState = PortalMaps.camel(mapper.lockSubscription(enterpriseId));
    if (currentState == null) throw new BusinessException(500, "企业订阅状态不存在");
    if (intValue(currentState.get("status")) == 3
        && !"ARREARS".equals(currentState.get("suspendReason")))
      throw new BusinessException(409, "当前套餐不是欠费暂停状态，请联系平台处理后再变更套餐");
    Map<String, Object> preview = calculate(enterpriseId, planId, periods, currentState);
    if (!(Boolean) preview.get("eligible")) {
      String message = String.valueOf(preview.get("validationMessage"));
      int code = message.contains("周期") ? 422 : 422;
      throw new BusinessException(code, message, preview);
    }
    BigDecimal payable = money(preview.get("payableAmount"));
    BigDecimal refund = money(preview.get("refundAmount"));
    Map<String, Object> plan = (Map<String, Object>) preview.get("plan"),
        activeSubscription = activeSubscription(currentState);
    Map<String, Object> order = new LinkedHashMap<>();
    order.putAll(preview);
    order.put("enterpriseId", enterpriseId);
    order.put("userId", context.userId());
    order.put("planId", planId);
    order.put("userLimit", plan.get("userLimit"));
    order.put("workorderLimit", intValue(plan.get("workorderLimit")));
    order.put("durationDays", ((Number) plan.get("durationDays")).intValue() * periods);
    order.put("subscriptionId", currentState.get("id"));
    order.put("oldPlanId", activeSubscription == null ? null : activeSubscription.get("planId"));
    order.put("autoRenew", autoRenew ? 1 : 0);
    order.put("planSnapshotJson", json(plan));
    UniqueCodeRetryUtil.insertWithGeneratedCode(
        SaasCodeConstraints.SUBSCRIPTION_ORDER_NO,
        codes::subscriptionOrderNo,
        orderNo -> order.put("orderNo", orderNo),
        () -> mapper.insertOrder(order));
    Map<String, Object> subscription = new LinkedHashMap<>();
    subscription.put("enterpriseId", enterpriseId);
    subscription.put("planId", planId);
    subscription.put("orderId", order.get("id"));
    subscription.put("userLimit", plan.get("userLimit"));
    subscription.put("workorderLimit", intValue(plan.get("workorderLimit")));
    subscription.put("ocrQuota", intValue(plan.get("ocrQuota")));
    subscription.put("requestQuota", intValue(plan.get("requestQuota")));
    subscription.put(
        "startAt",
        activeSubscription != null && "RENEW".equals(preview.get("orderType"))
            ? activeSubscription.get("startAt")
            : preview.get("startAt"));
    subscription.put("endAt", preview.get("endAt"));
    subscription.put("autoRenew", autoRenew ? 1 : 0);
    subscription.put("nextRenewAt", autoRenew ? preview.get("endAt") : null);
    if (mapper.updateSubscription(subscription) == 0)
      throw new BusinessException(409, "企业订阅状态已变化，请重试");

    // 套餐订单完成订阅快照更新后再通过统一余额入口结算，使恢复或暂停判断基于新套餐有效期执行。
    BalanceChangeResult balanceChange;
    try {
      balanceChange =
          balances.changeBalance(
              enterpriseId, refund.subtract(payable), context.userId(), false);
    } catch (IllegalStateException exception) {
      if ("企业余额不足".equals(exception.getMessage()))
        throw new BusinessException(409, "企业余额不足，请先充值", preview);
      throw exception;
    }

    // 套餐降额产生的超额费用已经包含在本次订单中，同一事务写入业务扣费日，避免次日维护再次收费。
    @SuppressWarnings("unchecked")
    List<Long> overageIds = (List<Long>) preview.getOrDefault("workorderOverageIds", List.of());
    if (!overageIds.isEmpty()) {
      LocalDate billingDate = LocalDate.now(BUSINESS_ZONE);
      int marked =
          workorderBillingMapper.markBilled(
              enterpriseId,
              overageIds,
              billingDate,
              workorderBillingProperties.getCycleDays());
      if (marked != overageIds.size())
        throw new BusinessException(409, "工单计费状态已变化，请重新确认套餐变更金额");
    }
    seats.synchronize(enterpriseId, ((Number) plan.get("userLimit")).intValue());
    Long subscriptionId = ((Number) currentState.get("id")).longValue();
    BigDecimal change = refund.signum() > 0 ? refund : payable;
    Map<String, Object> tx = new LinkedHashMap<>();
    tx.put("enterpriseId", enterpriseId);
    tx.put("walletId", balanceChange.walletId());
    tx.put("userId", context.userId());
    tx.put("direction", refund.signum() > 0 ? "IN" : "OUT");
    tx.put("transactionType", transactionType(String.valueOf(preview.get("orderType")), refund));
    tx.put("amount", change);
    tx.put("balanceBefore", balanceChange.balanceBefore());
    tx.put("balanceAfter", balanceChange.balanceAfter());
    tx.put("orderId", order.get("id"));
    tx.put("subscriptionId", subscriptionId);
    BigDecimal workorderOverageAmount = money(preview.get("workorderOverageAmount"));
    int workorderOverageCount = intValue(preview.get("workorderOverageCount"));
    tx.put(
        "remark",
        workorderOverageAmount.signum() > 0
            ? "套餐订单 "
                + order.get("orderNo")
                + "（含超额工单费 ¥"
                + workorderOverageAmount
                + "，"
                + workorderOverageCount
                + " 单）"
            : "套餐订单 " + order.get("orderNo"));
    UniqueCodeRetryUtil.insertWithGeneratedCode(
        SaasCodeConstraints.WALLET_TRANSACTION_NO,
        codes::transactionNo,
        transactionNo -> tx.put("transactionNo", transactionNo),
        () -> mapper.insertTransaction(tx));
    mapper.bindOrderTransaction(
        ((Number) order.get("id")).longValue(), ((Number) tx.get("id")).longValue());
    order.put("paidAmount", payable);
    order.put("payType", "BALANCE");
    order.put("status", 2);
    order.remove("workorderOverageIds");
    return order;
  }

  public Map<String, Object> updateAutoRenew(Map<String, Object> body) {
    context.requireRoles("OWNER", "ADMIN");
    Map<String, Object> subscription =
        PortalMaps.camel(mapper.findSubscription(context.enterpriseId()));
    if (activeSubscription(subscription) == null)
      throw new BusinessException(400, "当前没有生效中的订阅");
    boolean enabled = Boolean.parseBoolean(String.valueOf(body.get("autoRenewEnabled")));
    mapper.updateAutoRenew(((Number) subscription.get("id")).longValue(), enabled ? 1 : 0);
    return subscriptionView(context.enterpriseId());
  }

  public TableData<Map<String, Object>> orders(
      int pageNum,
      int pageSize,
      String orderNo,
      String orderType,
      String startTime,
      String endTime) {
    Map<String, Object> q = query(pageNum, pageSize);
    q.put("enterpriseId", context.enterpriseId());
    q.put("orderNo", orderNo);
    q.put("orderType", orderType);
    putTimeRange(q, startTime, endTime);
    List<Map<String, Object>> rows = PortalMaps.camel(mapper.findOrders(q));
    rows.forEach(this::decorateOrder);
    return new TableData<>(mapper.countOrders(q), rows);
  }

  /**
   * 按当前企业边界读取历史订阅订单，并复用列表的快照解析逻辑补齐周期数和套餐权益。
   * 查询不到时统一返回业务 404，既避免泄露其他企业订单，也让前端能够展示明确的空状态。
   *
   * @param id 由订阅订单列表进入详情页时携带的订单主键
   * @return 已转换为小驼峰字段并解析套餐快照的订阅订单
   * @throws BusinessException 订单不存在、已删除或不属于当前企业时抛出 404
   */
  public Map<String, Object> orderDetail(Long id) {
    Map<String, Object> order = PortalMaps.camel(mapper.findOrder(id, context.enterpriseId()));
    if (order == null) throw new BusinessException(404, "订阅订单不存在");
    decorateOrder(order);
    return order;
  }

  public TableData<Map<String, Object>> transactions(
      int pageNum,
      int pageSize,
      String transactionNo,
      String direction,
      String type,
      String startTime,
      String endTime) {
    Map<String, Object> q = query(pageNum, pageSize);
    q.put("enterpriseId", context.enterpriseId());
    q.put("transactionNo", transactionNo);
    q.put("direction", direction);
    q.put("transactionType", type);
    putTimeRange(q, startTime, endTime);
    return new TableData<>(
        mapper.countTransactions(q), PortalMaps.camel(mapper.findTransactions(q)));
  }

  private Map<String, Object> calculate(
      Long enterpriseId, Long planId, int periods, Map<String, Object> lockedState) {
    if (periods < 1) throw new BusinessException(400, "订阅周期必须为正整数");
    Map<String, Object> plan = PortalMaps.camel(mapper.findPlan(planId));
    if (plan == null) throw new BusinessException(404, "套餐不存在");
    Map<String, Object> state =
            lockedState == null
                ? PortalMaps.camel(mapper.findSubscription(enterpriseId))
                : lockedState,
        sub = activeSubscription(state),
        currentPlan =
            sub == null
                ? null
                : PortalMaps.camel(mapper.findPlan(((Number) sub.get("planId")).longValue()));
    LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE), start = now, end;
    String type;
    double remainingDays = 0, remainingPeriods = 0;
    int minimum = 1;
    BigDecimal credit = BigDecimal.ZERO;
    int duration = ((Number) plan.get("durationDays")).intValue();
    BigDecimal price = money(plan.get("price"));
    if (sub == null) {
      type = "BUY";
      end = now.plusDays((long) duration * periods);
    } else {
      LocalDateTime oldEnd = (LocalDateTime) sub.get("endAt");
      /*
       * 改订按上海业务自然日计算旧套餐剩余价值，不随当日付款耗时减少抵扣。
       * 到期日期减去当前日期得到整数剩余天数；到期当天剩余计价天数为零。
       * 预览与实际订阅共用此计算入口，跨日时重新计价，生效资格仍按实际到期时间校验。
       */
      remainingDays = Math.max(0, ChronoUnit.DAYS.between(now.toLocalDate(), oldEnd.toLocalDate()));
      if (((Number) sub.get("planId")).longValue() == planId) {
        type = "RENEW";
        start = oldEnd.isAfter(now) ? oldEnd : now;
        end = start.plusDays((long) duration * periods);
      } else {
        type = "CHANGE_PLAN";
        minimum = Math.max(1, (int) Math.ceil(remainingDays / duration));
        end = now.plusDays((long) duration * periods);
        if (currentPlan != null) {
          int oldDuration = ((Number) currentPlan.get("durationDays")).intValue();
          remainingPeriods = remainingDays / oldDuration;
          credit =
              money(currentPlan.get("price"))
                  .multiply(BigDecimal.valueOf((long) remainingDays))
                  .divide(BigDecimal.valueOf(oldDuration), 2, RoundingMode.HALF_UP);
        }
      }
    }
    // 只有套餐变更到更低工单额度时才立即计费；最近一次扣费仍覆盖当前周期的工单会被排除，避免周期内重复收费。
    int oldWorkorderLimit = sub == null ? 0 : intValue(sub.get("workorderLimit"));
    int newWorkorderLimit = Math.max(0, intValue(plan.get("workorderLimit")));
    LocalDate billingDate = LocalDate.now(BUSINESS_ZONE);
    List<Long> overageIds =
        "CHANGE_PLAN".equals(type) && newWorkorderLimit < oldWorkorderLimit
            ? workorderBillingMapper.findCurrentExcessWorkorderIds(
                enterpriseId,
                newWorkorderLimit,
                billingDate,
                workorderBillingProperties.getCycleDays())
            : List.of();
    BigDecimal workorderOverageAmount =
        workorderBillingProperties
            .getUnitPrice()
            .multiply(BigDecimal.valueOf(overageIds.size()))
            .setScale(2, RoundingMode.HALF_UP);

    // 套餐价减去旧套餐剩余价值后，再加上降额产生的工单费，最终净额决定扣款或退款方向。
    BigDecimal
        priceAmount = price.multiply(BigDecimal.valueOf(periods)).setScale(2, RoundingMode.HALF_UP),
        diff = priceAmount.subtract(credit).add(workorderOverageAmount),
        payable = diff.max(BigDecimal.ZERO),
        refund = diff.min(BigDecimal.ZERO).abs();
    Map<String, Object> wallet = PortalMaps.camel(mapper.findWallet(enterpriseId));
    BigDecimal balance = wallet == null ? BigDecimal.ZERO : money(wallet.get("balanceAmount"));
    int members = enterprises.countActiveMembers(enterpriseId),
        limit = ((Number) plan.get("userLimit")).intValue();
    boolean eligible = periods >= minimum;
    String message = periods < minimum ? "改订周期不能少于 " + minimum + " 个周期" : "";
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("plan", plan);
    result.put("currentPlan", currentPlan);
    result.put("orderType", type);
    result.put("periodCount", periods);
    result.put("minimumPeriodCount", minimum);
    result.put("remainingDays", round(remainingDays));
    result.put("remainingPeriodCount", round(remainingPeriods));
    result.put("priceAmount", priceAmount);
    result.put("creditAmount", credit);
    result.put("workorderOverageCount", overageIds.size());
    result.put("workorderOverageAmount", workorderOverageAmount);
    result.put("workorderOverageUnitPrice", workorderBillingProperties.getUnitPrice());
    result.put("workorderOverageIds", overageIds);
    result.put("payableAmount", payable);
    result.put("refundAmount", refund);
    result.put("balanceAmount", balance);
    result.put("shortfallAmount", payable.subtract(balance).max(BigDecimal.ZERO));
    result.put("startAt", start);
    result.put("endAt", end);
    result.put("memberCount", members);
    result.put("eligible", eligible);
    result.put("validationMessage", message);
    return result;
  }

  private Map<String, Object> subscriptionView(Long enterpriseId) {
    Map<String, Object> sub = PortalMaps.camel(mapper.findSubscription(enterpriseId));
    if (sub != null && sub.get("planId") != null)
      sub.put("plan", PortalMaps.camel(mapper.findPlan(((Number) sub.get("planId")).longValue())));
    return sub;
  }

  private Map<String, Object> activeSubscription(Map<String, Object> subscription) {
    int status = subscription == null ? 0 : intValue(subscription.get("status"));
    if (status != 1 && status != 3) return null;
    if (status == 3 && !"ARREARS".equals(subscription.get("suspendReason"))) return null;
    Object endAt = subscription.get("endAt");
    return endAt instanceof LocalDateTime && ((LocalDateTime) endAt).isAfter(LocalDateTime.now(BUSINESS_ZONE))
        ? subscription
        : null;
  }

  private int intValue(Object value) {
    return value == null ? 0 : ((Number) value).intValue();
  }

  private Map<String, Object> walletView(Long enterpriseId) {
    Map<String, Object> wallet = PortalMaps.camel(mapper.findWallet(enterpriseId));
    if (wallet != null) return wallet;

    Map<String, Object> emptyWallet = new LinkedHashMap<>();
    emptyWallet.put("balanceAmount", BigDecimal.ZERO.setScale(2));
    emptyWallet.put("currency", "CNY");
    return emptyWallet;
  }

  private Map<String, Object> ensureWallet(Long enterpriseId, Long operatorUserId) {
    enterprises.lockEnterprise(enterpriseId);
    Map<String, Object> wallet = PortalMaps.camel(mapper.lockWallet(enterpriseId));
    if (wallet != null) return wallet;

    enterprises.insertWallet(enterpriseId, operatorUserId);
    wallet = PortalMaps.camel(mapper.lockWallet(enterpriseId));
    if (wallet == null) throw new BusinessException(500, "企业钱包初始化失败");
    return wallet;
  }

  private void decorateOrder(Map<String, Object> order) {
    Object raw = order.remove("planSnapshotJson");
    if (raw != null) {
      try {
        Map<?, ?> snapshot = objectMapper.readValue(raw.toString(), Map.class);
        order.put("planSnapshot", snapshot);
        Object duration = snapshot.get("durationDays");
        if (duration instanceof Number && ((Number) duration).intValue() > 0)
          order.put(
              "periodCount",
              ((Number) order.get("buyDurationDays")).intValue() / ((Number) duration).intValue());
      } catch (Exception ignored) {
        order.put("planSnapshot", null);
      }
    }
  }

  private String transactionType(String orderType, BigDecimal refund) {
    if (refund.signum() > 0) return "REFUND";
    if ("BUY".equals(orderType)) return "BUY_PLAN";
    if ("RENEW".equals(orderType)) return "RENEW_PLAN";
    return orderType;
  }

  private Map<String, Object> query(int pageNum, int pageSize) {
    pageNum = Math.max(1, pageNum);
    pageSize = pageSize < 1 ? 10 : Math.min(pageSize, 100);
    Map<String, Object> q = new LinkedHashMap<>();
    q.put("offset", (pageNum - 1) * pageSize);
    q.put("pageSize", pageSize);
    return q;
  }

  private void putTimeRange(Map<String, Object> query, String startTime, String endTime) {
    LocalDateTime start = parseTime(startTime, "开始时间");
    LocalDateTime end = parseTime(endTime, "结束时间");
    if (start != null && end != null && start.isAfter(end)) {
      throw new BusinessException(400, "开始时间不能晚于结束时间");
    }
    query.put("startTime", start);
    query.put("endTime", end);
  }

  private LocalDateTime parseTime(String value, String fieldName) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    } catch (Exception exception) {
      throw new BusinessException(400, fieldName + "格式不正确，应为 yyyy-MM-dd HH:mm:ss");
    }
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new BusinessException(500, "套餐快照生成失败");
    }
  }

  private String required(Map<String, Object> b, String k) {
    Object v = b.get(k);
    if (v == null || v.toString().isBlank()) throw new BusinessException(400, k + "不能为空");
    return v.toString().trim();
  }

  private Long number(Map<String, Object> b, String k) {
    return Long.valueOf(required(b, k));
  }

  private int integer(Map<String, Object> b, String k) {
    return Integer.parseInt(required(b, k));
  }

  private BigDecimal decimal(Map<String, Object> b, String k) {
    try {
      return new BigDecimal(required(b, k));
    } catch (Exception e) {
      throw new BusinessException(400, k + "格式不正确");
    }
  }

  private BigDecimal money(Object v) {
    return v == null
        ? BigDecimal.ZERO
        : new BigDecimal(v.toString()).setScale(2, RoundingMode.HALF_UP);
  }

  private double round(double v) {
    return Math.round(v * 100d) / 100d;
  }
}
