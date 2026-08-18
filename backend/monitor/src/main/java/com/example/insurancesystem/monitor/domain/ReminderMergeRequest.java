package com.example.insurancesystem.monitor.domain;

import java.time.LocalDateTime;

/**
 * SaaS 向监控系统传递的提醒当前阶段快照。reminderKey 表示稳定业务生命周期，stageLevel 控制只升级不降级；
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
}
