package com.example.insurancesystem.filter;

import com.example.insurancesystem.system.MaintenanceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MaintenanceFilter implements Filter {

    @Autowired
    private MaintenanceManager maintenanceManager;

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletResponse res = (HttpServletResponse) response;

        // ========== 核心修复：添加跨域头 ==========
        res.setHeader("Access-Control-Allow-Origin", "*");
        res.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT");
        res.setHeader("Access-Control-Max-Age", "3600");
        res.setHeader("Access-Control-Allow-Headers", "*");

        if (maintenanceManager.isMaintenance()) {
            // 维护中 → 直接返回
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"code\":503,\"msg\":\"系统维护中，请稍后再试\"}");
            return;
        }

        // 正常放行
        chain.doFilter(request, response);
    }
}