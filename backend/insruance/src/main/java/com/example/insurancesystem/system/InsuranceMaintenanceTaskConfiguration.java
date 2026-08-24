package com.example.insurancesystem.system;

import com.example.insurancesystem.maintenance.MaintenanceTask;
import com.example.insurancesystem.maintenance.SimpleMaintenanceTask;
import com.example.insurancesystem.statistics.EnterpriseDailyStatisticsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册车险后端的本地维护任务。任务由 C 在联机模式按编号下发，C 失联时则按 order 串行执行，
 * 从而替代原本与 SaaS 同时触发且无法协调的独立凌晨四点调度器。
 */
@Configuration
public class InsuranceMaintenanceTaskConfiguration {
    @Bean
    public MaintenanceTask renewalStatusTask(RenewalStatusManager manager) {
        return new SimpleMaintenanceTask("insurance-renewal-status", 100, true,
                context -> manager.resetExpiredStatuses());
    }

    /**
     * 在业务归档前终算已经结束的前一自然日，保证被清理或归档的数据仍能进入最终统计。
     * 任务采用绝对值覆盖和 Redis 延迟清理，协调器重试不会重复累计。
     */
    @Bean
    public MaintenanceTask enterpriseDailyStatisticsTask(EnterpriseDailyStatisticsService service) {
        return new SimpleMaintenanceTask("insurance-enterprise-daily-statistics", 150, true,
                context -> service.finalizeDay(context.getBusinessDate().minusDays(1)));
    }

    @Bean
    public MaintenanceTask insuranceArchiveTask(DataArchive archive) {
        return new SimpleMaintenanceTask("insurance-archive", 200, true,
                context -> archive.archive());
    }
}
