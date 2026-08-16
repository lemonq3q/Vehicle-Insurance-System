package com.example.insurancesystem.saas.maintenance;

import com.example.insurancesystem.system.MaintenanceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class SaasMaintenanceScheduler {
  private static final Logger log = LoggerFactory.getLogger(SaasMaintenanceScheduler.class);

  private final MaintenanceManager maintenanceManager;
  private final WorkorderOverageBillingCoordinator workorderBilling;
  private final SubscriptionMaintenanceCoordinator subscriptions;
  private final BalanceAccessMaintenanceCoordinator balanceAccess;
  private final InviteCleanupService inviteCleanupService;
  private final SaasDataArchiveService archiveService;

  public SaasMaintenanceScheduler(
      MaintenanceManager maintenanceManager,
      WorkorderOverageBillingCoordinator workorderBilling,
      SubscriptionMaintenanceCoordinator subscriptions,
      BalanceAccessMaintenanceCoordinator balanceAccess,
      InviteCleanupService inviteCleanupService,
      SaasDataArchiveService archiveService) {
    this.maintenanceManager = maintenanceManager;
    this.workorderBilling = workorderBilling;
    this.subscriptions = subscriptions;
    this.balanceAccess = balanceAccess;
    this.inviteCleanupService = inviteCleanupService;
    this.archiveService = archiveService;
  }

  @Scheduled(cron = "0 0 4 * * ?", zone = "Asia/Shanghai")
  public void maintain() {
    log.info("SaaS daily maintenance started");
    try {
      maintenanceManager.startMaintenance();
      maintenanceManager.waitForAllRequestsComplete();
      // 凌晨四点执行的是前一自然日的结算，因此超额工单计费必须最先使用“今天减一天”的业务日期。
      LocalDate billingDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
      int billedWorkorders = workorderBilling.bill(billingDate);
      // 日常余额变更已即时维护欠费状态；全量扫描紧随工单扣费执行，仅用于兜底修复状态不一致。
      int checkedWallets = balanceAccess.reconcileAll();
      subscriptions.processExpiredSubscriptions();
      int deletedInvites = inviteCleanupService.cleanup();
      int archived = archiveService.archiveDeletedData();
      log.info(
          "SaaS daily maintenance completed, billed workorders: {}, checked wallets: {}, deleted invites: {}, archived rows: {}",
          billedWorkorders,
          checkedWallets,
          deletedInvites,
          archived);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      log.error("SaaS daily maintenance interrupted", exception);
    } catch (Exception exception) {
      log.error("SaaS daily maintenance failed", exception);
    } finally {
      maintenanceManager.stopMaintenance();
    }
  }
}
