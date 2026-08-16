package com.example.insurancesystem.system;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;

@Component
/**
 * 协调系统维护窗口与正在执行的 HTTP 请求。volatile 开关用于立即阻止新请求，原子计数记录已经进入业务链的请求，
 * 维护任务在计数归零后才能安全执行数据归档、订阅结算等全局操作。
 */
public class MaintenanceManager {

    // 维护模式开关
    private volatile boolean isMaintenance = false;

    // 当前正在处理的请求数（原子计数）
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    private volatile MaintenanceState state = MaintenanceState.NORMAL;

    private volatile String runId;

    /**
     * 开启维护模式，使最高优先级维护过滤器拒绝此后进入的新请求；已经进入业务链的请求继续执行并由计数器追踪。
     */
    public void startMaintenance() {
        isMaintenance = true;
        state = MaintenanceState.DRAINING;
        System.out.println("【维护模式】已开启，禁止新请求进入");
    }

    /**
     * 关闭维护模式并恢复接收业务请求，通常在全部维护任务成功或异常清理完成后调用。
     */
    public void stopMaintenance() {
        isMaintenance = false;
        state = MaintenanceState.NORMAL;
        runId = null;
        System.out.println("【维护模式】已关闭，恢复正常服务");
    }

    /**
     * 返回当前维护开关，供请求过滤器和调度流程判断是否允许新流量。
     */
    public boolean isMaintenance() {
        return isMaintenance;
    }

    /**
     * 为协调维护周期开启请求排空，并保存 C 下发的运行标识。相同 runId 的重复开始请求保持幂等，
     * 不同周期在本服务仍处于维护时不会覆盖现有上下文。
     */
    public synchronized boolean begin(String requestedRunId) {
        if (isMaintenance && runId != null && !runId.equals(requestedRunId)) return false;
        runId = requestedRunId;
        startMaintenance();
        return true;
    }

    /** 将已经排空业务请求的服务推进到可接收维护任务的 READY 状态。 */
    public synchronized void markReady() {
        if (isMaintenance) state = MaintenanceState.READY;
    }

    /** 服务脱离协调器后进入不可逆的单机维护状态，当前周期不再接收 C 的任务。 */
    public synchronized void markStandalone() {
        if (isMaintenance) state = MaintenanceState.STANDALONE;
    }

    /** 在恢复业务前进入释放阶段，调用方仍需等待所有维护任务安全退出。 */
    public synchronized void markReleasing() {
        if (isMaintenance) state = MaintenanceState.RELEASING;
    }

    public MaintenanceState getState() { return state; }

    public String getRunId() { return runId; }

    public int getActiveRequests() { return activeRequests.get(); }

    /**
     * 请求进入受统计的业务过滤链时原子递增活跃计数，保证并发更新不会丢失。
     */
    public void incrementRequest() {
        activeRequests.incrementAndGet();
    }

    /**
     * 请求无论正常结束还是抛出异常都原子递减活跃计数，因此必须由过滤器 finally 块调用。
     */
    public void decrementRequest() {
        activeRequests.decrementAndGet();
    }

    /**
     * 维护开关开启后轮询活跃请求数，直到所有已经放行的请求退出业务链。
     * 调用线程可被中断，以便应用关闭或调度取消时停止等待而不是永久阻塞。
     */
    public void waitForAllRequestsComplete() throws InterruptedException {
        System.out.println("【维护等待】等待当前请求执行完毕...");
        while (activeRequests.get() > 0) {
            Thread.sleep(500); // 每500ms检查一次
            System.out.println("【等待中】剩余活跃请求数：" + activeRequests.get());
        }
        System.out.println("【维护就绪】所有请求已执行完毕，可以开始维护！");
    }

    /**
     * 在有界时间内等待业务请求排空，避免异常请求令维护准备线程永久阻塞。
     * 返回 false 表示达到期限，调用方应进入降级路径而不能假定请求已经安全结束。
     */
    public boolean waitForAllRequestsComplete(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (activeRequests.get() > 0) {
            if (System.nanoTime() >= deadline) return false;
            Thread.sleep(200);
        }
        return true;
    }
}
