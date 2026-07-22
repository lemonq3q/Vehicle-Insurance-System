package com.example.insurancesystem.integration;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class SaasSsoClient {
    private final RestTemplate restTemplate;
    private final String exchangeUrl;
    private final String clientSecret;

    public SaasSsoClient(
            RestTemplateBuilder builder,
            @Value("${insurance.sso.saas-exchange-url:http://localhost:8081/internal/sso/exchange}") String exchangeUrl,
            @Value("${insurance.sso.client-secret:change-me-in-production}") String clientSecret) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(8))
                .build();
        this.exchangeUrl = exchangeUrl;
        this.clientSecret = clientSecret;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> exchange(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Insurance-Client-Secret", clientSecret);
        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(Collections.singletonMap("code", code), headers);
        try {
            ResponseResult<?> response = restTemplate.postForObject(exchangeUrl, request, ResponseResult.class);
            if (response == null) throw new BusinessException(502, "SaaS 认证服务无响应");
            if (response.getCode() == null || response.getCode() != 200) {
                throw new BusinessException(
                        response.getCode() == null ? 401 : response.getCode(),
                        response.getMsg() == null ? "单点登录认证失败" : response.getMsg());
            }
            if (!(response.getData() instanceof Map))
                throw new BusinessException(502, "SaaS 认证响应格式不正确");
            return (Map<String, Object>) response.getData();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException(502, "暂时无法连接 SaaS 认证服务");
        }
    }
}
