package com.example.insurancesystem.monitor.integration;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.HttpBusinessException;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;

/** 监控诊断筛选的SaaS只读客户端，企业用户归属由SaaS负责，监控不访问租户业务表。 */
@Component
public class SaasLogDirectoryClient {
    private final RestTemplate rest;
    private final String baseUrl;
    private final String secret;
    /** 复用内部共享密钥，限制连接与读取等待，依赖失败返回502而不是空候选。 */
    public SaasLogDirectoryClient(RestTemplateBuilder builder,
            @Value("${monitor.request-log.saas-base-url:http://localhost:8081}") String baseUrl,
            @Value("${maintenance.participant.internal-secret}") String secret) {
        rest=builder.setConnectTimeout(Duration.ofSeconds(2)).setReadTimeout(Duration.ofSeconds(5)).build();
        this.baseUrl=baseUrl;this.secret=secret;
    }
    /** 转发精简企业/用户候选查询，所有关键词由URI构造器编码，内部密钥不向浏览器返回。 */
    public Object options(String type,Long enterpriseId,String keyword) {
        if(keyword==null||keyword.isBlank())return List.of();
        UriComponentsBuilder uri=UriComponentsBuilder.fromHttpUrl(baseUrl).path("/internal/log-directory/"+type).queryParam("keyword",keyword);
        if(enterpriseId!=null)uri.queryParam("enterpriseId",enterpriseId);
        HttpHeaders headers=new HttpHeaders();headers.set("X-Maintenance-Secret",secret);
        try {
            ResponseResult<?> result=rest.exchange(uri.build().encode().toUri(),HttpMethod.GET,new HttpEntity<>(headers),ResponseResult.class).getBody();
            if(result==null||!Integer.valueOf(200).equals(result.getCode())||result.getData()==null)throw new HttpBusinessException(502,"SaaS候选查询失败");
            return result.getData();
        } catch(RestClientException e) {throw new HttpBusinessException(502,"SaaS候选查询暂不可用，请稍后重试");}
    }
}
