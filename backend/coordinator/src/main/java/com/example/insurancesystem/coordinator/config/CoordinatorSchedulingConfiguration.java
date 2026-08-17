package com.example.insurancesystem.coordinator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 为 C 提供多线程定时调度器。维护主流程会持续较长时间，心跳必须在独立线程继续运行，
 * 否则 Spring 默认单线程调度器会令参与服务的租约在维护期间失效。
 */
@Configuration
public class CoordinatorSchedulingConfiguration {
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("maintenance-coordinator-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }
}
