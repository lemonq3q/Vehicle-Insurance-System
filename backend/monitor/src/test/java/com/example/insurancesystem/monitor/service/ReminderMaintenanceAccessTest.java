package com.example.insurancesystem.monitor.service;

import com.example.insurancesystem.filter.MaintenanceFilter;
import com.example.insurancesystem.system.MaintenanceManager;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证维护窗口允许必要的提醒同步进入控制器鉴权，同时保持其他业务路径关闭。
 * 测试仅检查过滤器路由边界，不绕过内部控制器的共享密钥校验，也不连接真实数据库。
 */
class ReminderMaintenanceAccessTest {
    /**
     * 监控READY时合并接口应继续执行，提醒查询和其他internal路径仍返回维护响应。
     */
    @Test
    void permitsOnlyExactReminderMergeDuringMaintenance() throws Exception {
        MaintenanceFilter filter = new MaintenanceFilter();
        MaintenanceManager manager = mock(MaintenanceManager.class);
        when(manager.isMaintenance()).thenReturn(true);
        ReflectionTestUtils.setField(filter, "maintenanceManager", manager);
        for (String path : new String[]{"/internal/reminders/merge", "/internal/reminders/merge/extra", "/monitor/reminders", "/internal/reminders/delete", "/portal/payment/stripe/webhook"}) {
            AtomicBoolean passed = new AtomicBoolean(false);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("POST", path), response, (request, result) -> passed.set(true));
            assertEquals(path.equals("/internal/reminders/merge"), passed.get(), path);
            if (!passed.get()) {
                assertEquals(503, response.getStatus(), path);
                assertTrue(response.getContentAsString().contains("503"));
            }
        }
    }
}
