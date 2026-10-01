package com.example.insurancesystem.coordinator;

import com.example.insurancesystem.coordinator.client.ParticipantClient;
import com.example.insurancesystem.coordinator.config.CoordinatorProperties;
import com.example.insurancesystem.coordinator.controller.MaintenanceCoordinatorController;
import com.example.insurancesystem.coordinator.service.MaintenanceCoordinatorService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证查询密钥协议及手动/定时共享防重锁，不调用实际服务或执行维护写操作。 */
class ManualMaintenanceTriggerTest {
  /** GET 接受请求立即返回中文结果，重复请求只报告进行中，错误密钥不得触发维护。 */
  @Test
  void getTriggerUsesQuerySecretAndReturnsResult() throws Exception {
    CoordinatorProperties properties = new CoordinatorProperties();
    properties.setInternalSecret("test-secret");
    MaintenanceCoordinatorService service = mock(MaintenanceCoordinatorService.class);
    when(service.startManualRun()).thenReturn(true, false);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new MaintenanceCoordinatorController(service, properties)).build();
    mvc.perform(get("/internal/coordinator/run").param("secret", "test-secret"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("维护已启动"))
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(get("/internal/coordinator/run").param("secret", "test-secret"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("维护正在进行中"));
    mvc.perform(get("/internal/coordinator/run").param("secret", "wrong"))
        .andExpect(status().isForbidden());
    verify(service, times(2)).startManualRun();
  }

  /** 任意入口持有锁时两个触发入口均拒绝重启；释放资源避免测试线程泄漏。 */
  @Test
  void manualAndScheduledEntryShareRunningLock() {
    CoordinatorProperties properties = new CoordinatorProperties();
    properties.setEnabled(true);
    ParticipantClient client = mock(ParticipantClient.class);
    MaintenanceCoordinatorService service = new MaintenanceCoordinatorService(properties, client);
    try {
      ((AtomicBoolean) ReflectionTestUtils.getField(service, "running")).set(true);
      assertFalse(service.startManualRun());
      assertFalse(service.runNow());
      verifyNoInteractions(client);
    } finally { service.shutdown(); }
  }
}
