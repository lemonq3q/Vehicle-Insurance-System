package com.example.insurancesystem.coordinator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * C 的服务目录和维护计划配置。阶段按配置顺序执行，组互斥串行，组内任务并行；dependsOn 负责
 * 失败传播，requiredServices 只描述执行任务时必须处于 READY 的服务。
 */
@Component
@ConfigurationProperties(prefix = "maintenance.coordinator")
public class CoordinatorProperties {
    private boolean enabled = true;
    private String cron = "0 0 4 * * ?";
    private String zone = "Asia/Shanghai";
    private String internalSecret = "change-me";
    private long prepareTimeoutSeconds = 900;
    private long leaseTimeoutSeconds = 900;
    private long heartbeatIntervalMs = 60000;
    private long finishTimeoutSeconds = 900;
    private int requestMaxAttempts = 3;
    private long requestRetryDelayMs = 1000;
    private double requestRetryBackoffMultiplier = 2.0;
    private long requestRetryMaxDelayMs = 5000;
    private long requestConnectTimeoutSeconds = 5;
    private long requestReadTimeoutSeconds = 1800;
    private List<ServiceEndpoint> services = new ArrayList<>();
    private List<Stage> stages = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getInternalSecret() { return internalSecret; }
    public void setInternalSecret(String value) { this.internalSecret = value; }
    public long getPrepareTimeoutSeconds() { return prepareTimeoutSeconds; }
    public void setPrepareTimeoutSeconds(long value) { this.prepareTimeoutSeconds = value; }
    public long getLeaseTimeoutSeconds() { return leaseTimeoutSeconds; }
    public void setLeaseTimeoutSeconds(long value) { this.leaseTimeoutSeconds = value; }
    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    public void setHeartbeatIntervalMs(long value) { this.heartbeatIntervalMs = value; }
    public long getFinishTimeoutSeconds() { return finishTimeoutSeconds; }
    public void setFinishTimeoutSeconds(long value) { this.finishTimeoutSeconds = value; }
    public int getRequestMaxAttempts() { return requestMaxAttempts; }
    public void setRequestMaxAttempts(int value) { this.requestMaxAttempts = value; }
    public long getRequestRetryDelayMs() { return requestRetryDelayMs; }
    public void setRequestRetryDelayMs(long value) { this.requestRetryDelayMs = value; }
    public double getRequestRetryBackoffMultiplier() { return requestRetryBackoffMultiplier; }
    public void setRequestRetryBackoffMultiplier(double value) { this.requestRetryBackoffMultiplier = value; }
    public long getRequestRetryMaxDelayMs() { return requestRetryMaxDelayMs; }
    public void setRequestRetryMaxDelayMs(long value) { this.requestRetryMaxDelayMs = value; }
    public long getRequestConnectTimeoutSeconds() { return requestConnectTimeoutSeconds; }
    public void setRequestConnectTimeoutSeconds(long value) { this.requestConnectTimeoutSeconds = value; }
    public long getRequestReadTimeoutSeconds() { return requestReadTimeoutSeconds; }
    public void setRequestReadTimeoutSeconds(long value) { this.requestReadTimeoutSeconds = value; }
    public List<ServiceEndpoint> getServices() { return services; }
    public void setServices(List<ServiceEndpoint> services) { this.services = services; }
    public List<Stage> getStages() { return stages; }
    public void setStages(List<Stage> stages) { this.stages = stages; }

    public static class ServiceEndpoint {
        private String serviceId;
        private String baseUrl;
        public String getServiceId() { return serviceId; }
        public void setServiceId(String value) { this.serviceId = value; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String value) { this.baseUrl = value; }
    }

    public static class Stage {
        private int stage;
        private List<Group> groups = new ArrayList<>();
        public int getStage() { return stage; }
        public void setStage(int value) { this.stage = value; }
        public List<Group> getGroups() { return groups; }
        public void setGroups(List<Group> value) { this.groups = value; }
    }

    public static class Group {
        private String group;
        private List<Task> tasks = new ArrayList<>();
        public String getGroup() { return group; }
        public void setGroup(String value) { this.group = value; }
        public List<Task> getTasks() { return tasks; }
        public void setTasks(List<Task> value) { this.tasks = value; }
    }

    public static class Task {
        private String taskId;
        private String targetService;
        private List<String> dependsOn = new ArrayList<>();
        private List<String> requiredServices = new ArrayList<>();
        private long timeoutSeconds = 600;
        public String getTaskId() { return taskId; }
        public void setTaskId(String value) { this.taskId = value; }
        public String getTargetService() { return targetService; }
        public void setTargetService(String value) { this.targetService = value; }
        public List<String> getDependsOn() { return dependsOn; }
        public void setDependsOn(List<String> value) { this.dependsOn = value; }
        public List<String> getRequiredServices() { return requiredServices; }
        public void setRequiredServices(List<String> value) { this.requiredServices = value; }
        public long getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(long value) { this.timeoutSeconds = value; }
    }
}
