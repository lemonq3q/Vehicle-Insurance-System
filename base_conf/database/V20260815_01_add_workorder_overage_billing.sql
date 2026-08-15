-- 工单超额周期计费：套餐权益、订阅快照、订单审计和工单幂等扣费标记。
-- 现有套餐按已确认的产品权益回填：轻量版 1000、专业版 5000、企业版 10000。

ALTER TABLE `saas_plan`
  ADD COLUMN `workorder_limit` int NOT NULL DEFAULT 1000 COMMENT '套餐包含的工单数量额度' AFTER `user_limit`;
ALTER TABLE `saas_plan_archive`
  ADD COLUMN `workorder_limit` int NOT NULL DEFAULT 1000 COMMENT '套餐包含的工单数量额度' AFTER `user_limit`;
UPDATE `saas_plan`
SET `workorder_limit` = CASE `code`
  WHEN 'STARTER_MONTH' THEN 1000
  WHEN 'PRO_YEAR' THEN 5000
  WHEN 'ENTERPRISE_YEAR' THEN 10000
  ELSE `workorder_limit`
END;

ALTER TABLE `saas_subscription`
  ADD COLUMN `workorder_limit` int NOT NULL DEFAULT 0 COMMENT '当前订阅包含的工单数量额度' AFTER `user_limit`;
UPDATE `saas_subscription` s
LEFT JOIN `saas_plan` p ON p.id = s.plan_id
SET s.workorder_limit = CASE WHEN s.status = 1 THEN COALESCE(p.workorder_limit, 0) ELSE 0 END;

ALTER TABLE `saas_order`
  ADD COLUMN `buy_workorder_limit` int DEFAULT NULL COMMENT '下单时套餐工单额度' AFTER `buy_user_limit`,
  ADD COLUMN `workorder_overage_count` int NOT NULL DEFAULT 0 COMMENT '套餐变更时立即计费的超额工单数' AFTER `buy_duration_days`,
  ADD COLUMN `workorder_overage_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '套餐变更时立即计收的超额工单费用' AFTER `workorder_overage_count`;
ALTER TABLE `saas_order_archive`
  ADD COLUMN `buy_workorder_limit` int DEFAULT NULL COMMENT '下单时套餐工单额度' AFTER `buy_user_limit`,
  ADD COLUMN `workorder_overage_count` int NOT NULL DEFAULT 0 COMMENT '套餐变更时立即计费的超额工单数' AFTER `buy_duration_days`,
  ADD COLUMN `workorder_overage_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '套餐变更时立即计收的超额工单费用' AFTER `workorder_overage_count`;

ALTER TABLE `biz_workorder`
  ADD COLUMN `last_overage_billing_date` date DEFAULT NULL COMMENT '最近一次超额周期费用的业务扣费日' AFTER `created_at`,
  ADD KEY `idx_workorder_overage_billing` (`enterprise_id`,`deleted`,`created_at`,`last_overage_billing_date`);
ALTER TABLE `biz_workorder_archive`
  ADD COLUMN `last_overage_billing_date` date DEFAULT NULL COMMENT '最近一次超额周期费用的业务扣费日' AFTER `created_at`;
