package com.example.insurancesystem.saas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业车险业务资料保留策略及内部清理接口参数。
 * 保留天数从最近一次套餐结束时间开始计算；接口超时覆盖单个企业 OSS 与数据库清理所需时间，
 * 开发和生产环境可通过环境变量分别调整，但不得把地址配置为外部用户可写的代理端点。
 */
@Component
@ConfigurationProperties(prefix = "saas.data-retention")
public class EnterpriseDataRetentionProperties {
    private int retentionDays = 90;
    private String insurancePurgeUrl =
            "http://localhost:8080/internal/maintenance/enterprise-data/purge";
    private long connectTimeoutSeconds = 5;
    private long readTimeoutSeconds = 1200;

    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int value) { this.retentionDays = value; }
    public String getInsurancePurgeUrl() { return insurancePurgeUrl; }
    public void setInsurancePurgeUrl(String value) { this.insurancePurgeUrl = value; }
    public long getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(long value) { this.connectTimeoutSeconds = value; }
    public long getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(long value) { this.readTimeoutSeconds = value; }
}
