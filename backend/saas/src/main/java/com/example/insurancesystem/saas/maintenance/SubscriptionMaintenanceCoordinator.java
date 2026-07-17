package com.example.insurancesystem.saas.maintenance;

import com.example.insurancesystem.saas.mapper.SubscriptionMaintenanceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionMaintenanceCoordinator {
  private static final Logger log =
      LoggerFactory.getLogger(SubscriptionMaintenanceCoordinator.class);

  private final SubscriptionMaintenanceMapper mapper;
  private final SubscriptionMaintenanceProcessor processor;

  public SubscriptionMaintenanceCoordinator(
      SubscriptionMaintenanceMapper mapper, SubscriptionMaintenanceProcessor processor) {
    this.mapper = mapper;
    this.processor = processor;
  }

  public void processExpiredSubscriptions() {
    for (Long subscriptionId : mapper.findDueSubscriptionIds()) {
      try {
        String result = processor.process(subscriptionId);
        log.info("Subscription {} maintenance result: {}", subscriptionId, result);
      } catch (Exception exception) {
        log.error(
            "Subscription {} auto renewal failed; expiring subscription",
            subscriptionId,
            exception);
        try {
          processor.expireAfterFailure(subscriptionId, exception.getMessage());
        } catch (Exception expireException) {
          log.error("Subscription {} expiration fallback failed", subscriptionId, expireException);
        }
      }
    }
  }
}
