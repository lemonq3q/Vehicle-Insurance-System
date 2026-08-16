package com.example.insurancesystem.filter;

import com.example.insurancesystem.system.MaintenanceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component
@Order(10) // 晚于SpringSecurity
/**
 * 对已通过前置维护检查的请求进行活跃计数，为全局维护任务提供排空依据。
 * 过滤器晚于安全链运行，避免被提前拒绝的非法请求被误计为正在执行的业务请求。
 */
public class RequestCountFilter implements Filter {

    @Autowired
    private MaintenanceManager maintenanceManager;

    @Override
    /**
     * 进入后续过滤链前递增计数，并在 finally 中无条件递减，确保控制器异常、认证失败或响应中断均不会泄漏计数。
     */
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        if (((HttpServletRequest) request).getRequestURI().startsWith("/internal/maintenance/")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            maintenanceManager.incrementRequest();
            chain.doFilter(request, response);
        } finally {
            maintenanceManager.decrementRequest();
        }
    }
}
