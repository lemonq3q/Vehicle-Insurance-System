package com.example.insurancesystem.coordinator;

import com.example.insurancesystem.maintenance.MaintenanceProtocol;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

/**
 * C 调用业务服务内部维护接口的唯一客户端。所有请求使用独立共享密钥；网络异常交由协调器按
 * READY 不可达、任务失败或任务超时分别处理。
 */
@Component
public class ParticipantClient {
    private final RestTemplate restTemplate;
    private final CoordinatorProperties properties;

    public ParticipantClient(RestTemplateBuilder builder, CoordinatorProperties properties) {
        this.properties = properties;
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofMinutes(30)).build();
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
        ResponseEntity<MaintenanceProtocol.Response> response = restTemplate.exchange(
                endpoint.getBaseUrl() + path, HttpMethod.POST, new HttpEntity<>(body, headers),
                MaintenanceProtocol.Response.class);
        return response.getBody();
    }
}
