package com.example.insurancesystem.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.insurancesystem.config.json.SafeLongIdSerializer;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
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
     * ID 语义的 Long 由统一序列化器安全输出为字符串，其他 Long 仍保持数字协议；请求中的数字字符串
     * 继续由 Jackson 按 DTO 字段类型还原为 Long，因此该精度保护不侵入业务代码。
     */
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.registerModule(new JavaTimeModule());
        SimpleModule safeLongIdModule = new SimpleModule("SafeLongIdModule");
        SafeLongIdSerializer safeLongIdSerializer = new SafeLongIdSerializer();
        safeLongIdModule.addSerializer(Long.class, safeLongIdSerializer);
        safeLongIdModule.addSerializer(Long.TYPE, safeLongIdSerializer);
        objectMapper.registerModule(safeLongIdModule);
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);


        return objectMapper;
    }
}
