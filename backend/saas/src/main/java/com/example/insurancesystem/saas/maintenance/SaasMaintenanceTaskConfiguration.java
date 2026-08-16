package com.example.insurancesystem.saas.maintenance;

import com.example.insurancesystem.maintenance.MaintenanceTask;
import com.example.insurancesystem.maintenance.SimpleMaintenanceTask;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将 SaaS 原凌晨维护流程拆分为 C 可独立编排的任务编号。order 保留原有业务顺序并用于单机串行兜底，
 * 所有任务仅操作 SaaS 负责的数据，因此当前均允许单机执行。
 */
@Configuration
public class SaasMaintenanceTaskConfiguration {
    @Bean
    public MaintenanceTask workorderOverageTask(WorkorderOverageBillingCoordinator coordinator) {
        return new SimpleMaintenanceTask("saas-workorder-overage", 100, true,
                context -> coordinator.bill(context.getBusinessDate()));
    }

    @Bean
    public MaintenanceTask balanceAccessTask(BalanceAccessMaintenanceCoordinator coordinator) {
        return new SimpleMaintenanceTask("saas-balance-access", 200, true,
                context -> coordinator.reconcileAll());
    }

    @Bean
    public MaintenanceTask subscriptionTask(SubscriptionMaintenanceCoordinator coordinator) {
        return new SimpleMaintenanceTask("saas-subscriptions", 300, true,
                context -> coordinator.processExpiredSubscriptions());
    }

    @Bean
    public MaintenanceTask inviteCleanupTask(InviteCleanupService service) {
        return new SimpleMaintenanceTask("saas-invite-cleanup", 400, true,
                context -> service.cleanup());
    }

    @Bean
    public MaintenanceTask saasArchiveTask(SaasDataArchiveService service) {
        return new SimpleMaintenanceTask("saas-archive", 500, true,
                context -> service.archiveDeletedData());
    }
}
