package com.example.insurancesystem.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("!prod")
/**
 * 非生产环境的宽松跨域配置，支持本地车险、SaaS 门户和监控前端从不同开发端口访问后端。
 * 生产环境不加载该 Bean，应由网关或受控域名策略负责跨域边界。
 */
public class CorsConfig implements WebMvcConfigurer {

    @Override
    /**
     * 为全部开发接口允许任意来源、方法和请求头，并显式暴露 JWT 续签响应头 new-token；
     * 浏览器可据此自动替换临近过期的令牌，预检结果缓存一小时以减少 OPTIONS 请求。
     */
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .exposedHeaders("new-token")
                .maxAge(3600);
    }
}
