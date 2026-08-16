package com.example.insurancesystem.saas.maintenance;

import com.example.insurancesystem.saas.mapper.FinanceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 遍历全部有效企业钱包并调度欠费访问状态兜底校准。
 * 协调器自身不持有事务，每个企业由处理器开启独立事务，确保坏数据或并发冲突不会中止全量维护。
 */
@Service
public class BalanceAccessMaintenanceCoordinator {
  private static final Logger log = LoggerFactory.getLogger(BalanceAccessMaintenanceCoordinator.class);

  private final FinanceMapper mapper;
  private final BalanceAccessMaintenanceProcessor processor;

  public BalanceAccessMaintenanceCoordinator(
      FinanceMapper mapper, BalanceAccessMaintenanceProcessor processor) {
    this.mapper = mapper;
    this.processor = processor;
  }

  /**
   * 按企业编号稳定顺序执行全量校准，并返回成功检查的钱包数量用于维护日志和运行监控。
   */
  public int reconcileAll() {
    int checked = 0;
    for (Long enterpriseId : mapper.findWalletEnterpriseIds()) {
      try {
        processor.reconcile(enterpriseId);
        checked++;
      } catch (Exception exception) {
        log.error("Failed to reconcile balance access for enterprise {}", enterpriseId, exception);
      }
    }
    return checked;
  }
}
