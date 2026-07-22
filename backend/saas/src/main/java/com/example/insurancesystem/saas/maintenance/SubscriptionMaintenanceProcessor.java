package com.example.insurancesystem.saas.maintenance;

import com.example.insurancesystem.saas.mapper.FinanceMapper;
import com.example.insurancesystem.saas.mapper.SubscriptionMaintenanceMapper;
import com.example.insurancesystem.saas.service.MemberSeatService;
import com.example.insurancesystem.saas.support.*;
import com.example.insurancesystem.utils.UniqueCodeRetryUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionMaintenanceProcessor {
  private final SubscriptionMaintenanceMapper maintenanceMapper;
  private final FinanceMapper financeMapper;
  private final MemberSeatService seats;
  private final BusinessCodeGenerator codes;
  private final ObjectMapper objectMapper;

  public SubscriptionMaintenanceProcessor(
      SubscriptionMaintenanceMapper maintenanceMapper,
      FinanceMapper financeMapper,
      MemberSeatService seats,
      BusinessCodeGenerator codes,
      ObjectMapper objectMapper) {
    this.maintenanceMapper = maintenanceMapper;
    this.financeMapper = financeMapper;
    this.seats = seats;
    this.codes = codes;
    this.objectMapper = objectMapper;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public String process(Long subscriptionId) {
    Map<String, Object> subscription =
        PortalMaps.camel(maintenanceMapper.lockSubscription(subscriptionId));
    if (!isDue(subscription)) return "SKIPPED";

    Long enterpriseId = number(subscription.get("enterpriseId"));
    if (!enabled(subscription.get("autoRenewEnabled"))) {
      expire(subscriptionId, enterpriseId);
      return "EXPIRED";
    }

    Long planId =
        subscription.get("autoRenewPlanId") == null
            ? number(subscription.get("planId"))
            : number(subscription.get("autoRenewPlanId"));
    Map<String, Object> plan = PortalMaps.camel(financeMapper.findPlan(planId));
    Map<String, Object> wallet = PortalMaps.camel(financeMapper.lockWallet(enterpriseId));
    if (plan == null || wallet == null) {
      createFailureOrder(subscription, plan, plan == null ? "自动续费套餐不存在或已下架" : "企业钱包不存在");
      expire(subscriptionId, enterpriseId);
      return "EXPIRED_CONFIGURATION";
    }

    BigDecimal price = money(plan.get("price"));
    BigDecimal balance = money(wallet.get("balanceAmount"));
    if (balance.compareTo(price) < 0) {
      createFailureOrder(subscription, plan, "企业余额不足");
      expire(subscriptionId, enterpriseId);
      return "EXPIRED_INSUFFICIENT_BALANCE";
    }

    Long ownerUserId = maintenanceMapper.findOwnerUserId(enterpriseId);
    BigDecimal balanceAfter = balance.subtract(price);
    Map<String, Object> walletUpdate = new LinkedHashMap<>();
    walletUpdate.put("walletId", wallet.get("id"));
    walletUpdate.put("balanceBefore", balance);
    walletUpdate.put("balanceAfter", balanceAfter);
    walletUpdate.put("userId", ownerUserId);
    if (financeMapper.updateWallet(walletUpdate) == 0) {
      throw new IllegalStateException("Wallet balance changed while auto renewing");
    }

    int durationDays = ((Number) plan.get("durationDays")).intValue();
    int userLimit = ((Number) plan.get("userLimit")).intValue();
    LocalDateTime oldEnd = (LocalDateTime) subscription.get("endAt");
    LocalDateTime newEnd = oldEnd.plusDays(durationDays);

    Map<String, Object> order = new LinkedHashMap<>();
    order.put("orderType", "AUTO_RENEW");
    order.put("enterpriseId", enterpriseId);
    order.put("userId", ownerUserId);
    order.put("planId", planId);
    order.put("planSnapshotJson", json(plan));
    order.put("userLimit", userLimit);
    order.put("durationDays", durationDays);
    order.put("payableAmount", price);
    order.put("priceAmount", price);
    order.put("creditAmount", BigDecimal.ZERO);
    order.put("refundAmount", BigDecimal.ZERO);
    order.put("subscriptionId", subscriptionId);
    order.put("oldPlanId", subscription.get("planId"));
    order.put("autoRenew", 1);
    UniqueCodeRetryUtil.insertWithGeneratedCode(
        SaasCodeConstraints.SUBSCRIPTION_ORDER_NO,
        codes::subscriptionOrderNo,
        orderNo -> order.put("orderNo", orderNo),
        () -> financeMapper.insertOrder(order));

    Map<String, Object> update = new LinkedHashMap<>();
    update.put("id", subscriptionId);
    update.put("enterpriseId", enterpriseId);
    update.put("planId", planId);
    update.put("orderId", order.get("id"));
    update.put("userLimit", userLimit);
    update.put("ocrQuota", intValue(plan.get("ocrQuota")));
    update.put("requestQuota", intValue(plan.get("requestQuota")));
    update.put("startAt", subscription.get("startAt"));
    update.put("endAt", newEnd);
    update.put("autoRenew", 1);
    update.put("nextRenewAt", newEnd);
    financeMapper.updateSubscription(update);

    Map<String, Object> transaction = new LinkedHashMap<>();
    transaction.put("enterpriseId", enterpriseId);
    transaction.put("walletId", wallet.get("id"));
    transaction.put("userId", ownerUserId);
    transaction.put("direction", "OUT");
    transaction.put("transactionType", "AUTO_RENEW");
    transaction.put("amount", price);
    transaction.put("balanceBefore", balance);
    transaction.put("balanceAfter", balanceAfter);
    transaction.put("orderId", order.get("id"));
    transaction.put("subscriptionId", subscriptionId);
    transaction.put("remark", "套餐自动续费 " + order.get("orderNo"));
    UniqueCodeRetryUtil.insertWithGeneratedCode(
        SaasCodeConstraints.WALLET_TRANSACTION_NO,
        codes::transactionNo,
        transactionNo -> transaction.put("transactionNo", transactionNo),
        () -> financeMapper.insertTransaction(transaction));
    financeMapper.bindOrderTransaction(number(order.get("id")), number(transaction.get("id")));
    seats.synchronize(enterpriseId, userLimit);
    return "RENEWED";
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void expireAfterFailure(Long subscriptionId, String failureReason) {
    Map<String, Object> subscription =
        PortalMaps.camel(maintenanceMapper.lockSubscription(subscriptionId));
    if (!isDue(subscription)) return;
    Long enterpriseId = number(subscription.get("enterpriseId"));
    Long planId =
        subscription.get("autoRenewPlanId") == null
            ? number(subscription.get("planId"))
            : number(subscription.get("autoRenewPlanId"));
    createFailureOrder(
        subscription, PortalMaps.camel(financeMapper.findPlan(planId)), failureReason);
    expire(subscriptionId, enterpriseId);
  }

  private void createFailureOrder(
      Map<String, Object> subscription, Map<String, Object> plan, String failureReason) {
    Long enterpriseId = number(subscription.get("enterpriseId"));
    Long planId =
        subscription.get("autoRenewPlanId") == null
            ? number(subscription.get("planId"))
            : number(subscription.get("autoRenewPlanId"));
    BigDecimal price = plan == null ? BigDecimal.ZERO : money(plan.get("price"));
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("enterpriseId", enterpriseId);
    order.put("userId", maintenanceMapper.findOwnerUserId(enterpriseId));
    order.put("planId", planId);
    order.put("planSnapshotJson", plan == null ? null : json(plan));
    order.put("userLimit", plan == null ? subscription.get("userLimit") : plan.get("userLimit"));
    order.put("durationDays", plan == null ? 0 : plan.get("durationDays"));
    order.put("payableAmount", price);
    order.put("priceAmount", price);
    order.put("subscriptionId", subscription.get("id"));
    order.put("oldPlanId", subscription.get("planId"));
    order.put(
        "failureReason",
        failureReason == null
            ? "自动续费执行异常"
            : failureReason.substring(0, Math.min(failureReason.length(), 500)));
    UniqueCodeRetryUtil.insertWithGeneratedCode(
        SaasCodeConstraints.SUBSCRIPTION_ORDER_NO,
        codes::subscriptionOrderNo,
        orderNo -> order.put("orderNo", orderNo),
        () -> financeMapper.insertAutoRenewFailureOrder(order));
  }

  private void expire(Long subscriptionId, Long enterpriseId) {
    maintenanceMapper.expireSubscription(subscriptionId);
    seats.disableAll(enterpriseId);
  }

  private boolean isDue(Map<String, Object> subscription) {
    if (subscription == null || ((Number) subscription.get("status")).intValue() != 1) return false;
    LocalDateTime endAt = (LocalDateTime) subscription.get("endAt");
    return endAt != null && !endAt.isAfter(LocalDateTime.now());
  }

  private boolean enabled(Object value) {
    return value != null && ((Number) value).intValue() == 1;
  }

  private int intValue(Object value) {
    return value == null ? 0 : ((Number) value).intValue();
  }

  private Long number(Object value) {
    return ((Number) value).longValue();
  }

  private BigDecimal money(Object value) {
    return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize plan snapshot", exception);
    }
  }
}
