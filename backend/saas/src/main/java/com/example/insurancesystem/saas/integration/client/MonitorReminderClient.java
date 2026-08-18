package com.example.insurancesystem.saas.integration.client;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.maintenance.MaintenanceParticipantProperties;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * SaaS 调用监控后端内部提醒接口的专用客户端。每次候选提醒均发送当前阶段，即使企业侧已存在同阶段记录，
 * 监控端仍可利用自己的唯一键补齐上一次网络失败的数据，从而以幂等重试实现跨服务最终一致性。
 */
@Component
public class MonitorReminderClient {
    private final RestTemplate restTemplate;
    private final MaintenanceParticipantProperties maintenance;
    private final String mergeUrl;

    public MonitorReminderClient(RestTemplateBuilder builder, MaintenanceParticipantProperties maintenance,
            @Value("${saas.reminder.monitor-merge-url:http://localhost:8083/internal/reminders/merge}") String mergeUrl) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(30)).build();
        this.maintenance = maintenance;
        this.mergeUrl = mergeUrl;
    }

    /** 调用监控合并接口并严格校验统一响应；失败会使本次维护任务失败，由协调框架在后续运行中重试。 */
    public void merge(Map<String, Object> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Maintenance-Secret", maintenance.getInternalSecret());
        try {
            ResponseResult<?> response = restTemplate.postForObject(mergeUrl, new HttpEntity<>(request, headers), ResponseResult.class);
            if (response == null || response.getCode() == null || response.getCode() != 200) {
                throw new IllegalStateException(response == null ? "监控提醒服务无有效响应" : response.getMsg());
            }
        } catch (RestClientException exception) {
            throw new IllegalStateException("暂时无法连接监控提醒服务", exception);
        }
    }
}
