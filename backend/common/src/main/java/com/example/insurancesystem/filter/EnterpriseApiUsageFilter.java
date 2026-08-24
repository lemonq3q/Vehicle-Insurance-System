package com.example.insurancesystem.filter;

import com.example.insurancesystem.security.EnterpriseContextHolder;
import com.example.insurancesystem.statistics.UsageMetricRecorder;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在 JWT 已恢复企业上下文后统一记录业务 API 访问量。过滤器排除认证、内部维护和非业务资源，
 * 使所有 Controller 自动获得一致统计行为，无需在业务方法中重复插入计数代码。
 */
@Component
public class EnterpriseApiUsageFilter extends OncePerRequestFilter {
    private final UsageMetricRecorder metricRecorder;

    /**
     * 注入高频统计记录器；记录失败由记录器降级，不影响过滤链和业务响应。
     */
    public EnterpriseApiUsageFilter(UsageMetricRecorder metricRecorder) {
        this.metricRecorder = metricRecorder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long enterpriseId = EnterpriseContextHolder.getEnterpriseId();
        if (enterpriseId != null && isBusinessRequest(request)) {
            metricRecorder.recordApiRequest(enterpriseId);
        }
        chain.doFilter(request, response);
    }

    /**
     * 只统计已认证业务路径；预检、登录、内部服务调用、健康检查和静态资源不属于企业 API 用量。
     */
    private boolean isBusinessRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return !"OPTIONS".equalsIgnoreCase(method)
                && !path.startsWith("/internal/")
                && !path.startsWith("/auth/")
                && !path.startsWith("/portal/auth/")
                && !path.startsWith("/actuator/")
                && !path.contains(".");
    }
}
