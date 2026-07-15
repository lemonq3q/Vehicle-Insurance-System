package com.example.insurancesystem.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
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
