package com.example.insurancesystem.maintenance;

/**
 * 业务后端中预先部署的维护任务契约。任务编号由协调计划引用，order 只决定失联后的本地串行顺序；
 * standaloneEnabled=false 的跨服务任务禁止在单机模式执行。
 */
public interface MaintenanceTask {
    String taskId();
    int order();
    boolean standaloneEnabled();
    void execute(MaintenanceTaskContext context) throws Exception;
}
