package com.example.insurancesystem.saas.service.impl;

import com.example.insurancesystem.domain.encapsulate.TableData;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.saas.integration.payment.PaymentGateway;
import com.example.insurancesystem.saas.config.WorkorderOverageBillingProperties;
import com.example.insurancesystem.saas.mapper.EnterpriseMapper;
import com.example.insurancesystem.saas.mapper.FinanceMapper;
import com.example.insurancesystem.saas.mapper.WorkorderOverageBillingMapper;
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
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceServiceImpl implements FinanceService {
  private final FinanceMapper mapper;
  private final EnterpriseMapper enterprises;
  private final PortalContextService context;
  private final BusinessCodeGenerator codes;
  private final PaymentGateway payments;
  private final ObjectMapper objectMapper;
  private final MemberSeatService seats;
  private final WorkorderOverageBillingMapper workorderBillingMapper;
  private final WorkorderOverageBillingProperties workorderBillingProperties;
  private final WalletBalanceService balances;
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

  public FinanceServiceImpl(
      FinanceMapper mapper,
      EnterpriseMapper enterprises,
      PortalContextService context,
      BusinessCodeGenerator codes,
      PaymentGateway payments,
      ObjectMapper objectMapper,
      MemberSeatService seats,
      WorkorderOverageBillingMapper workorderBillingMapper,
      WorkorderOverageBillingProperties workorderBillingProperties,
      WalletBalanceService balances) {
    this.mapper = mapper;
    this.enterprises = enterprises;
    this.context = context;
    this.codes = codes;
    this.payments = payments;
    this.objectMapper = objectMapper;
    this.seats = seats;
    this.workorderBillingMapper = workorderBillingMapper;
    this.workorderBillingProperties = workorderBillingProperties;
    this.balances = balances;
  }

  public Map<String, Object> overview() {
    Long enterpriseId = context.enterpriseId();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("wallet", walletView(enterpriseId));
    data.put("subscription", subscriptionView(enterpriseId));
    data.put("currentMemberCount", enterprises.countActiveMembers(enterpriseId));
    return data;
  }

  public List<Map<String, Object>> plans() {
    return PortalMaps.camel(mapper.findPlans());
  }

  @Transactional
  public Map<String, Object> createRecharge(Map<String, Object> body) {
    Long enterpriseId =
        ((Number) context.requireRoles("OWNER", "ADMIN").get("enterpriseId")).longValue();
    ensureWallet(enterpriseId);
    BigDecimal amount = decimal(body, "amount");
    if (amount.signum() <= 0) throw new BusinessException(400, "充值金额必须大于 0");
    String channel = required(body, "payChannel").toUpperCase();
    if (!Set.of("WECHAT", "ALIPAY", "BANK").contains(channel))
      throw new BusinessException(400, "支付渠道不支持");
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("enterpriseId", enterpriseId);
    order.put("userId", context.userId());
    order.put("amount", amount);
    order.put("payChannel", channel);
    order.put("status", 1);
    order.put("createdAt", LocalDateTime.now());
    UniqueCodeRetryUtil.insertWithGeneratedCode(
        SaasCodeConstraints.RECHARGE_NO,
        codes::rechargeNo,
        rechargeNo -> {
          order.put("rechargeNo", rechargeNo);
          order.put("payTradeNo", payments.createPayment(rechargeNo, amount, channel));
        },
        () -> mapper.insertRecharge(order));
    return PortalMaps.camel(order);
  }

  public Map<String, Object> rechargeDetail(Long id) {
    context.requireRoles("OWNER", "ADMIN");
    Map<String, Object> order = PortalMaps.camel(mapper.findRechargeOrder(id, context.enterpriseId()));
    if (order == null) throw new BusinessException(404, "充值订单不存在");
    return order;
  }

  @Transactional
  public Map<String, Object> completeRecharge(Map<String, Object> body) {
    context.requireRoles("OWNER", "ADMIN");
    Long enterpriseId = context.enterpriseId();
    Long orderId = number(body, "rechargeOrderId");
    Map<String, Object> order = PortalMaps.camel(mapper.lockRechargeOrder(orderId, enterpriseId));
    if (order == null) throw new BusinessException(404, "充值订单不存在");

    int status = ((Number) order.get("status")).intValue();
    if (status == 2) return order;
    if (status != 1) throw new BusinessException(409, "当前充值订单状态不可支付");

    ensureWallet(enterpriseId);
    BigDecimal amount = money(order.get("amount"));
    BalanceChangeResult balanceChange =
        balances.changeBalance(enterpriseId, amount, context.userId(), false);
    if (mapper.completeRechargeOrder(orderId, enterpriseId) == 0)
      throw new BusinessException(409, "充值订单状态已变化，请刷新后重试");

    Map<String, Object> transaction = new LinkedHashMap<>();
    transaction.put("enterpriseId", enterpriseId);
    transaction.put("walletId", balanceChange.walletId());
    transaction.put("userId", context.userId());
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

  @Transactional
  public Map<String, Object> cancelRecharge(Long id) {
    context.requireRoles("OWNER", "ADMIN");
    Long enterpriseId = context.enterpriseId();
    Map<String, Object> order = PortalMaps.camel(mapper.lockRechargeOrder(id, enterpriseId));
    if (order == null) throw new BusinessException(404, "充值订单不存在");
    int status = ((Number) order.get("status")).intValue();
    if (status == 3) return order;
    if (status != 1) throw new BusinessException(409, "只有待支付订单可以取消");
    if (mapper.cancelRechargeOrder(id, enterpriseId) == 0)
      throw new BusinessException(409, "充值订单状态已变化，请刷新后重试");
    order.put("status", 3);
    return order;
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
    LocalDateTime now = LocalDateTime.now(), start = now, end;
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
      remainingDays = Math.max(0, Duration.between(now, oldEnd).toSeconds() / 86400d);
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
                  .multiply(BigDecimal.valueOf(remainingPeriods))
                  .setScale(2, RoundingMode.HALF_UP);
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
    return endAt instanceof LocalDateTime && ((LocalDateTime) endAt).isAfter(LocalDateTime.now())
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

  private Map<String, Object> ensureWallet(Long enterpriseId) {
    enterprises.lockEnterprise(enterpriseId);
    Map<String, Object> wallet = PortalMaps.camel(mapper.lockWallet(enterpriseId));
    if (wallet != null) return wallet;

    enterprises.insertWallet(enterpriseId, context.userId());
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
