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
    private final String deleteUrl;

    public MonitorReminderClient(RestTemplateBuilder builder, MaintenanceParticipantProperties maintenance,
            @Value("${saas.reminder.monitor-merge-url:http://localhost:8083/internal/reminders/merge}") String mergeUrl,
            @Value("${saas.reminder.monitor-delete-url:http://localhost:8083/internal/reminders/delete}") String deleteUrl) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(30)).build();
        this.maintenance = maintenance;
        this.mergeUrl = mergeUrl;
        this.deleteUrl = deleteUrl;
    }

    /** 调用监控合并接口并严格校验统一响应；失败会使本次维护任务失败，由协调框架在后续运行中重试。 */
    public void merge(Map<String, Object> request) {
        post(mergeUrl, request, "监控提醒合并");
    }

    /**
     * 请求监控端删除已经恢复的风险提醒。调用每日重复执行时接口保持幂等，确保此前监控服务不可用
     * 导致的残留提醒可以在下一次维护中继续清理。
     */
    public void delete(Long enterpriseId, String reminderKey) {
        post(deleteUrl, Map.of("enterpriseId", enterpriseId, "reminderKey", reminderKey), "监控提醒清理");
    }

    /** 统一发送带内部密钥的提醒请求并校验响应，网络失败交由每日维护任务后续重试。 */
    private void post(String url, Object request, String action) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Maintenance-Secret", maintenance.getInternalSecret());
        try {
            ResponseResult<?> response = restTemplate.postForObject(url, new HttpEntity<>(request, headers), ResponseResult.class);
            if (response == null || response.getCode() == null || response.getCode() != 200) {
                throw new IllegalStateException(response == null ? "监控提醒服务无有效响应" : response.getMsg());
            }
        } catch (RestClientException exception) {
            throw new IllegalStateException(action + "服务暂时不可用", exception);
        }
    }
}
