package com.example.insurancesystem.filter;

import com.example.insurancesystem.system.MaintenanceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import javax.servlet.*;
import java.io.IOException;

@Component
@Order(10) // 晚于SpringSecurity
public class RequestCountFilter implements Filter {

    @Autowired
    private MaintenanceManager maintenanceManager;

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        try {
            maintenanceManager.incrementRequest();
            chain.doFilter(request, response);
        } finally {
            maintenanceManager.decrementRequest();
        }
    }
}