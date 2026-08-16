package com.example.insurancesystem.maintenance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 业务服务参与端配置。开发和生产环境可以分别调整密钥、排空期限、租约及单机兜底时间，
 * enabled=false 时保留正常业务能力但不启动本地维护监控。
 */
@Component
@ConfigurationProperties(prefix = "maintenance.participant")
public class MaintenanceParticipantProperties {
    private boolean enabled = true;
    private String serviceId;
    private String internalSecret = "change-me";
    private long drainTimeoutSeconds = 900;
    private long leaseTimeoutSeconds = 900;
    private long standaloneExitDelaySeconds = 900;
    private String fallbackCron = "0 15 4 * * ?";
    private String zone = "Asia/Shanghai";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    public String getInternalSecret() { return internalSecret; }
    public void setInternalSecret(String internalSecret) { this.internalSecret = internalSecret; }
    public long getDrainTimeoutSeconds() { return drainTimeoutSeconds; }
    public void setDrainTimeoutSeconds(long value) { this.drainTimeoutSeconds = value; }
    public long getLeaseTimeoutSeconds() { return leaseTimeoutSeconds; }
    public void setLeaseTimeoutSeconds(long value) { this.leaseTimeoutSeconds = value; }
    public long getStandaloneExitDelaySeconds() { return standaloneExitDelaySeconds; }
    public void setStandaloneExitDelaySeconds(long value) { this.standaloneExitDelaySeconds = value; }
    public String getFallbackCron() { return fallbackCron; }
    public void setFallbackCron(String fallbackCron) { this.fallbackCron = fallbackCron; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
}
