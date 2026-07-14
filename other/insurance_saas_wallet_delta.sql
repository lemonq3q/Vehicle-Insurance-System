USE `insurance_saas`;

CREATE TABLE IF NOT EXISTS `saas_wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `balance_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '可用余额',
  `frozen_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '冻结金额',
  `currency` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1正常 0冻结',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业钱包表';

CREATE TABLE IF NOT EXISTS `saas_wallet_archive` LIKE `saas_wallet`;

DROP TRIGGER IF EXISTS `trg_saas_wallet_bi`;
CREATE TRIGGER `trg_saas_wallet_bi` BEFORE INSERT ON `saas_wallet`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_saas_wallet_bu`;
CREATE TRIGGER `trg_saas_wallet_bu` BEFORE UPDATE ON `saas_wallet`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `saas_recharge_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recharge_no` varchar(64) DEFAULT NULL COMMENT '充值订单号',
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `user_id` bigint DEFAULT NULL COMMENT '充值操作人',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '充值金额',
  `pay_channel` varchar(32) DEFAULT NULL COMMENT '支付渠道',
  `pay_trade_no` varchar(100) DEFAULT NULL COMMENT '第三方交易号',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1待支付 2已支付 3已取消 4支付失败',
  `paid_at` datetime DEFAULT NULL COMMENT '支付时间',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='充值订单表';

CREATE TABLE IF NOT EXISTS `saas_recharge_order_archive` LIKE `saas_recharge_order`;

DROP TRIGGER IF EXISTS `trg_saas_recharge_order_bi`;
CREATE TRIGGER `trg_saas_recharge_order_bi` BEFORE INSERT ON `saas_recharge_order`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_saas_recharge_order_bu`;
CREATE TRIGGER `trg_saas_recharge_order_bu` BEFORE UPDATE ON `saas_recharge_order`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `saas_wallet_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `wallet_id` bigint DEFAULT NULL COMMENT '钱包ID',
  `user_id` bigint DEFAULT NULL COMMENT '操作用户',
  `transaction_no` varchar(64) DEFAULT NULL COMMENT '流水号',
  `direction` varchar(10) DEFAULT NULL COMMENT 'IN增加 OUT减少',
  `transaction_type` varchar(32) DEFAULT NULL COMMENT 'RECHARGE BUY_PLAN RENEW_PLAN AUTO_RENEW CHANGE_PLAN REFUND ADJUST',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '本次变动金额',
  `balance_before` decimal(12,2) DEFAULT NULL COMMENT '变动前余额',
  `balance_after` decimal(12,2) DEFAULT NULL COMMENT '变动后余额',
  `related_order_id` bigint DEFAULT NULL COMMENT '关联套餐订单',
  `related_recharge_order_id` bigint DEFAULT NULL COMMENT '关联充值订单',
  `related_subscription_id` bigint DEFAULT NULL COMMENT '关联订阅',
  `remark` varchar(500) DEFAULT NULL COMMENT '说明',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业钱包流水表';

ALTER TABLE `saas_order`
  ADD COLUMN `order_type` varchar(32) DEFAULT NULL COMMENT 'BUY RENEW AUTO_RENEW CHANGE_PLAN' AFTER `order_no`,
  ADD COLUMN `pay_type` varchar(32) DEFAULT NULL COMMENT 'BALANCE ONLINE' AFTER `buy_duration_days`,
  ADD COLUMN `price_amount` decimal(12,2) DEFAULT NULL COMMENT '原始套餐金额' AFTER `amount`,
  ADD COLUMN `discount_amount` decimal(12,2) DEFAULT NULL COMMENT '折扣金额' AFTER `price_amount`,
  ADD COLUMN `credit_amount` decimal(12,2) DEFAULT NULL COMMENT '原套餐剩余价值抵扣' AFTER `discount_amount`,
  ADD COLUMN `payable_amount` decimal(12,2) DEFAULT NULL COMMENT '最终应付金额' AFTER `credit_amount`,
  ADD COLUMN `refund_amount` decimal(12,2) DEFAULT NULL COMMENT '应退回余额金额' AFTER `payable_amount`,
  ADD COLUMN `wallet_transaction_id` bigint DEFAULT NULL COMMENT '关联扣款或退款流水' AFTER `pay_trade_no`,
  ADD COLUMN `original_subscription_id` bigint DEFAULT NULL COMMENT '变更套餐时关联原订阅' AFTER `wallet_transaction_id`,
  ADD COLUMN `old_plan_id` bigint DEFAULT NULL COMMENT '变更前套餐' AFTER `original_subscription_id`,
  ADD COLUMN `new_plan_id` bigint DEFAULT NULL COMMENT '变更后套餐' AFTER `old_plan_id`,
  ADD COLUMN `auto_renew` tinyint NOT NULL DEFAULT 0 COMMENT '是否自动续费订单' AFTER `new_plan_id`;

ALTER TABLE `saas_order_archive`
  ADD COLUMN `order_type` varchar(32) DEFAULT NULL COMMENT 'BUY RENEW AUTO_RENEW CHANGE_PLAN' AFTER `order_no`,
  ADD COLUMN `pay_type` varchar(32) DEFAULT NULL COMMENT 'BALANCE ONLINE' AFTER `buy_duration_days`,
  ADD COLUMN `price_amount` decimal(12,2) DEFAULT NULL COMMENT '原始套餐金额' AFTER `amount`,
  ADD COLUMN `discount_amount` decimal(12,2) DEFAULT NULL COMMENT '折扣金额' AFTER `price_amount`,
  ADD COLUMN `credit_amount` decimal(12,2) DEFAULT NULL COMMENT '原套餐剩余价值抵扣' AFTER `discount_amount`,
  ADD COLUMN `payable_amount` decimal(12,2) DEFAULT NULL COMMENT '最终应付金额' AFTER `credit_amount`,
  ADD COLUMN `refund_amount` decimal(12,2) DEFAULT NULL COMMENT '应退回余额金额' AFTER `payable_amount`,
  ADD COLUMN `wallet_transaction_id` bigint DEFAULT NULL COMMENT '关联扣款或退款流水' AFTER `pay_trade_no`,
  ADD COLUMN `original_subscription_id` bigint DEFAULT NULL COMMENT '变更套餐时关联原订阅' AFTER `wallet_transaction_id`,
  ADD COLUMN `old_plan_id` bigint DEFAULT NULL COMMENT '变更前套餐' AFTER `original_subscription_id`,
  ADD COLUMN `new_plan_id` bigint DEFAULT NULL COMMENT '变更后套餐' AFTER `old_plan_id`,
  ADD COLUMN `auto_renew` tinyint NOT NULL DEFAULT 0 COMMENT '是否自动续费订单' AFTER `new_plan_id`;

ALTER TABLE `saas_subscription`
  ADD COLUMN `auto_renew_enabled` tinyint NOT NULL DEFAULT 0 COMMENT '是否开启自动续费' AFTER `end_at`,
  ADD COLUMN `auto_renew_plan_id` bigint DEFAULT NULL COMMENT '自动续费用套餐' AFTER `auto_renew_enabled`,
  ADD COLUMN `next_renew_at` datetime DEFAULT NULL COMMENT '下次自动续费时间' AFTER `auto_renew_plan_id`,
  ADD COLUMN `last_renew_order_id` bigint DEFAULT NULL COMMENT '最近一次续费订单' AFTER `next_renew_at`,
  ADD COLUMN `cancel_auto_renew_at` datetime DEFAULT NULL COMMENT '取消自动续费时间' AFTER `last_renew_order_id`;

DROP TABLE IF EXISTS `saas_subscription_change_log`;
