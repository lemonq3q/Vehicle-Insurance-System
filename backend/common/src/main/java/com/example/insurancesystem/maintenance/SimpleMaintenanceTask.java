package com.example.insurancesystem.maintenance;

/**
 * 将现有业务维护方法适配为统一任务契约，避免为每个简单任务创建仅做委托的类型。
 * 业务规则仍保留在原有 Service/Coordinator 中，本类只保存任务元数据和执行入口。
 */
public class SimpleMaintenanceTask implements MaintenanceTask {
    @FunctionalInterface
    public interface Action { void execute(MaintenanceTaskContext context) throws Exception; }

    private final String taskId;
    private final int order;
    private final boolean standaloneEnabled;
    private final Action action;

    public SimpleMaintenanceTask(String taskId, int order, boolean standaloneEnabled, Action action) {
        this.taskId = taskId;
        this.order = order;
        this.standaloneEnabled = standaloneEnabled;
        this.action = action;
    }

    public String taskId() { return taskId; }
    public int order() { return order; }
    public boolean standaloneEnabled() { return standaloneEnabled; }
    public void execute(MaintenanceTaskContext context) throws Exception {
        context.checkActive();
        action.execute(context);
        context.checkActive();
    }
}
