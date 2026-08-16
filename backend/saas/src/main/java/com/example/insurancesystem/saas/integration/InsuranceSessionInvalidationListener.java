package com.example.insurancesystem.saas.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 在 SaaS 数据库事务成功提交后调用车险会话服务。
 * 远端失败会记录可检索日志而不回滚已提交业务；车险端登录仍有数据库状态校验，后续可基于日志或监控
 * 增加重试队列，当前实现优先消除跨服务调用对核心事务的侵入。
 */
@Component
public class InsuranceSessionInvalidationListener {
  private static final Logger log = LoggerFactory.getLogger(InsuranceSessionInvalidationListener.class);
  private final InsuranceSessionClient client;

  public InsuranceSessionInvalidationListener(InsuranceSessionClient client) {
    this.client = client;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void invalidate(InsuranceSessionInvalidationEvent event) {
    try {
      if (event.scope() == InsuranceSessionInvalidationEvent.Scope.ENTERPRISE)
        client.logoutEnterprise(event.targetId());
      else client.logoutUser(event.targetId());
    } catch (Exception exception) {
      log.error(
          "Failed to invalidate insurance session, scope: {}, targetId: {}",
          event.scope(), event.targetId(), exception);
    }
  }
}
