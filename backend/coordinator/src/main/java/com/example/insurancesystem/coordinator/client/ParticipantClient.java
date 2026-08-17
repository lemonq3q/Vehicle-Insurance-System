package com.example.insurancesystem.coordinator.client;

import com.example.insurancesystem.maintenance.MaintenanceProtocol;
import com.example.insurancesystem.coordinator.config.CoordinatorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

/**
 * C 调用业务服务内部维护接口的唯一客户端。所有请求使用独立共享密钥，并对连接失败、读取超时、
 * HTTP 5xx 和空响应执行有界退避重试；达到阈值后返回 null，由协调器按具体命令决定降级结果。
 */
@Component
public class ParticipantClient {
    private static final Logger log = LoggerFactory.getLogger(ParticipantClient.class);

    private final RestTemplate restTemplate;
    private final CoordinatorProperties properties;

    /**
     * 生产环境使用的依赖注入入口。类中同时保留了测试专用构造函数，因此必须显式标记该构造函数，
     * 避免 Spring 在存在多个候选构造函数时转而寻找不存在的无参构造函数。
     *
     * @param builder Spring Boot 提供的 HTTP 客户端构建器
     * @param properties 协调器的请求超时、重试及服务端点配置
     */
    @Autowired
    public ParticipantClient(RestTemplateBuilder builder, CoordinatorProperties properties) {
        this(builder.setConnectTimeout(Duration.ofSeconds(properties.getRequestConnectTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.getRequestReadTimeoutSeconds())).build(), properties);
    }

    /** 供单元测试注入可控 HTTP 客户端，生产环境使用 RestTemplateBuilder 构造函数。 */
    ParticipantClient(RestTemplate restTemplate, CoordinatorProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public MaintenanceProtocol.Response start(CoordinatorProperties.ServiceEndpoint endpoint,
                                              MaintenanceProtocol.StartRequest request) {
        return post(endpoint, "/internal/maintenance/start", request);
    }
    public MaintenanceProtocol.Response heartbeat(CoordinatorProperties.ServiceEndpoint endpoint,
                                                  MaintenanceProtocol.HeartbeatRequest request) {
        return post(endpoint, "/internal/maintenance/heartbeat", request);
    }
    public MaintenanceProtocol.Response execute(CoordinatorProperties.ServiceEndpoint endpoint,
                                                MaintenanceProtocol.ExecuteRequest request) {
        return post(endpoint, "/internal/maintenance/execute", request);
    }
    public MaintenanceProtocol.Response cancel(CoordinatorProperties.ServiceEndpoint endpoint,
                                               MaintenanceProtocol.CancelRequest request) {
        return post(endpoint, "/internal/maintenance/cancel", request);
    }
    public MaintenanceProtocol.Response finish(CoordinatorProperties.ServiceEndpoint endpoint,
                                               MaintenanceProtocol.FinishRequest request) {
        return post(endpoint, "/internal/maintenance/finish", request);
    }

    private MaintenanceProtocol.Response post(CoordinatorProperties.ServiceEndpoint endpoint,
                                              String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Maintenance-Secret", properties.getInternalSecret());
        HttpEntity<Object> request = new HttpEntity<>(body, headers);
        String url = endpoint.getBaseUrl() + path;
        int maxAttempts = Math.max(1, properties.getRequestMaxAttempts());
        long retryDelayMs = Math.max(0, properties.getRequestRetryDelayMs());

        /*
         * 每次重试仍使用完全相同的请求体和 runId/taskId。接收端必须以这些标识保证幂等，
         * 从而覆盖“服务已经处理成功，但响应在网络中丢失”后 C 再次发送的情况。
         */
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<MaintenanceProtocol.Response> response = restTemplate.exchange(
                        url, HttpMethod.POST, request, MaintenanceProtocol.Response.class);
                if (response.getBody() != null) return response.getBody();
                log.warn("Maintenance request to {} returned an empty response, attempt {}/{}",
                        endpoint.getServiceId(), attempt, maxAttempts);
            } catch (ResourceAccessException | HttpServerErrorException exception) {
                log.warn("Maintenance request to {} failed, attempt {}/{}: {}",
                        endpoint.getServiceId(), attempt, maxAttempts, exception.getMessage());
            }

            if (attempt == maxAttempts || !waitBeforeRetry(retryDelayMs)) break;
            retryDelayMs = nextDelay(retryDelayMs);
        }
        log.error("Maintenance request to {} exhausted {} attempts: {}",
                endpoint.getServiceId(), maxAttempts, path);
        return null;
    }

    /** 按配置倍数增长等待时间，并限制最大延迟，避免故障期间持续高频请求业务服务。 */
    private long nextDelay(long currentDelayMs) {
        double multiplier = Math.max(1.0, properties.getRequestRetryBackoffMultiplier());
        long maxDelay = Math.max(0, properties.getRequestRetryMaxDelayMs());
        double increased = currentDelayMs * multiplier;
        long next = increased >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) increased;
        return maxDelay == 0 ? next : Math.min(next, maxDelay);
    }

    /** 调度线程被中断时立即停止重试并保留中断标志，确保应用关闭不会被退避等待拖延。 */
    private boolean waitBeforeRetry(long delayMs) {
        if (delayMs <= 0) return true;
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
