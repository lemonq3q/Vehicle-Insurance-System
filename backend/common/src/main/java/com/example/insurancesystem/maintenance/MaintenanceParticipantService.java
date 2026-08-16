package com.example.insurancesystem.maintenance;

import com.example.insurancesystem.system.MaintenanceManager;
import com.example.insurancesystem.system.MaintenanceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 实现业务服务参与端状态机、任务幂等、租约监控和单机兜底。联机任务由 HTTP 调用线程执行，
 * 单机流程由独立单线程执行器串行运行；进入 STANDALONE 后当前周期不可重新加入 C。
 */
@Service
public class MaintenanceParticipantService {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceParticipantService.class);

    private final MaintenanceManager manager;
    private final MaintenanceTaskRegistry registry;
    private final MaintenanceParticipantProperties properties;
    private final Map<String, MaintenanceTaskResult> history = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> activeCancellations = new ConcurrentHashMap<>();
    private final ExecutorService standaloneExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean standaloneRunning = new AtomicBoolean(false);
    private volatile Instant leaseDeadline = Instant.EPOCH;
    private volatile LocalDate businessDate;
    private volatile LocalDate lastMaintenanceDate;

    public MaintenanceParticipantService(MaintenanceManager manager, MaintenanceTaskRegistry registry,
                                         MaintenanceParticipantProperties properties) {
        this.manager = manager;
        this.registry = registry;
        this.properties = properties;
    }

    /** 接收 C 的开始通知，排空业务请求并在期限内进入 READY；重复请求保持幂等。 */
    public synchronized MaintenanceProtocol.Response start(MaintenanceProtocol.StartRequest request) {
        if (!properties.isEnabled() || request == null || request.runId == null) {
            return response(false, "invalid or disabled");
        }
        if (manager.getState() == MaintenanceState.STANDALONE) {
            return response(false, "service already entered standalone mode");
        }
        if (request.runId.equals(manager.getRunId()) && manager.getState() == MaintenanceState.READY) {
            renewLease(request.leaseTimeoutSeconds);
            return response(true, "already ready");
        }
        if (!manager.begin(request.runId)) return response(false, "another run is active");
        businessDate = request.businessDate;
        lastMaintenanceDate = LocalDate.now(ZoneId.of(properties.getZone()));
        renewLease(request.leaseTimeoutSeconds);
        try {
            boolean drained = manager.waitForAllRequestsComplete(Duration.ofSeconds(properties.getDrainTimeoutSeconds()));
            if (!drained) return response(false, "business request drain timed out");
            manager.markReady();
            return response(true, "ready");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return response(false, "drain interrupted");
        }
    }

    /** C 的有效心跳只在联机状态续租，不能把已经降级的服务拉回当前周期。 */
    public synchronized MaintenanceProtocol.Response heartbeat(String runId) {
        if (!isCurrentRun(runId) || manager.getState() == MaintenanceState.STANDALONE) {
            return response(false, "run is not participating online");
        }
        renewLease(properties.getLeaseTimeoutSeconds());
        return response(true, "lease renewed");
    }

    /**
     * 幂等执行 C 指定的任务。重复的 runId+taskId 返回已有结果；截止时间和取消标记通过上下文交给业务代码检查。
     */
    public MaintenanceProtocol.Response execute(MaintenanceProtocol.ExecuteRequest request) {
        if (request == null || !isCurrentRun(request.runId) || manager.getState() != MaintenanceState.READY) {
            return response(false, "service is not ready for this run");
        }
        String key = key(request.runId, request.taskId);
        MaintenanceTaskResult previous = history.get(key);
        if (previous != null) return taskResponse(true, "duplicate task", previous);
        MaintenanceTask task = registry.find(request.taskId).orElse(null);
        if (task == null) return taskResponse(false, "unknown task: " + request.taskId, MaintenanceTaskResult.FAILED);
        AtomicBoolean cancellation = new AtomicBoolean(false);
        if (activeCancellations.putIfAbsent(key, cancellation) != null) {
            return response(false, "task is already executing");
        }
        renewLease(properties.getLeaseTimeoutSeconds());
        try {
            MaintenanceTaskContext context = new MaintenanceTaskContext(
                    request.runId, request.taskId,
                    request.businessDate == null ? businessDate : request.businessDate,
                    Instant.ofEpochMilli(request.deadlineEpochMillis), cancellation);
            context.checkActive();
            task.execute(context);
            context.checkActive();
            history.put(key, MaintenanceTaskResult.SUCCEEDED);
            return taskResponse(true, "task succeeded", MaintenanceTaskResult.SUCCEEDED);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            history.put(key, MaintenanceTaskResult.CANCELLED);
            return taskResponse(false, exception.getMessage(), MaintenanceTaskResult.CANCELLED);
        } catch (Exception exception) {
            log.error("Maintenance task {} failed for run {}", request.taskId, request.runId, exception);
            history.put(key, MaintenanceTaskResult.FAILED);
            return taskResponse(false, exception.getMessage(), MaintenanceTaskResult.FAILED);
        } finally {
            activeCancellations.remove(key);
        }
    }

    /** 超时取消是协作式信号，只影响指定任务，不会强制终止线程或同组任务。 */
    public MaintenanceProtocol.Response requestCancel(MaintenanceProtocol.CancelRequest request) {
        if (request == null || !isCurrentRun(request.runId)) return response(false, "run mismatch");
        AtomicBoolean cancellation = activeCancellations.get(key(request.runId, request.taskId));
        if (cancellation == null) return response(false, "task is not active");
        cancellation.set(true);
        return response(true, "cancellation requested");
    }

    /** 只有仍处于联机 READY 的服务响应结束通知；STANDALONE 按自己的节奏完成全部本地流程。 */
    public synchronized MaintenanceProtocol.Response finish(String runId) {
        if (!isCurrentRun(runId) || manager.getState() != MaintenanceState.READY) {
            return response(false, "finish ignored outside online ready state");
        }
        if (!activeCancellations.isEmpty()) return response(false, "maintenance tasks are still active");
        manager.markReleasing();
        manager.stopMaintenance();
        return response(true, "released");
    }

    /** 租约过期后触发不可逆单机降级；由定时监控周期调用。 */
    public void checkLease() {
        if ((manager.getState() == MaintenanceState.READY || manager.getState() == MaintenanceState.DRAINING)
                && Instant.now().isAfter(leaseDeadline)) {
            startStandalone(manager.getRunId(), businessDate);
        }
    }

    /** 本地维护窗口未收到 C 的 START 时，从无 runId 的路径进入单机维护。 */
    public void startFallbackIfNeeded() {
        LocalDate today = LocalDate.now(ZoneId.of(properties.getZone()));
        if (properties.isEnabled() && manager.getState() == MaintenanceState.NORMAL
                && !today.equals(lastMaintenanceDate)) {
            manager.startMaintenance();
            try {
                if (!manager.waitForAllRequestsComplete(Duration.ofSeconds(properties.getDrainTimeoutSeconds()))) {
                    log.error("Standalone maintenance aborted because business requests did not drain");
                    manager.stopMaintenance();
                    return;
                }
                startStandalone(null, today.minusDays(1));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                manager.stopMaintenance();
            }
        }
    }

    /**
     * 按代码注册顺序执行本地任务。有 runId 时跳过本周期已有结果的任务；无 runId 时依次执行全部任务。
     * 进入后忽略 C 的所有控制消息，任务结束后固定等待配置时长再恢复业务。
     */
    private void startStandalone(String runId, LocalDate date) {
        if (!standaloneRunning.compareAndSet(false, true)) return;
        manager.markStandalone();
        standaloneExecutor.submit(() -> {
            try {
                /*
                 * DRAINING 期间失联时仍可能存在已进入业务链的请求。单机维护必须继续阻止流量并等待这些请求自然结束，
                 * 不能为了满足退出计时而与业务事务并发修改本地数据库。
                 */
                while (manager.getActiveRequests() > 0) Thread.sleep(200);
                while (!activeCancellations.isEmpty()) Thread.sleep(200);
                for (MaintenanceTask task : registry.standaloneTasks()) {
                    String historyKey = runId == null ? null : key(runId, task.taskId());
                    if (historyKey != null && history.containsKey(historyKey)) continue;
                    AtomicBoolean cancellation = new AtomicBoolean(false);
                    MaintenanceTaskContext context = new MaintenanceTaskContext(
                            runId, task.taskId(), date, Instant.now().plus(Duration.ofDays(1)), cancellation);
                    try {
                        task.execute(context);
                        if (historyKey != null) history.put(historyKey, MaintenanceTaskResult.SUCCEEDED);
                    } catch (Exception exception) {
                        log.error("Standalone maintenance task {} failed", task.taskId(), exception);
                        if (historyKey != null) history.put(historyKey, MaintenanceTaskResult.FAILED);
                    }
                }
                Thread.sleep(Duration.ofSeconds(properties.getStandaloneExitDelaySeconds()).toMillis());
                manager.markReleasing();
                manager.stopMaintenance();
                lastMaintenanceDate = LocalDate.now(ZoneId.of(properties.getZone()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.error("Standalone maintenance interrupted; service remains in maintenance mode", exception);
            } finally {
                standaloneRunning.set(false);
            }
        });
    }

    public MaintenanceProtocol.Response status() { return response(true, "ok"); }

    private boolean isCurrentRun(String requestedRunId) {
        return requestedRunId != null && requestedRunId.equals(manager.getRunId());
    }

    private void renewLease(long requestedSeconds) {
        long seconds = requestedSeconds > 0 ? requestedSeconds : properties.getLeaseTimeoutSeconds();
        leaseDeadline = Instant.now().plusSeconds(seconds);
    }

    private String key(String runId, String taskId) { return runId + ":" + taskId; }

    private MaintenanceProtocol.Response response(boolean accepted, String message) {
        return MaintenanceProtocol.Response.of(accepted, message, manager.getState());
    }

    private MaintenanceProtocol.Response taskResponse(boolean accepted, String message, MaintenanceTaskResult result) {
        MaintenanceProtocol.Response response = response(accepted, message);
        response.taskResult = result;
        return response;
    }

    @PreDestroy
    public void shutdown() { standaloneExecutor.shutdownNow(); }
}
