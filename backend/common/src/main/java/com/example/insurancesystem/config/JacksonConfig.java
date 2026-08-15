package com.example.insurancesystem.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


@Configuration
/**
 * 定义两个业务后端共享的主 JSON 序列化器，统一 Java 时间和空字段的接口表现。
 */
public class JacksonConfig {

    @Bean
    @Primary
    /**
     * 创建主 ObjectMapper：注册 Java 8 时间支持、保留 null 字段、将日期输出为字符串，并允许空对象序列化。
     * 保留 null 能让前端区分“字段存在但暂无值”和“接口未返回字段”，关闭时间戳则保持协议可读性。
     */
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);


        return objectMapper;
    }
}
