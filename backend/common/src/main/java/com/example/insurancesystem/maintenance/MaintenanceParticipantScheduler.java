package com.example.insurancesystem.maintenance;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 参与端本地监控：高频检查 C 的维护租约，并在凌晨兜底时刻仍未收到 START 时启动无 runId 单机维护。
 * 正常联机任务不由此调度，避免与 C 重复执行。
 */
@Component
public class MaintenanceParticipantScheduler {
    private final MaintenanceParticipantService service;

    public MaintenanceParticipantScheduler(MaintenanceParticipantService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${maintenance.participant.lease-check-interval-ms:10000}")
    public void checkLease() { service.checkLease(); }

    @Scheduled(cron = "${maintenance.participant.fallback-cron:0 15 4 * * ?}",
            zone = "${maintenance.participant.zone:Asia/Shanghai}")
    public void fallback() { service.startFallbackIfNeeded(); }
}
