package com.example.insurancesystem.coordinator.participant;

import com.example.insurancesystem.maintenance.MaintenanceParticipantProperties;
import com.example.insurancesystem.maintenance.MaintenanceParticipantService;
import com.example.insurancesystem.maintenance.MaintenanceProtocol;
import com.example.insurancesystem.maintenance.MaintenanceTask;
import com.example.insurancesystem.maintenance.MaintenanceTaskContext;
import com.example.insurancesystem.maintenance.MaintenanceTaskRegistry;
import com.example.insurancesystem.maintenance.MaintenanceTaskResult;
import com.example.insurancesystem.system.MaintenanceManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证参与端面对协调器重试时的幂等边界。测试覆盖首次请求仍在执行时的并发重复请求，
 * 以及 FINISH 响应丢失后的重复通知，防止重试机制导致数据库维护被重复执行或错误拒绝。
 */
class MaintenanceParticipantServiceIdempotencyTest {
    private MaintenanceParticipantService participant;
    private ExecutorService executor;

    /**
     * 释放测试创建的维护参与端及并发线程，避免后台任务影响后续测试。
     */
    @AfterEach
    void tearDown() {
        if (participant != null) participant.shutdown();
        if (executor != null) executor.shutdownNow();
    }

    /**
     * 模拟首次 EXECUTE 已到达服务、但协调器因未及时收到响应而重试的情况。
     * 两个请求应共享同一次任务执行，并最终拿到相同的成功结果。
     */
    @Test
    void duplicateExecuteWaitsForTheSameExecutionResult() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch taskEntered = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        MaintenanceTask task = blockingTask(executions, taskEntered, releaseTask);
        participant = participantWith(task);
        startRun("run-1");

        MaintenanceProtocol.ExecuteRequest request = executeRequest("run-1", "task-1");
        executor = Executors.newFixedThreadPool(2);
        Future<MaintenanceProtocol.Response> first = executor.submit(() -> participant.execute(request));
        assertTrue(taskEntered.await(2, TimeUnit.SECONDS));
        Future<MaintenanceProtocol.Response> retry = executor.submit(() -> participant.execute(request));

        releaseTask.countDown();
        MaintenanceProtocol.Response firstResponse = first.get(2, TimeUnit.SECONDS);
        MaintenanceProtocol.Response retryResponse = retry.get(2, TimeUnit.SECONDS);

        assertEquals(1, executions.get());
        assertEquals(MaintenanceTaskResult.SUCCEEDED, firstResponse.taskResult);
        assertEquals(MaintenanceTaskResult.SUCCEEDED, retryResponse.taskResult);
        assertTrue(firstResponse.accepted);
        assertTrue(retryResponse.accepted);
    }

    /**
     * 模拟 FINISH 已生效但响应在网络中丢失的情况。相同 runId 的重试必须返回成功，
     * 不能因为服务已经恢复 NORMAL 且当前 runId 被清理而误报周期不匹配。
     */
    @Test
    void duplicateFinishIsAcceptedAfterRunWasReleased() {
        participant = participantWith();
        startRun("run-2");

        MaintenanceProtocol.Response first = participant.finish("run-2");
        MaintenanceProtocol.Response retry = participant.finish("run-2");

        assertTrue(first.accepted);
        assertTrue(retry.accepted);
    }

    private MaintenanceParticipantService participantWith(MaintenanceTask... tasks) {
        MaintenanceParticipantProperties properties = new MaintenanceParticipantProperties();
        properties.setDrainTimeoutSeconds(1);
        properties.setLeaseTimeoutSeconds(30);
        return new MaintenanceParticipantService(
                new MaintenanceManager(), new MaintenanceTaskRegistry(List.of(tasks)), properties);
    }

    private void startRun(String runId) {
        MaintenanceProtocol.StartRequest request = new MaintenanceProtocol.StartRequest();
        request.runId = runId;
        request.businessDate = LocalDate.now();
        request.leaseTimeoutSeconds = 30;
        assertTrue(participant.start(request).accepted);
    }

    private MaintenanceProtocol.ExecuteRequest executeRequest(String runId, String taskId) {
        MaintenanceProtocol.ExecuteRequest request = new MaintenanceProtocol.ExecuteRequest();
        request.runId = runId;
        request.taskId = taskId;
        request.businessDate = LocalDate.now();
        request.deadlineEpochMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
        return request;
    }

    private MaintenanceTask blockingTask(AtomicInteger executions, CountDownLatch entered, CountDownLatch release) {
        return new MaintenanceTask() {
            @Override public String taskId() { return "task-1"; }
            @Override public int order() { return 1; }
            @Override public boolean standaloneEnabled() { return true; }

            /**
             * 阻塞首次执行，为并发重试制造稳定窗口；计数用于证明业务维护逻辑只运行一次。
             */
            @Override
            public void execute(MaintenanceTaskContext context) throws Exception {
                executions.incrementAndGet();
                entered.countDown();
                release.await(2, TimeUnit.SECONDS);
            }
        };
    }
}
