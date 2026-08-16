-- 为有效套餐增加可追溯的暂停原因及暂停/恢复时间。
-- status=3 继续表示“已暂停”；ARREARS 允许统一余额服务自动恢复，其他原因不得自动恢复。
ALTER TABLE `saas_subscription`
  ADD COLUMN `suspend_reason` varchar(32) DEFAULT NULL COMMENT '暂停原因：ARREARS欠费、MANUAL人工、RISK_CONTROL风控' AFTER `status`,
  ADD COLUMN `suspended_at` datetime DEFAULT NULL COMMENT '最近一次暂停时间' AFTER `suspend_reason`,
  ADD COLUMN `resumed_at` datetime DEFAULT NULL COMMENT '最近一次从欠费暂停恢复时间' AFTER `suspended_at`,
  ADD KEY `idx_saas_subscription_arrears` (`status`, `suspend_reason`, `end_at`, `enterprise_id`);

-- 不推断历史 status=3 的暂停原因，避免把人工或未知暂停误恢复为正常状态。
