package com.example.insurancesystem.saas.integration.client;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 封装 SaaS 后端对车险内部会话失效接口的调用。
 * 客户端沿用 SSO 服务共享密钥，设置有限连接和读取超时，并严格校验统一响应码；调用由事务提交后
 * 监听器触发，因此网络异常不会反向破坏已经完成的余额或成员关系事务。
 */
@Component
public class InsuranceSessionClient {
  private final RestTemplate restTemplate;
  private final String logoutEnterpriseUrl;
  private final String logoutUserUrl;
  private final String clientSecret;

  public InsuranceSessionClient(
      RestTemplateBuilder builder,
      @Value("${portal.insurance-session.logout-enterprise-url:http://localhost:8080/internal/session/logout-enterprise}") String logoutEnterpriseUrl,
      @Value("${portal.insurance-session.logout-user-url:http://localhost:8080/internal/session/logout-user}") String logoutUserUrl,
      @Value("${portal.sso.insurance-client-secret:change-me-in-production}") String clientSecret) {
    this.restTemplate = builder
        .setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofSeconds(8))
        .build();
    this.logoutEnterpriseUrl = logoutEnterpriseUrl;
    this.logoutUserUrl = logoutUserUrl;
    this.clientSecret = clientSecret;
  }

  /** 请求车险系统失效指定企业全部成员的会话。 */
  public void logoutEnterprise(Long enterpriseId) {
    post(logoutEnterpriseUrl, Map.of("enterpriseId", enterpriseId));
  }

  /** 请求车险系统失效指定用户的当前会话。 */
  public void logoutUser(Long userId) {
    post(logoutUserUrl, Map.of("userId", userId));
  }

  private void post(String url, Map<String, Object> body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Insurance-Client-Secret", clientSecret);
    try {
      ResponseResult<?> response = restTemplate.postForObject(
          url, new HttpEntity<>(body, headers), ResponseResult.class);
      if (response == null || response.getCode() == null || response.getCode() != 200)
        throw new IllegalStateException(
            response == null || response.getMsg() == null ? "车险会话服务无有效响应" : response.getMsg());
    } catch (RestClientException exception) {
      throw new IllegalStateException("暂时无法连接车险会话服务", exception);
    }
  }
}
