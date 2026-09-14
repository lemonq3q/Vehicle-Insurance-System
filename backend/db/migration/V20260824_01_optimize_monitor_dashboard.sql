-- 监控仪表盘跨月企业总数比较索引。
-- REVIEW REQUIRED：执行前确认生产库不存在等价的 (deleted, created_at) 联合索引。
-- 充值流水和企业日用量已分别具备 idx_recharge_paid_rollup、uk_monitor_enterprise_usage_day，
-- 本迁移不重复创建索引，避免增加业务写入成本。

USE insurance_saas;

ALTER TABLE tenant_enterprise
  ADD KEY idx_enterprise_dashboard_created (deleted, created_at);
