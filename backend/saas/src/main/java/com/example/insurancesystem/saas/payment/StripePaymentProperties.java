package com.example.insurancesystem.saas.payment;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SaaS 余额充值使用的 Stripe 配置。
 * 密钥只从部署环境注入，不进入前端或数据库；金额上下限和币种同时约束创建本地订单与
 * Checkout Session，避免客户端绕过页面校验提交异常金额。
 */
@Component
@ConfigurationProperties(prefix = "saas.payment.stripe")
public class StripePaymentProperties {
  private boolean enabled;
  private String secretKey;
  private String publishableKey;
  private String webhookSecret;
  private String currency = "cny";
  private BigDecimal minimumAmount = new BigDecimal("5.00");
  private BigDecimal maximumAmount = new BigDecimal("50000.00");
  private String returnUrl;
  private long reconciliationIntervalMs = 300000L;
  private int reconciliationBatchSize = 100;

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getSecretKey() { return secretKey; }
  public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
  public String getPublishableKey() { return publishableKey; }
  public void setPublishableKey(String publishableKey) { this.publishableKey = publishableKey; }
  public String getWebhookSecret() { return webhookSecret; }
  public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
  public String getCurrency() { return currency; }
  public void setCurrency(String currency) { this.currency = currency; }
  public BigDecimal getMinimumAmount() { return minimumAmount; }
  public void setMinimumAmount(BigDecimal minimumAmount) { this.minimumAmount = minimumAmount; }
  public BigDecimal getMaximumAmount() { return maximumAmount; }
  public void setMaximumAmount(BigDecimal maximumAmount) { this.maximumAmount = maximumAmount; }
  public String getReturnUrl() { return returnUrl; }
  public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
  public long getReconciliationIntervalMs() { return reconciliationIntervalMs; }
  public void setReconciliationIntervalMs(long reconciliationIntervalMs) { this.reconciliationIntervalMs = reconciliationIntervalMs; }
  public int getReconciliationBatchSize() { return reconciliationBatchSize; }
  public void setReconciliationBatchSize(int reconciliationBatchSize) { this.reconciliationBatchSize = reconciliationBatchSize; }
}
