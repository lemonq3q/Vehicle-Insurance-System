package com.example.insurancesystem.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 为承保 OCR 提供有界后台执行线程，避免外部模型调用占住 HTTP 请求线程。
 * 队列满时拒绝新任务，由提交接口明确提示用户稍后重试；应用关闭时等待当前任务结束。
 */
@Configuration
public class PolicyOcrTaskConfiguration {
    /**
     * 创建独立于普通请求的 OCR 线程池，限制并发模型调用和排队数量。
     *
     * @return 供承保 OCR 任务服务提交识别工作的线程池
     */
    @Bean("policyOcrTaskExecutor")
    public ThreadPoolTaskExecutor policyOcrTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(40);
        executor.setThreadNamePrefix("policy-ocr-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(600);
        executor.initialize();
        return executor;
    }
}
