package com.example.insurancesystem.coordinator.service;

import com.example.insurancesystem.coordinator.client.ParticipantClient;
import com.example.insurancesystem.coordinator.config.CoordinatorProperties;
import com.example.insurancesystem.coordinator.domain.CoordinatorTaskStatus;
import com.example.insurancesystem.maintenance.MaintenanceProtocol;
import com.example.insurancesystem.maintenance.MaintenanceTaskResult;
import com.example.insurancesystem.system.MaintenanceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 独立协调服务 C 的核心编排器。一次运行内先并行准备所有服务，再按阶段串行、组串行、组内并行的规则
 * 下发任务；任务失败只通过 dependsOn 影响下游，其他任务继续执行。当前实现保存本次运行快照供状态接口查询。
 */
@Service
public class MaintenanceCoordinatorService {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceCoordinatorService.class);

    private final CoordinatorProperties properties;
    private final ParticipantClient client;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, CoordinatorTaskStatus> taskStatuses = new ConcurrentHashMap<>();
    private final Set<String> readyServices = ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> serviceLeaseDeadlines = new ConcurrentHashMap<>();
    private volatile String activeRunId;
    private volatile String phase = "IDLE";

    public MaintenanceCoordinatorService(CoordinatorProperties properties, ParticipantClient client) {
        this.properties = properties;
        this.client = client;
    }

    /** 每日由 C 唯一发起维护；A、B 的本地定时器只在未收到通知时执行单机兜底。 */
    @Scheduled(cron = "${maintenance.coordinator.cron:0 0 4 * * ?}",
            zone = "${maintenance.coordinator.zone:Asia/Shanghai}")
    public void scheduledRun() { runNow(); }

    /**
     * 创建并执行一个维护周期。AtomicBoolean 充当单实例锁；生产多实例部署时应在外部保证 C 单实例，
     * 或将此锁替换为共享存储中的分布式锁。
     */
    public boolean runNow() {
        if (!properties.isEnabled() || !running.compareAndSet(false, true)) return false;
        try {
            validatePlan();
        } catch (RuntimeException exception) {
            running.set(false);
            phase = "IDLE";
            throw exception;
        }
        activeRunId = LocalDateTime.now(ZoneId.of(properties.getZone())).toString() + "-" + UUID.randomUUID();
        LocalDate businessDate = LocalDate.now(ZoneId.of(properties.getZone())).minusDays(1);
        taskStatuses.clear();
        readyServices.clear();
        serviceLeaseDeadlines.clear();
        allTasks().forEach(task -> taskStatuses.put(task.getTaskId(), CoordinatorTaskStatus.PENDING));
        try {
            phase = "PREPARING";
            prepareServices(businessDate);
            phase = "EXECUTING";
            for (CoordinatorProperties.Stage stage : sortedStages()) {
                executeStage(stage, businessDate);
            }
        } catch (Exception exception) {
            log.error("Maintenance run {} failed unexpectedly", activeRunId, exception);
        } finally {
            phase = "CLOSING";
            finishServices();
            phase = "ENDED";
            running.set(false);
        }
        return true;
    }

    /** 在整个准备和执行期间持续续租；单个服务心跳失败只会令后续任务的在线检查失败。 */
    @Scheduled(fixedDelayString = "${maintenance.coordinator.heartbeat-interval-ms:60000}")
    public void heartbeat() {
        String runId = activeRunId;
        if (!running.get() || runId == null) return;
        MaintenanceProtocol.HeartbeatRequest request = new MaintenanceProtocol.HeartbeatRequest();
        request.runId = runId;
        for (CoordinatorProperties.ServiceEndpoint endpoint : properties.getServices()) {
            if (!readyServices.contains(endpoint.getServiceId())) continue;
            try {
                MaintenanceProtocol.Response response = client.heartbeat(endpoint, request);
                if (response == null || !response.accepted || response.state != MaintenanceState.READY) {
                    removeIfLeaseExpired(endpoint.getServiceId());
                } else {
                    serviceLeaseDeadlines.put(endpoint.getServiceId(),
                            Instant.now().plusSeconds(properties.getLeaseTimeoutSeconds()));
                }
            } catch (Exception exception) {
                log.warn("Heartbeat to {} failed", endpoint.getServiceId(), exception);
                removeIfLeaseExpired(endpoint.getServiceId());
            }
        }
    }

    /** 并行发送 START，超过准备期限或未返回 READY 的服务不再加入当前运行。 */
    private void prepareServices(LocalDate businessDate) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        Instant prepareDeadline = Instant.now().plusSeconds(properties.getPrepareTimeoutSeconds());
        for (CoordinatorProperties.ServiceEndpoint endpoint : properties.getServices()) {
            futures.add(CompletableFuture.runAsync(() -> {
                MaintenanceProtocol.StartRequest request = new MaintenanceProtocol.StartRequest();
                request.runId = activeRunId;
                request.businessDate = businessDate;
                request.leaseTimeoutSeconds = properties.getLeaseTimeoutSeconds();
                try {
                    MaintenanceProtocol.Response response = client.start(endpoint, request);
                    if (response != null && response.accepted && response.state == MaintenanceState.READY
                            && Instant.now().isBefore(prepareDeadline) && "PREPARING".equals(phase)) {
                        readyServices.add(endpoint.getServiceId());
                        serviceLeaseDeadlines.put(endpoint.getServiceId(),
                                Instant.now().plusSeconds(properties.getLeaseTimeoutSeconds()));
                    }
                } catch (Exception exception) {
                    log.warn("Service {} did not become ready", endpoint.getServiceId(), exception);
                }
            }, executor).orTimeout(properties.getPrepareTimeoutSeconds(), TimeUnit.SECONDS));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).exceptionally(error -> null).join();
    }

    private void removeIfLeaseExpired(String serviceId) {
        Instant deadline = serviceLeaseDeadlines.getOrDefault(serviceId, Instant.EPOCH);
        if (Instant.now().isAfter(deadline)) readyServices.remove(serviceId);
    }

    /**
     * 对组内任务逐个判断依赖和服务在线状态，仅下发满足条件的任务。任务通过独立 future 并行运行，
     * 单项失败或超时不会取消同组其他任务。
     */
    private void executeGroup(CoordinatorProperties.Group group, LocalDate businessDate) {
        List<CompletableFuture<Void>> executions = new ArrayList<>();
        for (CoordinatorProperties.Task task : group.getTasks()) {
            if (!dependenciesSucceeded(task) || !readyServices.containsAll(task.getRequiredServices())) {
                taskStatuses.put(task.getTaskId(), CoordinatorTaskStatus.SKIPPED);
                continue;
            }
            taskStatuses.put(task.getTaskId(), CoordinatorTaskStatus.DISPATCHED);
            CompletableFuture<Void> execution = CompletableFuture
                    .supplyAsync(() -> executeTask(task, businessDate), executor)
                    .orTimeout(task.getTimeoutSeconds(), TimeUnit.SECONDS)
                    .handle((status, error) -> {
                        if (error == null) {
                            taskStatuses.put(task.getTaskId(), status);
                        } else {
                            taskStatuses.put(task.getTaskId(), CoordinatorTaskStatus.TIMED_OUT);
                            requestCancel(task);
                        }
                        return null;
                    });
            executions.add(execution);
        }
        CompletableFuture.allOf(executions.toArray(new CompletableFuture[0])).join();
    }

    /**
     * 同一阶段的组保持互斥，但没有固定业务顺序。优先选择其外部依赖已经进入终态的组，
     * 因而同阶段跨组依赖不会因 YAML 书写顺序而被错误跳过。
     */
    private void executeStage(CoordinatorProperties.Stage stage, LocalDate businessDate) {
        List<CoordinatorProperties.Group> remaining = new ArrayList<>(stage.getGroups());
        while (!remaining.isEmpty()) {
            CoordinatorProperties.Group selected = remaining.stream()
                    .filter(this::dependenciesAreDecidable).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No executable group in stage " + stage.getStage() + "; dependency points to a later stage"));
            executeGroup(selected, businessDate);
            remaining.remove(selected);
        }
    }

    private boolean dependenciesAreDecidable(CoordinatorProperties.Group group) {
        return group.getTasks().stream().flatMap(task -> task.getDependsOn().stream())
                .allMatch(dependency -> taskStatuses.get(dependency) != CoordinatorTaskStatus.PENDING
                        && taskStatuses.get(dependency) != CoordinatorTaskStatus.DISPATCHED);
    }

    private CoordinatorTaskStatus executeTask(CoordinatorProperties.Task task, LocalDate businessDate) {
        CoordinatorProperties.ServiceEndpoint endpoint = endpoint(task.getTargetService());
        MaintenanceProtocol.ExecuteRequest request = new MaintenanceProtocol.ExecuteRequest();
        request.runId = activeRunId;
        request.taskId = task.getTaskId();
        request.businessDate = businessDate;
        request.deadlineEpochMillis = Instant.now().plusSeconds(task.getTimeoutSeconds()).toEpochMilli();
        try {
            MaintenanceProtocol.Response response = client.execute(endpoint, request);
            if (response != null && response.accepted && response.taskResult == MaintenanceTaskResult.SUCCEEDED) {
                return CoordinatorTaskStatus.SUCCEEDED;
            }
            if (response != null && response.taskResult == MaintenanceTaskResult.CANCELLED) {
                return CoordinatorTaskStatus.CANCELLED;
            }
            return CoordinatorTaskStatus.FAILED;
        } catch (Exception exception) {
            log.error("Task {} execution failed", task.getTaskId(), exception);
            return CoordinatorTaskStatus.FAILED;
        }
    }

    /** 超时通知只设置目标任务的协作式取消标记，发送失败不会阻塞其他任务调度。 */
    private void requestCancel(CoordinatorProperties.Task task) {
        MaintenanceProtocol.CancelRequest request = new MaintenanceProtocol.CancelRequest();
        request.runId = activeRunId;
        request.taskId = task.getTaskId();
        try { client.cancel(endpoint(task.getTargetService()), request); }
        catch (Exception exception) { log.warn("Cancellation request for {} was not delivered", task.getTaskId()); }
    }

    /** 所有计划任务进入终态后通知仍在线的参与服务释放；进入单机模式的服务会自行忽略。 */
    private void finishServices() {
        if (activeRunId == null) return;
        MaintenanceProtocol.FinishRequest request = new MaintenanceProtocol.FinishRequest();
        request.runId = activeRunId;
        Set<String> pending = new HashSet<>(readyServices);
        Instant deadline = Instant.now().plusSeconds(properties.getFinishTimeoutSeconds());
        while (!pending.isEmpty() && Instant.now().isBefore(deadline)) {
            for (CoordinatorProperties.ServiceEndpoint endpoint : properties.getServices()) {
                if (!pending.contains(endpoint.getServiceId())) continue;
                try {
                    MaintenanceProtocol.Response response = client.finish(endpoint, request);
                    if (response != null && response.accepted) pending.remove(endpoint.getServiceId());
                } catch (Exception exception) {
                    log.warn("Finish notification to {} failed", endpoint.getServiceId());
                }
            }
            if (!pending.isEmpty()) {
                try { Thread.sleep(1000); }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (!pending.isEmpty()) log.error("Services did not confirm safe release: {}", pending);
    }

    private boolean dependenciesSucceeded(CoordinatorProperties.Task task) {
        return task.getDependsOn().stream()
                .allMatch(id -> taskStatuses.get(id) == CoordinatorTaskStatus.SUCCEEDED);
    }

    private CoordinatorProperties.ServiceEndpoint endpoint(String serviceId) {
        return properties.getServices().stream().filter(item -> item.getServiceId().equals(serviceId))
                .findFirst().orElseThrow(() -> new IllegalStateException("Unknown service: " + serviceId));
    }

    /** 启动前验证编号、服务、同组依赖和依赖环，避免运行到一半才发现计划不可执行。 */
    private void validatePlan() {
        List<CoordinatorProperties.Task> tasks = allTasks();
        Map<String, CoordinatorProperties.Task> byId = tasks.stream().collect(Collectors.toMap(
                CoordinatorProperties.Task::getTaskId, Function.identity(),
                (left, right) -> { throw new IllegalStateException("Duplicate taskId: " + left.getTaskId()); }));
        Set<String> serviceIds = properties.getServices().stream()
                .map(CoordinatorProperties.ServiceEndpoint::getServiceId).collect(Collectors.toSet());
        for (CoordinatorProperties.Stage stage : properties.getStages()) {
            for (CoordinatorProperties.Group group : stage.getGroups()) {
                Set<String> sameGroup = group.getTasks().stream()
                        .map(CoordinatorProperties.Task::getTaskId).collect(Collectors.toSet());
                for (CoordinatorProperties.Task task : group.getTasks()) {
                    if (!serviceIds.contains(task.getTargetService())
                            || !serviceIds.containsAll(task.getRequiredServices())) {
                        throw new IllegalStateException("Unknown service in task " + task.getTaskId());
                    }
                    for (String dependency : task.getDependsOn()) {
                        if (!byId.containsKey(dependency)) throw new IllegalStateException(
                                "Unknown dependency " + dependency + " for " + task.getTaskId());
                        if (sameGroup.contains(dependency)) throw new IllegalStateException(
                                "Dependent tasks cannot share a parallel group: " + task.getTaskId());
                    }
                }
            }
        }
        for (String taskId : byId.keySet()) detectCycle(taskId, byId, new HashSet<>(), new HashSet<>());
    }

    private void detectCycle(String taskId, Map<String, CoordinatorProperties.Task> tasks,
                             Set<String> visiting, Set<String> visited) {
        if (visited.contains(taskId)) return;
        if (!visiting.add(taskId)) throw new IllegalStateException("Circular maintenance dependency: " + taskId);
        for (String dependency : tasks.get(taskId).getDependsOn()) detectCycle(dependency, tasks, visiting, visited);
        visiting.remove(taskId);
        visited.add(taskId);
    }

    private List<CoordinatorProperties.Stage> sortedStages() {
        return properties.getStages().stream().sorted(Comparator.comparingInt(CoordinatorProperties.Stage::getStage))
                .collect(Collectors.toList());
    }

    private List<CoordinatorProperties.Task> allTasks() {
        return properties.getStages().stream().flatMap(stage -> stage.getGroups().stream())
                .flatMap(group -> group.getTasks().stream()).collect(Collectors.toList());
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", activeRunId);
        result.put("phase", phase);
        result.put("running", running.get());
        result.put("readyServices", new TreeSet<>(readyServices));
        result.put("tasks", new TreeMap<>(taskStatuses));
        return result;
    }

    @PreDestroy
    public void shutdown() { executor.shutdownNow(); }
}
