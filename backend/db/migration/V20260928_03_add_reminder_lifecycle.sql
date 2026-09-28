-- 审阅后执行：不删除任何历史提醒，不按月份推断风险已消失。
-- 历史记录先标记为生效，由首次业务扫描按当前条件逐条校验；待同步标记用于网络失败补偿。
ALTER TABLE saas_enterprise_reminder
  ADD COLUMN is_active tinyint NOT NULL DEFAULT 1 COMMENT '1生效 0失效',
  ADD COLUMN invalidated_at datetime DEFAULT NULL COMMENT '业务条件不再满足的检查时间',
  ADD COLUMN lifecycle_version bigint NOT NULL DEFAULT 1 COMMENT '业务生命周期版本，与人工处理版本独立',
  ADD COLUMN sync_pending tinyint NOT NULL DEFAULT 1 COMMENT '1待同步监控 0已同步',
  ADD INDEX idx_reminder_active_scan (is_active,id),
  ADD INDEX idx_reminder_pending_sync (sync_pending,id),
  ADD INDEX idx_reminder_enterprise_active (enterprise_id,is_active,last_triggered_at);

-- 监控历史版本从0开始，以便接受SaaS首次权威快照；客服处理状态不受迁移影响。
ALTER TABLE monitor_enterprise_reminder
  ADD COLUMN is_active tinyint NOT NULL DEFAULT 1 COMMENT '1生效 0失效',
  ADD COLUMN invalidated_at datetime DEFAULT NULL COMMENT 'SaaS检查失效时间',
  ADD COLUMN lifecycle_version bigint NOT NULL DEFAULT 0 COMMENT '最近接受的SaaS生命周期版本',
  ADD INDEX idx_monitor_active_queue (is_active,process_status,severity,last_triggered_at);
