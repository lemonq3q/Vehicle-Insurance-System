package com.example.insurancesystem.saas.service.impl;

import com.example.insurancesystem.domain.encapsulate.TableData;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.saas.integration.payment.PaymentGateway;
import com.example.insurancesystem.saas.mapper.EnterpriseMapper;
import com.example.insurancesystem.saas.mapper.FinanceMapper;
import com.example.insurancesystem.saas.service.FinanceService;
import com.example.insurancesystem.saas.service.MemberSeatService;
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

  public FinanceServiceImpl(
      FinanceMapper mapper,
      EnterpriseMapper enterprises,
      PortalContextService context,
      BusinessCodeGenerator codes,
      PaymentGateway payments,
      ObjectMapper objectMapper,
      MemberSeatService seats) {
    this.mapper = mapper;
    this.enterprises = enterprises;
    this.context = context;
    this.codes = codes;
    this.payments = payments;
    this.objectMapper = objectMapper;
    this.seats = seats;
  }

  public Map<String, Object> overview() {
    Long enterpriseId = context.enterpriseId();
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("wallet", PortalMaps.camel(mapper.findWallet(enterpriseId)));
    data.put("subscription", subscriptionView(enterpriseId));
    data.put("currentMemberCount", enterprises.countActiveMembers(enterpriseId));
    return data;
  }

  public List<Map<String, Object>> plans() {
    return PortalMaps.camel(mapper.findPlans());
  }

  public Map<String, Object> createRecharge(Map<String, Object> body) {
    Long enterpriseId =
        ((Number) context.requireRoles("OWNER", "ADMIN").get("enterpriseId")).longValue();
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
    return calculate(
        context.enterpriseId(), number(body, "planId"), integer(body, "periodCount"), false);
  }

  @Transactional
  public Map<String, Object> subscribe(Map<String, Object> body) {
    context.requireRoles("OWNER", "ADMIN");
    Long enterpriseId = context.enterpriseId();
    Long planId = number(body, "planId");
    int periods = integer(body, "periodCount");
    boolean autoRenew = Boolean.parseBoolean(String.valueOf(body.getOrDefault("autoRenew", false)));
    Map<String, Object> wallet = PortalMaps.camel(mapper.lockWallet(enterpriseId));
    if (wallet == null) throw new BusinessException(404, "企业钱包不存在");
    Map<String, Object> preview = calculate(enterpriseId, planId, periods, true);
    if (!(Boolean) preview.get("eligible")) {
      String message = String.valueOf(preview.get("validationMessage"));
      int code = message.contains("周期") ? 422 : 422;
      throw new BusinessException(code, message, preview);
    }
    BigDecimal balance = money(wallet.get("balanceAmount")),
        payable = money(preview.get("payableAmount")),
        refund = money(preview.get("refundAmount"));
    if (balance.compareTo(payable) < 0) throw new BusinessException(409, "企业余额不足，请先充值", preview);
    BigDecimal after = balance.subtract(payable).add(refund).setScale(2, RoundingMode.HALF_UP);
    Map<String, Object> update = new LinkedHashMap<>();
    update.put("walletId", wallet.get("id"));
    update.put("balanceBefore", balance);
    update.put("balanceAfter", after);
    update.put("userId", context.userId());
    if (mapper.updateWallet(update) == 0) throw new BusinessException(409, "企业余额已变化，请重试");
    Map<String, Object> plan = (Map<String, Object>) preview.get("plan"),
        currentSub = PortalMaps.camel(mapper.findSubscription(enterpriseId));
    Map<String, Object> order = new LinkedHashMap<>();
    order.putAll(preview);
    order.put("enterpriseId", enterpriseId);
    order.put("userId", context.userId());
    order.put("planId", planId);
    order.put("userLimit", plan.get("userLimit"));
    order.put("durationDays", ((Number) plan.get("durationDays")).intValue() * periods);
    order.put("subscriptionId", currentSub == null ? null : currentSub.get("id"));
    order.put("oldPlanId", currentSub == null ? null : currentSub.get("planId"));
    order.put("autoRenew", autoRenew ? 1 : 0);
    order.put("planSnapshotJson", json(plan));
    UniqueCodeRetryUtil.insertWithGeneratedCode(
        SaasCodeConstraints.SUBSCRIPTION_ORDER_NO,
        codes::subscriptionOrderNo,
        orderNo -> order.put("orderNo", orderNo),
        () -> mapper.insertOrder(order));
    Map<String, Object> subscription = new LinkedHashMap<>();
    if (currentSub != null) subscription.put("id", currentSub.get("id"));
    subscription.put("enterpriseId", enterpriseId);
    subscription.put("planId", planId);
    subscription.put("orderId", order.get("id"));
    subscription.put("userLimit", plan.get("userLimit"));
    subscription.put(
        "startAt",
        currentSub != null && "RENEW".equals(preview.get("orderType"))
            ? currentSub.get("startAt")
            : preview.get("startAt"));
    subscription.put("endAt", preview.get("endAt"));
    subscription.put("autoRenew", autoRenew ? 1 : 0);
    subscription.put("nextRenewAt", autoRenew ? preview.get("endAt") : null);
    if (currentSub == null) mapper.insertSubscription(subscription);
    else mapper.updateSubscription(subscription);
    seats.synchronize(enterpriseId, ((Number) plan.get("userLimit")).intValue());
    Long subscriptionId =
        currentSub == null
            ? ((Number) subscription.get("id")).longValue()
            : ((Number) currentSub.get("id")).longValue();
    BigDecimal change = refund.signum() > 0 ? refund : payable;
    Map<String, Object> tx = new LinkedHashMap<>();
    tx.put("enterpriseId", enterpriseId);
    tx.put("walletId", wallet.get("id"));
    tx.put("userId", context.userId());
    tx.put("direction", refund.signum() > 0 ? "IN" : "OUT");
    tx.put("transactionType", transactionType(String.valueOf(preview.get("orderType")), refund));
    tx.put("amount", change);
    tx.put("balanceBefore", balance);
    tx.put("balanceAfter", after);
    tx.put("orderId", order.get("id"));
    tx.put("subscriptionId", subscriptionId);
    tx.put("remark", "套餐订单 " + order.get("orderNo"));
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
    return order;
  }

  public Map<String, Object> updateAutoRenew(Map<String, Object> body) {
    context.requireRoles("OWNER", "ADMIN");
    Map<String, Object> subscription =
        PortalMaps.camel(mapper.findSubscription(context.enterpriseId()));
    if (subscription == null) throw new BusinessException(404, "当前订阅不存在");
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
      Long enterpriseId, Long planId, int periods, boolean submitting) {
    if (periods < 1) throw new BusinessException(400, "订阅周期必须为正整数");
    Map<String, Object> plan = PortalMaps.camel(mapper.findPlan(planId));
    if (plan == null) throw new BusinessException(404, "套餐不存在");
    Map<String, Object> sub = PortalMaps.camel(mapper.findSubscription(enterpriseId)),
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
    BigDecimal
        priceAmount = price.multiply(BigDecimal.valueOf(periods)).setScale(2, RoundingMode.HALF_UP),
        diff = priceAmount.subtract(credit),
        payable = diff.max(BigDecimal.ZERO),
        refund = diff.min(BigDecimal.ZERO).abs();
    Map<String, Object> wallet = PortalMaps.camel(mapper.findWallet(enterpriseId));
    BigDecimal balance = wallet == null ? BigDecimal.ZERO : money(wallet.get("balanceAmount"));
    int members = enterprises.countActiveMembers(enterpriseId),
        limit = ((Number) plan.get("userLimit")).intValue();
    boolean eligible = periods >= minimum && members <= limit;
    String message =
        periods < minimum
            ? "改订周期不能少于 " + minimum + " 个周期"
            : members > limit ? "当前企业有 " + members + " 名成员，超过该套餐 " + limit + " 人的成员上限" : "";
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
    if (sub != null)
      sub.put("plan", PortalMaps.camel(mapper.findPlan(((Number) sub.get("planId")).longValue())));
    return sub;
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
