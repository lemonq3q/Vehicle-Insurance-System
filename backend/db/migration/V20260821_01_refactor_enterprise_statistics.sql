-- 企业每日统计宽表重构草案。
-- REVIEW REQUIRED：执行前确认“客户”和“盈利”口径，并核对生产库索引。
-- Target: insurance_saas (MySQL 8.0)

USE insurance_saas;

-- 沿用现有企业日统计表；新增指标时继续增加列，不再按指标新建表。
-- 原 workorder_count 表示创建量，新字段明确改为按 finish_time 统计完成量。
ALTER TABLE monitor_enterprise_daily_usage
  CHANGE COLUMN stat_date stat_date DATE NOT NULL
    COMMENT '自然日，Asia/Shanghai [00:00,次日00:00)',
  CHANGE COLUMN workorder_count processed_workorder_count BIGINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT '当日处理完成的非删除工单数',
  MODIFY COLUMN request_count BIGINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT '当日企业鉴权业务API请求次数',
  MODIFY COLUMN ocr_count BIGINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT '当日实际发起的OCR供应商调用次数',
  ADD COLUMN new_customer_count BIGINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT '当日新增且未删除的下游商户数' AFTER ocr_count,
  ADD COLUMN upstream_income DECIMAL(18,2) NOT NULL DEFAULT 0.00
    COMMENT '当日完成工单的上游政策收入' AFTER new_customer_count,
  ADD COLUMN downstream_cost DECIMAL(18,2) NOT NULL DEFAULT 0.00
    COMMENT '当日完成工单的下游政策成本' AFTER upstream_income,
  ADD COLUMN profit_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00
    COMMENT '当日盈利=上游收入-下游成本' AFTER downstream_cost,
  ADD COLUMN is_finalized TINYINT NOT NULL DEFAULT 0
    COMMENT '0当日滚动值 1自然日结束后的终值' AFTER profit_amount,
  ADD COLUMN calculated_at DATETIME DEFAULT NULL
    COMMENT '计算型指标最近重算时间' AFTER is_finalized,
  ADD COLUMN finalized_at DATETIME DEFAULT NULL
    COMMENT '自然日终值确认时间' AFTER calculated_at,
  MODIFY COLUMN last_flushed_at DATETIME DEFAULT NULL
    COMMENT 'API/OCR Redis绝对值最近覆盖写库时间';

-- 系统级和月级数据均可从企业日表汇总，不再重复持久化。
DROP TABLE IF EXISTS monitor_system_daily_stat;
DROP TABLE IF EXISTS monitor_monthly_revenue;

-- 更早设计中的通用指标表在部分环境可能仍然存在，一并删除，避免新旧口径并行。
DROP TABLE IF EXISTS saas_usage_archive_job;
DROP TABLE IF EXISTS saas_usage_monthly;
DROP TABLE IF EXISTS saas_usage_daily;

-- 每日集合计算所需索引。执行前确认不存在同名或等价索引。
ALTER TABLE biz_merchant
  ADD KEY idx_merchant_daily_customer (enterprise_id, deleted, category_id, created_at);

ALTER TABLE biz_workorder_underwriting
  ADD KEY idx_underwriting_daily_finished (enterprise_id, deleted, finish_time, workorder_id);

ALTER TABLE biz_workorder_commission
  ADD KEY idx_commission_profit_rollup (enterprise_id, workorder_id, deleted, side);

ALTER TABLE biz_workorder
  ADD KEY idx_workorder_dashboard_renewal (enterprise_id, deleted, renewal_reminder_disabled, created_at);
