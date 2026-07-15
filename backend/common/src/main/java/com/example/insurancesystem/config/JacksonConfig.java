package com.example.insurancesystem.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // 1. 注册Java 8时间模块（处理LocalDateTime等）
        objectMapper.registerModule(new JavaTimeModule());

        // 保留null字段
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);

        // 3. 关闭日期转为时间戳，使用字符串格式
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 4. 允许序列化空的POJO（否则会抛异常）
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);


        return objectMapper;
    }
}
