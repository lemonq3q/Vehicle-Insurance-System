package com.example.insurancesystem.config;

import com.example.insurancesystem.filter.JwtAuthenticationTokenFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
/**
 * 两个业务后端共享的 Spring Security 配置，采用无状态 JWT 与方法级权限控制。
 * 登录、注册、SSO 交换和公开套餐接口允许匿名，其余请求必须由 JWT 过滤器建立认证上下文。
 */
public class SecurityConfig {

    @Autowired
    private AuthenticationConfiguration authenticationConfiguration;

    @Autowired
    private JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;

    @Autowired
    private AuthenticationEntryPoint authenticationEntryPoint;

    @Autowired
    private AccessDeniedHandler accessDeniedHandler;

    @Autowired
    private Environment environment;

    @Bean
    /**
     * 提供 BCrypt 密码编码器，登录校验、注册和密码重置使用同一不可逆哈希算法。
     */
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    /**
     * 暴露 Spring 自动装配的 AuthenticationManager，供登录服务执行用户名密码认证。
     */
    public AuthenticationManager authenticationManager() throws Exception{
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    /**
     * 构建无状态安全过滤链：关闭基于 Cookie 会话的 CSRF，声明匿名端点，将 JWT 过滤器置于用户名密码过滤器前，
     * 并注册统一的未认证与无权限响应。非生产环境启用 Spring CORS，生产跨域由外部受控层处理。
     */
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers(
                        "/auth/login", "/auth/register", "/auth/code", "/auth/forget",
                        "/portal/auth/login", "/portal/auth/register", "/portal/auth/sms-code",
                        "/portal/auth/forget-password", "/internal/sso/exchange",
                        "/internal/sso/portal-authorize", "/portal/sso/exchange",
                        "/internal/session/logout-enterprise", "/internal/session/logout-user",
                        "/internal/maintenance/**",
                        "/auth/sso/exchange", "/portal/finance/plans"
                ).permitAll()
                .anyRequest().authenticated();
        http.addFilterBefore(jwtAuthenticationTokenFilter, UsernamePasswordAuthenticationFilter.class);
        http.exceptionHandling()
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler);
        if (!environment.acceptsProfiles("prod")) {
            http.cors();
        }
        return http.build();
    }
}
