package com.example.insurancesystem.saas.service.impl;

import com.example.insurancesystem.saas.config.BalanceAccessProperties;
import com.example.insurancesystem.saas.mapper.FinanceMapper;
import com.example.insurancesystem.saas.integration.InsuranceSessionInvalidationEvent;
import com.example.insurancesystem.saas.service.WalletBalanceService;
import com.example.insurancesystem.saas.support.PortalMaps;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 实现企业余额原子更新与有效订阅欠费状态联动。
 * 所有入口先锁定企业唯一订阅，再锁定钱包；余额写入和状态切换共享调用方事务，因此任一环节失败都会
 * 整体回滚。这里只处理欠费原因导致的暂停，套餐到期、未订阅、人工暂停等状态不会被余额变化覆盖。
 */
@Service
public class WalletBalanceServiceImpl implements WalletBalanceService {
  private static final int ACTIVE = 1;
  private static final int ARREARS_SUSPENDED = 3;
  private static final String ARREARS = "ARREARS";

  private final FinanceMapper mapper;
  private final BalanceAccessProperties properties;
  private final ApplicationEventPublisher events;

  public WalletBalanceServiceImpl(
      FinanceMapper mapper, BalanceAccessProperties properties, ApplicationEventPublisher events) {
    this.mapper = mapper;
    this.properties = properties;
    this.events = events;
  }

  @Override
  public BalanceChangeResult changeBalance(
      Long enterpriseId, BigDecimal changeAmount, Long operatorUserId, boolean allowNegative) {
    Map<String, Object> subscription = PortalMaps.camel(mapper.lockSubscription(enterpriseId));
    Map<String, Object> wallet = PortalMaps.camel(mapper.lockWallet(enterpriseId));
    if (wallet == null) throw new IllegalStateException("企业 " + enterpriseId + " 不存在可用钱包");

    BigDecimal before = money(wallet.get("balanceAmount"));
    BigDecimal after = before.add(changeAmount).setScale(2, RoundingMode.HALF_UP);
    if (!allowNegative && after.signum() < 0) throw new IllegalStateException("企业余额不足");

    if (changeAmount.signum() != 0) {
      Map<String, Object> update = new LinkedHashMap<>();
      update.put("walletId", wallet.get("id"));
      update.put("balanceBefore", before);
      update.put("balanceAfter", after);
      update.put("userId", operatorUserId);
      if (mapper.updateWallet(update) == 0)
        throw new IllegalStateException("企业 " + enterpriseId + " 钱包余额并发变化");
    }
    reconcileLockedSubscription(subscription, after);
    return new BalanceChangeResult(number(wallet.get("id")), before, after);
  }

  @Override
  public void reconcileSubscriptionAccess(Long enterpriseId) {
    Map<String, Object> subscription = PortalMaps.camel(mapper.lockSubscription(enterpriseId));
    Map<String, Object> wallet = PortalMaps.camel(mapper.lockWallet(enterpriseId));
    if (wallet == null) return;
    reconcileLockedSubscription(subscription, money(wallet.get("balanceAmount")));
  }

  /**
   * 仅在套餐标识存在且结束时间晚于当前时间时执行状态切换。
   * 正常订阅余额严格低于停止阈值时标记为欠费暂停；只有欠费暂停且余额严格高于恢复阈值时恢复，
   * 从而保护其他暂停原因并落实双阈值缓冲规则。
   */
  private void reconcileLockedSubscription(Map<String, Object> subscription, BigDecimal balance) {
    if (!hasUnexpiredPlan(subscription)) return;
    int status = intValue(subscription.get("status"));
    String reason = subscription.get("suspendReason") == null
        ? null : subscription.get("suspendReason").toString();
    Long subscriptionId = number(subscription.get("id"));
    if (status == ACTIVE && balance.compareTo(properties.getSuspendThreshold()) < 0) {
      if (mapper.suspendSubscriptionForArrears(subscriptionId, ARREARS) > 0)
        events.publishEvent(
            InsuranceSessionInvalidationEvent.enterprise(number(subscription.get("enterpriseId"))));
    } else if (status == ARREARS_SUSPENDED
        && ARREARS.equals(reason)
        && balance.compareTo(properties.getRestoreThreshold()) > 0) {
      mapper.restoreArrearsSubscription(subscriptionId, ARREARS);
    }
  }

  private boolean hasUnexpiredPlan(Map<String, Object> subscription) {
    if (subscription == null || subscription.get("planId") == null) return false;
    Object endAt = subscription.get("endAt");
    return endAt instanceof LocalDateTime && ((LocalDateTime) endAt).isAfter(LocalDateTime.now());
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
}
