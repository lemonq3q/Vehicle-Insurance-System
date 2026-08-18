package com.example.insurancesystem.saas.maintenance;

import com.example.insurancesystem.maintenance.MaintenanceTask;
import com.example.insurancesystem.maintenance.SimpleMaintenanceTask;
import com.example.insurancesystem.saas.service.ReminderService;
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

    /**
     * 余额访问兜底完成后生成提醒、订阅到期处理前读取即将到期状态。任务需要调用监控后端，
     * 因此禁止单机执行，并由总协调器确认 SaaS 与监控服务均可用。
     */
    @Bean
    public MaintenanceTask reminderTask(ReminderService service) {
        return new SimpleMaintenanceTask("saas-recent-reminders", 250, false,
                context -> service.generateDailyReminders());
    }

    /**
     * 跨服务清除超过保留期的企业车险业务资料。任务实现需要调用车险后端，因此禁止在 SaaS 单机维护模式
     * 执行；联机模式下 C 还会通过 required-services 确认 SaaS 与车险服务均已 READY。
     */
    @Bean
    public MaintenanceTask enterpriseDataRetentionTask(EnterpriseDataRetentionCoordinator coordinator) {
        return new SimpleMaintenanceTask("saas-enterprise-data-retention", 350, false,
                context -> coordinator.purgeExpiredEnterpriseData());
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
