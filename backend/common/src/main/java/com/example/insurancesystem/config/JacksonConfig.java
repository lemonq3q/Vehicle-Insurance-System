package com.example.insurancesystem.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.insurancesystem.config.json.SafeLongIdSerializer;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import java.util.List;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
/**
 * 隔离内部持久化与 HTTP 接口的 JSON 协议。普通业务默认保留数字 ID，
 * MVC 和直接写响应的鉴权入口使用 API Mapper 保护前端整数精度。
 * 两个实例独立注册模块，避免接口配置污染数据库快照；不改变事务或历史数据。
 */
public class JacksonConfig {

    /**
     * 创建接口专用 Mapper，ID 语义的 Long 输出字符串，其他数字保留数字协议。
     * MVC 显式选择本实例；业务代码不默认注入它，防止快照 ID 意外变为字符串。
     * @return 仅用于接口 JSON 的独立实例
     */
    @Bean("apiObjectMapper")
    public ObjectMapper apiObjectMapper() {
        ObjectMapper objectMapper = persistenceObjectMapper().copy();
        SimpleModule safeLongIdModule = new SimpleModule("SafeLongIdModule");
        SafeLongIdSerializer safeLongIdSerializer = new SafeLongIdSerializer();
        safeLongIdModule.addSerializer(Long.class, safeLongIdSerializer);
        safeLongIdModule.addSerializer(Long.TYPE, safeLongIdSerializer);
        objectMapper.registerModule(safeLongIdModule);
        return objectMapper;
    }

    /**
     * 创建默认业务 Mapper，内部 JSON 的 Long ID 保持数字，不加载接口精度保护模块。
     * 无限定名称的注入选择本实例；历史字符串 ID 仍由业务兼容逻辑读取。
     * @return 保留时间、null 字段及空对象既有规则的持久化实例
     */
    @Bean("persistenceObjectMapper")
    @Primary
    public ObjectMapper persistenceObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return mapper;
    }

    /**
     * 将 MVC 的 JSON 转换器固定为接口 Mapper，避免默认持久化 Mapper 被自动选中。
     * 保留转换器顺序和非 JSON 转换器；请求绑定、正常响应及异常响应沿用接口协议。
     * @return 只调整 JSON Mapper 的 MVC 配置
     */
    @Bean
    public WebMvcConfigurer apiJsonWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
                for (HttpMessageConverter<?> converter : converters) {
                    if (converter instanceof MappingJackson2HttpMessageConverter) {
                        ((MappingJackson2HttpMessageConverter) converter).setObjectMapper(apiObjectMapper());
                    }
                }
            }
        };
    }
}
