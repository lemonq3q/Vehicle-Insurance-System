package com.example.insurancesystem.system;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MaintenanceManager {

    // 维护模式开关
    private volatile boolean isMaintenance = false;

    // 当前正在处理的请求数（原子计数）
    private final AtomicInteger activeRequests = new AtomicInteger(0);

    // ===================== 公开方法 =====================

    /**
     * 开启维护模式：阻塞新请求
     */
    public void startMaintenance() {
        isMaintenance = true;
        System.out.println("【维护模式】已开启，禁止新请求进入");
    }

    /**
     * 关闭维护模式：恢复接收请求
     */
    public void stopMaintenance() {
        isMaintenance = false;
        System.out.println("【维护模式】已关闭，恢复正常服务");
    }

    /**
     * 是否处于维护中
     */
    public boolean isMaintenance() {
        return isMaintenance;
    }

    // ===================== 请求计数 =====================
    // 进入请求 +1
    public void incrementRequest() {
        activeRequests.incrementAndGet();
    }

    // 离开请求 -1
    public void decrementRequest() {
        activeRequests.decrementAndGet();
    }

    /**
     * 等待所有正在执行的请求全部完成
     */
    public void waitForAllRequestsComplete() throws InterruptedException {
        System.out.println("【维护等待】等待当前请求执行完毕...");
        while (activeRequests.get() > 0) {
            Thread.sleep(500); // 每500ms检查一次
            System.out.println("【等待中】剩余活跃请求数：" + activeRequests.get());
        }
        System.out.println("【维护就绪】所有请求已执行完毕，可以开始维护！");
    }
}