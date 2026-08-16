package com.example.insurancesystem.saas.service;

import java.math.BigDecimal;

/**
 * 定义企业钱包余额的唯一变更入口，并在余额落库后同步校准有效订阅的访问状态。
 * 充值、套餐购买或改订、自动续费和超额工单扣费均应通过该接口修改余额，调用方仍负责在同一事务
 * 中写入对应订单和资金流水。接口通过固定的“订阅后钱包”加锁顺序处理并发，避免不同业务形成锁环。
 */
public interface WalletBalanceService {
  /**
   * 按带符号金额修改企业余额并返回变更快照。
   *
   * @param enterpriseId 企业主键，来自当前门户上下文或维护任务订阅快照
   * @param changeAmount 带符号变更额；正数入账、负数扣款，零仅用于状态校准
   * @param operatorUserId 操作用户；系统维护允许为空
   * @param allowNegative 是否允许本次业务把余额扣为负数；只有超额工单计费应传 true
   * @return 钱包主键及变更前后余额，供资金流水保持同一金额快照
   * @throws IllegalStateException 钱包不存在、余额不足或发生并发更新时抛出并回滚调用事务
   */
  BalanceChangeResult changeBalance(
      Long enterpriseId, BigDecimal changeAmount, Long operatorUserId, boolean allowNegative);

  /**
   * 不改变钱包金额，仅依据数据库当前余额修复有效订阅的欠费暂停或恢复状态。
   * 该方法服务于凌晨维护兜底，日常业务应依赖 {@link #changeBalance} 的即时校准。
   */
  void reconcileSubscriptionAccess(Long enterpriseId);

  /** 企业钱包一次原子变更的只读结果。 */
  record BalanceChangeResult(Long walletId, BigDecimal balanceBefore, BigDecimal balanceAfter) {}
}
