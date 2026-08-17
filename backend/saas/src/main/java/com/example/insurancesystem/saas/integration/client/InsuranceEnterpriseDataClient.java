package com.example.insurancesystem.saas.integration.client;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.maintenance.MaintenanceParticipantProperties;
import com.example.insurancesystem.saas.config.EnterpriseDataRetentionProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * SaaS 后端调用车险内部企业资料清理接口的专用客户端。
 * 客户端使用维护参与端共享密钥，不携带终端用户 JWT；每次请求只传递企业主键，不允许调用方传入表名、
 * 文件路径或 SQL 范围。接口与服务均具备按企业重复执行的幂等语义，因此协调任务失败后可在下一周期重试。
 */
@Component
public class InsuranceEnterpriseDataClient {
    private final RestTemplate restTemplate;
    private final EnterpriseDataRetentionProperties retentionProperties;
    private final MaintenanceParticipantProperties maintenanceProperties;

    /**
     * @param builder Spring Boot HTTP 客户端构建器
     * @param retentionProperties 清理地址及调用超时配置
     * @param maintenanceProperties SaaS 与车险服务共同配置的维护共享密钥
     */
    public InsuranceEnterpriseDataClient(
            RestTemplateBuilder builder,
            EnterpriseDataRetentionProperties retentionProperties,
            MaintenanceParticipantProperties maintenanceProperties) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(retentionProperties.getConnectTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(retentionProperties.getReadTimeoutSeconds()))
                .build();
        this.retentionProperties = retentionProperties;
        this.maintenanceProperties = maintenanceProperties;
    }

    /**
     * 请求车险后端物理清除单个企业的业务资料，并严格校验统一响应码。
     * 网络失败或车险服务拒绝清理时抛出异常，由上层批处理记录失败并令维护任务最终失败。
     *
     * @param enterpriseId 已由 SaaS 订阅数据判断超过保留期的企业主键
     */
    public void purgeEnterprise(Long enterpriseId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Maintenance-Secret", maintenanceProperties.getInternalSecret());
        try {
            ResponseResult<?> response = restTemplate.postForObject(
                    retentionProperties.getInsurancePurgeUrl(),
                    new HttpEntity<>(Map.of("enterpriseId", enterpriseId), headers),
                    ResponseResult.class);
            if (response == null || response.getCode() == null || response.getCode() != 200) {
                String message = response == null || response.getMsg() == null
                        ? "车险资料清理服务无有效响应" : response.getMsg();
                throw new IllegalStateException(message);
            }
        } catch (RestClientException exception) {
            throw new IllegalStateException("暂时无法连接车险资料清理服务", exception);
        }
    }
}
