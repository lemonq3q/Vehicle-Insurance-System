package com.example.insurancesystem.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
/**
 * 编排车险系统每日凌晨四点的维护窗口。
 * 维护期间先阻止新请求并等待在途请求结束，再执行续保状态重置和数据归档，最后恢复服务。
 */
public class DailyTaskScheduler {

    @Autowired
    private MaintenanceManager maintenanceManager;

    @Autowired
    private DataArchive dataArchive;

    @Autowired
    private RenewalStatusManager renewalStatusManager;

    /**
     * cron 表达式：秒 分 时 日 月 周
     * 0 0 4 * * ? = 每天凌晨 4:00 执行
     */
    @Scheduled(cron = "0 0 4 * * ?")
    /**
     * 串行执行每日维护任务。无论任务成功或异常，finally 都会关闭维护模式，
     * 避免系统因单次维护失败而长期拒绝正常业务请求。
     */
    public void executeDailyTasks() {
        System.out.println("============= 凌晨4点定时任务开始执行 =============");

        try {
            maintenanceManager.startMaintenance();
            maintenanceManager.waitForAllRequestsComplete();

            renewalStatusManager.resetExpiredStatuses();
            dataArchive.archive();

            System.out.println("============= 凌晨4点定时任务全部执行完成 =============");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("凌晨4点定时任务执行异常：" + e.getMessage());
        } finally {
            maintenanceManager.stopMaintenance();
        }
    }


}
