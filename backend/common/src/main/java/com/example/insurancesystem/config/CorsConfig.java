package com.example.insurancesystem.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 设置允许跨域请求的域名
                .allowedOriginPatterns("*")
                // 设置允许的请求方式
                .allowedMethods("*")
                // 设置允许的header属性
                .allowedHeaders("*")
                // 暴露续签后的token响应头，便于前端自动替换
                .exposedHeaders("new-token")
                // 跨域允许时间
                .maxAge(3600);
    }
}
