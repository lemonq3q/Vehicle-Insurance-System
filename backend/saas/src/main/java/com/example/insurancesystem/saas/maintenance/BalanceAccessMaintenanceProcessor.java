package com.example.insurancesystem.saas.maintenance;

import com.example.insurancesystem.saas.service.WalletBalanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 以单企业独立事务执行余额与订阅访问状态校准。
 * 该处理器不承担日常状态维护，只修复支付回调中断、人工调账或历史数据导致的不一致；单家企业失败
 * 不会回滚其他企业，协调器可继续扫描并在日志中暴露异常。
 */
@Service
public class BalanceAccessMaintenanceProcessor {
  private final WalletBalanceService balances;

  public BalanceAccessMaintenanceProcessor(WalletBalanceService balances) {
    this.balances = balances;
  }

  /**
   * 锁定指定企业的订阅和钱包并执行零金额校准，不生成资金流水，也不改变钱包更新时间。
   *
   * @param enterpriseId 钱包扫描得到的企业主键
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void reconcile(Long enterpriseId) {
    balances.reconcileSubscriptionAccess(enterpriseId);
  }
}
