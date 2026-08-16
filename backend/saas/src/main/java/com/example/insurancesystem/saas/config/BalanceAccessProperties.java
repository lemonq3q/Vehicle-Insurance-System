package com.example.insurancesystem.saas.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 统一承载企业余额触发订阅访问控制的阈值规则。
 * 停止阈值采用严格小于判断，恢复阈值采用严格大于判断，二者之间形成缓冲区，避免余额在临界点
 * 附近变化时反复暂停和恢复。配置只作用于尚未到期的有效套餐，不改变未订阅或已过期套餐状态。
 */
@Component
@ConfigurationProperties(prefix = "saas.billing.balance-access")
public class BalanceAccessProperties {
  private BigDecimal suspendThreshold = new BigDecimal("-100.00");
  private BigDecimal restoreThreshold = BigDecimal.ZERO.setScale(2);

  public BigDecimal getSuspendThreshold() {
    return suspendThreshold;
  }

  public void setSuspendThreshold(BigDecimal suspendThreshold) {
    if (suspendThreshold == null) throw new IllegalArgumentException("欠费暂停阈值不能为空");
    this.suspendThreshold = suspendThreshold;
  }

  public BigDecimal getRestoreThreshold() {
    return restoreThreshold;
  }

  public void setRestoreThreshold(BigDecimal restoreThreshold) {
    if (restoreThreshold == null) throw new IllegalArgumentException("欠费恢复阈值不能为空");
    this.restoreThreshold = restoreThreshold;
  }
}
