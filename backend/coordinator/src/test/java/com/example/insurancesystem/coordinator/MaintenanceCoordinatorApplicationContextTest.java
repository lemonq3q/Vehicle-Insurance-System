package com.example.insurancesystem.coordinator;

import com.example.insurancesystem.coordinator.client.ParticipantClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证协调后端完整 Spring 容器能够完成组件扫描和构造函数注入。
 * 该测试用于捕获普通单元测试无法发现的 Bean 构造函数歧义及配置绑定问题。
 */
@SpringBootTest(properties = "maintenance.coordinator.enabled=false")
class MaintenanceCoordinatorApplicationContextTest {
    @Autowired
    private ParticipantClient participantClient;

    /**
     * 启动完整应用上下文并确认维护请求客户端已经由 Spring 正确创建。
     */
    @Test
    void applicationContextCreatesParticipantClient() {
        assertNotNull(participantClient);
    }
}
