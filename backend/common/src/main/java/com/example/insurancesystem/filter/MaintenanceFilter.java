package com.example.insurancesystem.filter;

import com.example.insurancesystem.system.MaintenanceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
/**
 * 维护模式入口过滤器以最高优先级运行，在安全认证及业务处理前拒绝新请求，
 * 从而配合 MaintenanceManager 等待已进入的请求排空并建立稳定维护窗口。
 */
public class MaintenanceFilter implements Filter {

    @Autowired
    private MaintenanceManager maintenanceManager;

    @Value("${app.cors.enabled:true}")
    private boolean corsEnabled;

    @Override
    /**
     * 维护期间直接返回统一 JSON 503 业务响应；若应用启用跨域则同时补齐必要响应头，
     * 使浏览器前端能够读取维护提示。非维护状态不改变请求并继续过滤链。
     */
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletResponse res = (HttpServletResponse) response;
        HttpServletRequest req = (HttpServletRequest) request;

        if (maintenanceManager.isMaintenance() && !isMaintenanceEndpoint(req.getRequestURI())) {
            /*
             * 响应在此终止，不进入认证、请求计数或控制器；因此维护任务开启后不会再增加活跃请求数。
             */
            if (corsEnabled) {
                res.setHeader("Access-Control-Allow-Origin", "*");
                res.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT");
                res.setHeader("Access-Control-Max-Age", "3600");
                res.setHeader("Access-Control-Allow-Headers", "*");
            }
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"code\":503,\"msg\":\"系统维护中，请稍后再试\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /** 仅精确放行分布式协调所需接口，避免维护期间开放其他内部业务接口。 */
    private boolean isMaintenanceEndpoint(String path) {
        return path != null && path.startsWith("/internal/maintenance/");
    }
}
