package com.example.insurancesystem.coordinator;

import com.example.insurancesystem.coordinator.config.CoordinatorProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 加载实际生产配置并验证所有任务依赖的服务均已注册，覆盖 profile 列表覆盖问题。
 * 禁用定时维护并使用测试密钥，不调用真实参与服务或触发维护数据写入。
 */
@ActiveProfiles("prod")
@SpringBootTest(properties = {"maintenance.coordinator.enabled=false", "MAINTENANCE_INTERNAL_SECRET=test-secret"})
class ProductionMaintenancePlanTest {
  @Autowired private CoordinatorProperties properties;

  /** 生产列表必须包含监控服务，且所有任务的目标及依赖均可解析。 */
  @Test
  void productionServicesCoverEveryTask() {
    Set<String> services = properties.getServices().stream()
        .map(CoordinatorProperties.ServiceEndpoint::getServiceId).collect(Collectors.toSet());
    assertTrue(services.contains("monitor-backend"));
    java.util.Map<String, CoordinatorProperties.Task> tasks = properties.getStages().stream()
        .flatMap(stage -> stage.getGroups().stream()).flatMap(group -> group.getTasks().stream())
        .collect(Collectors.toMap(CoordinatorProperties.Task::getTaskId, task -> task));
    assertTrue(tasks.containsKey("insurance-enterprise-daily-statistics"));
    assertTrue(tasks.get("insurance-archive").getDependsOn().contains("insurance-enterprise-daily-statistics"));
    properties.getStages().forEach(stage -> stage.getGroups().forEach(group -> group.getTasks().forEach(task -> {
      assertTrue(services.contains(task.getTargetService()), task.getTaskId());
      assertTrue(services.containsAll(task.getRequiredServices()), task.getTaskId());
    })));
  }
}
