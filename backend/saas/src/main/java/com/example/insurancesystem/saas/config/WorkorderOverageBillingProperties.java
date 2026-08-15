package com.example.insurancesystem.saas.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 集中承载超额工单计费规则，避免周期和单价散落在维护、预览及套餐变更代码中。 */
@Component
@ConfigurationProperties(prefix = "saas.billing.workorder-overage")
public class WorkorderOverageBillingProperties {
  private int cycleDays = 365;
  private BigDecimal unitPrice = new BigDecimal("0.20");

  public int getCycleDays() {
    return cycleDays;
  }

  public void setCycleDays(int cycleDays) {
    if (cycleDays <= 0) throw new IllegalArgumentException("工单计费周期必须大于 0 天");
    this.cycleDays = cycleDays;
  }

  public BigDecimal getUnitPrice() {
    return unitPrice;
  }

  public void setUnitPrice(BigDecimal unitPrice) {
    if (unitPrice == null || unitPrice.signum() < 0)
      throw new IllegalArgumentException("单个工单周期费用不能小于 0");
    this.unitPrice = unitPrice;
  }
}

