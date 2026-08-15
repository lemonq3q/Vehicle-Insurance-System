package com.example.insurancesystem.saas.maintenance;

import com.example.insurancesystem.saas.mapper.WorkorderOverageBillingMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WorkorderOverageBillingCoordinator {
  private static final Logger log =
      LoggerFactory.getLogger(WorkorderOverageBillingCoordinator.class);
  private final WorkorderOverageBillingMapper mapper;
  private final WorkorderOverageBillingProcessor processor;

  public WorkorderOverageBillingCoordinator(
      WorkorderOverageBillingMapper mapper, WorkorderOverageBillingProcessor processor) {
    this.mapper = mapper;
    this.processor = processor;
  }

  public int bill(LocalDate billingDate) {
    int billed = 0;
    LocalDateTime maintenanceStart = billingDate.atStartOfDay();
    LocalDateTime maintenanceEnd = billingDate.atTime(LocalTime.MAX);
    for (Long subscriptionId : mapper.findActiveSubscriptionIds(maintenanceStart, maintenanceEnd)) {
      try {
        billed += processor.bill(subscriptionId, billingDate);
      } catch (Exception exception) {
        // 单个企业扣费失败只记录异常，其他企业继续执行；该企业未写扣费日，后续可以按业务日期补偿重跑。
        log.error("Subscription {} workorder overage billing failed", subscriptionId, exception);
      }
    }
    return billed;
  }
}
