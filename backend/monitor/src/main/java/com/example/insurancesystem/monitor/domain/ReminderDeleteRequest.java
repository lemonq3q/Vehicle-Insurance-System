package com.example.insurancesystem.monitor.domain;

/**
 * SaaS 每日维护向监控系统发送的风险恢复清理指令。企业标识与稳定提醒键共同定位一条客服提醒，
 * 服务端仍会限制为允许自动清理的提醒类型，不能借此接口删除人工处理或其他业务提醒。
 */
public class ReminderDeleteRequest {
    public Long enterpriseId;
    public String reminderKey;
}
