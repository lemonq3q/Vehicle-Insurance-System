package com.example.insurancesystem.saas.maintenance;

import com.example.insurancesystem.saas.config.WorkorderOverageBillingProperties;
import com.example.insurancesystem.saas.mapper.FinanceMapper;
import com.example.insurancesystem.saas.mapper.SubscriptionMaintenanceMapper;
import com.example.insurancesystem.saas.mapper.WorkorderOverageBillingMapper;
import com.example.insurancesystem.saas.service.WalletBalanceService;
import com.example.insurancesystem.saas.service.WalletBalanceService.BalanceChangeResult;
import com.example.insurancesystem.saas.support.BusinessCodeGenerator;
import com.example.insurancesystem.saas.support.PortalMaps;
import com.example.insurancesystem.saas.support.SaasCodeConstraints;
import com.example.insurancesystem.utils.UniqueCodeRetryUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkorderOverageBillingProcessor {
  private final WorkorderOverageBillingMapper billingMapper;
  private final FinanceMapper financeMapper;
  private final SubscriptionMaintenanceMapper subscriptionMapper;
  private final BusinessCodeGenerator codes;
  private final WorkorderOverageBillingProperties properties;
  private final WalletBalanceService balances;

  public WorkorderOverageBillingProcessor(
      WorkorderOverageBillingMapper billingMapper,
      FinanceMapper financeMapper,
      SubscriptionMaintenanceMapper subscriptionMapper,
      BusinessCodeGenerator codes,
      WorkorderOverageBillingProperties properties,
      WalletBalanceService balances) {
    this.billingMapper = billingMapper;
    this.financeMapper = financeMapper;
    this.subscriptionMapper = subscriptionMapper;
    this.codes = codes;
    this.properties = properties;
    this.balances = balances;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int bill(Long subscriptionId, LocalDate billingDate) {
    // 锁定企业当前订阅并再次验证状态，防止扫描完成后套餐发生变化时仍按旧额度扣费。
    Map<String, Object> subscription = PortalMaps.camel(billingMapper.lockSubscription(subscriptionId));
    if (subscription == null || intValue(subscription.get("status")) != 1) return 0;
    Long enterpriseId = number(subscription.get("enterpriseId"));
    int workorderLimit = Math.max(0, intValue(subscription.get("workorderLimit")));

    // 先按录入时间和 ID 排序剔除额度内工单，再从超额部分筛选恰逢周期日且当天未扣费的记录。
    List<Long> workorderIds =
        billingMapper.findDailyBillableWorkorderIds(
            enterpriseId, workorderLimit, billingDate, properties.getCycleDays());
    if (workorderIds.isEmpty()) return 0;

    // 每个企业通过统一余额入口扣费；超额工单是唯一允许穿透零余额的场景，并会即时触发欠费状态校准。
    BigDecimal amount =
        properties
            .getUnitPrice()
            .multiply(BigDecimal.valueOf(workorderIds.size()))
            .setScale(2, RoundingMode.HALF_UP);
    BalanceChangeResult balanceChange =
        balances.changeBalance(enterpriseId, amount.negate(), null, true);

    Map<String, Object> transaction = new LinkedHashMap<>();
    transaction.put("enterpriseId", enterpriseId);
    transaction.put("walletId", balanceChange.walletId());
    transaction.put("userId", subscriptionMapper.findOwnerUserId(enterpriseId));
    transaction.put("direction", "OUT");
    transaction.put("transactionType", "WORKORDER_OVERAGE");
    transaction.put("amount", amount);
    transaction.put("balanceBefore", balanceChange.balanceBefore());
    transaction.put("balanceAfter", balanceChange.balanceAfter());
    transaction.put("subscriptionId", subscriptionId);
    transaction.put(
        "remark",
        billingDate + " 超额工单周期费：" + workorderIds.size() + " 单 × ¥" + properties.getUnitPrice());
    UniqueCodeRetryUtil.insertWithGeneratedCode(
        SaasCodeConstraints.WALLET_TRANSACTION_NO,
        codes::transactionNo,
        transactionNo -> transaction.put("transactionNo", transactionNo),
        () -> financeMapper.insertTransaction(transaction));

    int marked =
        billingMapper.markBilled(
            enterpriseId, workorderIds, billingDate, properties.getCycleDays());
    if (marked != workorderIds.size())
      throw new IllegalStateException("企业 " + enterpriseId + " 工单扣费标记数量不一致");
    return marked;
  }

  private int intValue(Object value) {
    return value == null ? 0 : ((Number) value).intValue();
  }

  private Long number(Object value) {
    return ((Number) value).longValue();
  }

}
