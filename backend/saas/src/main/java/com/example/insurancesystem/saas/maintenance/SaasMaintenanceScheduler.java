package com.example.insurancesystem.saas.maintenance;

import com.example.insurancesystem.system.MaintenanceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SaasMaintenanceScheduler {
  private static final Logger log = LoggerFactory.getLogger(SaasMaintenanceScheduler.class);

  private final MaintenanceManager maintenanceManager;
  private final SubscriptionMaintenanceCoordinator subscriptions;
  private final SaasDataArchiveService archiveService;

  public SaasMaintenanceScheduler(
      MaintenanceManager maintenanceManager,
      SubscriptionMaintenanceCoordinator subscriptions,
      SaasDataArchiveService archiveService) {
    this.maintenanceManager = maintenanceManager;
    this.subscriptions = subscriptions;
    this.archiveService = archiveService;
  }

  @Scheduled(cron = "0 0 4 * * ?", zone = "Asia/Shanghai")
  public void maintain() {
    log.info("SaaS daily maintenance started");
    try {
      maintenanceManager.startMaintenance();
      maintenanceManager.waitForAllRequestsComplete();
      subscriptions.processExpiredSubscriptions();
      int archived = archiveService.archiveDeletedData();
      log.info("SaaS daily maintenance completed, archived rows: {}", archived);
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
