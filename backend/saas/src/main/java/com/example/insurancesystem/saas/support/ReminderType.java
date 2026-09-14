package com.example.insurancesystem.saas.support;

/**
 * SaaS 提醒生产端使用的稳定类型协议。枚举编码必须与 sys_reminder_type.type_code 完全一致，
 * 已投入使用的编码不可重命名；新增类型应先发布字典迁移，再发布产生规则，避免监控端拒绝未知类型。
 */
public enum ReminderType {
    AUTO_RENEW_BALANCE_INSUFFICIENT,
    AUTO_RENEW_PLAN_UNAVAILABLE,
    SUBSCRIPTION_EXPIRING_NO_AUTO_RENEW,
    WORKORDER_QUOTA_NEAR_LIMIT,
    WORKORDER_QUOTA_REACHED,
    WALLET_BALANCE_NEGATIVE,
    WALLET_BALANCE_NEAR_SUSPENSION,
    SUBSCRIPTION_SUSPENDED_ARREARS,
    ENTERPRISE_DATA_DELETION_APPROACHING
}
