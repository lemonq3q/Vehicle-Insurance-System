package com.example.insurancesystem.maintenance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单次任务执行上下文，向业务任务提供维护周期、业务日期、截止时间与协作式取消信号。
 * 任务应在批次边界、远程调用前后和事务提交前调用 checkActive，确保超时后安全回滚。
 */
public class MaintenanceTaskContext {
    private final String runId;
    private final String taskId;
    private final LocalDate businessDate;
    private final Instant deadline;
    private final AtomicBoolean cancelled;

    public MaintenanceTaskContext(String runId, String taskId, LocalDate businessDate,
                                  Instant deadline, AtomicBoolean cancelled) {
        this.runId = runId;
        this.taskId = taskId;
        this.businessDate = businessDate;
        this.deadline = deadline;
        this.cancelled = cancelled;
    }

    public String getRunId() { return runId; }
    public String getTaskId() { return taskId; }
    public LocalDate getBusinessDate() { return businessDate; }
    public Instant getDeadline() { return deadline; }

    /** 在安全检查点阻止已取消或超过截止时间的任务继续写入。 */
    public void checkActive() throws InterruptedException {
        if (cancelled.get() || Instant.now().isAfter(deadline)) {
            throw new InterruptedException("Maintenance task cancelled or timed out: " + taskId);
        }
    }
}
