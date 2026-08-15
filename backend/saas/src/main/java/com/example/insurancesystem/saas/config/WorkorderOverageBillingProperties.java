package com.example.insurancesystem.saas.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 承载 SaaS 超额工单计费的全局规则，供每日维护扣费和套餐变更试算共同使用。
 * 周期天数与单工单周期价格由 {@code saas.billing.workorder-overage} 配置绑定，默认值仅作为
 * 未配置环境的安全基线，避免不同计费入口各自硬编码后产生金额口径不一致。
 */
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
