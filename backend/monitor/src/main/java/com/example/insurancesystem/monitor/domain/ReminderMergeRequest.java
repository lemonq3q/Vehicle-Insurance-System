package com.example.insurancesystem.monitor.domain;

import java.time.LocalDateTime;

/**
 * SaaS 向监控系统传递的提醒当前阶段快照。reminderKey 表示稳定业务周期，新版生命周期允许阶段升降；
 * 所有文案均由掌握套餐、余额和用量语义的 SaaS 生成，监控系统仅负责幂等合并和客服待办状态恢复。
 */
public class ReminderMergeRequest {
    public Long enterpriseId;
    public String enterpriseName;
    public String reminderType;
    public String reminderKey;
    public String reminderStage;
    public Integer stageLevel;
    public String severity;
    public String title;
    public String content;
    public String businessDataJson;
    public LocalDateTime triggeredAt;
    /**
     * 业务生效状态与SaaS递增版本，独立于客服人工处理revision；空版本仅兼容旧生产端。
     * 失效快照必须携带失效时间，接收端按版本拒绝乱序状态，避免旧请求恢复已失效提醒。
     */
    public Integer isActive = 1;
    public Long lifecycleVersion;
    public LocalDateTime invalidatedAt;
}
