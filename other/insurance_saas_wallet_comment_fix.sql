USE `insurance_saas`;

ALTER TABLE `saas_wallet` COMMENT='SaaS enterprise wallet',
  MODIFY COLUMN `enterprise_id` bigint DEFAULT NULL COMMENT 'enterprise id',
  MODIFY COLUMN `balance_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT 'available balance',
  MODIFY COLUMN `frozen_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT 'frozen amount',
  MODIFY COLUMN `currency` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT 'currency',
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '1 normal 0 frozen',
  MODIFY COLUMN `created_at` datetime DEFAULT NULL COMMENT 'created time',
  MODIFY COLUMN `updated_at` datetime DEFAULT NULL COMMENT 'updated time',
  MODIFY COLUMN `updated_by` bigint DEFAULT NULL COMMENT 'updated by',
  MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT 'soft delete';

ALTER TABLE `saas_wallet_archive` COMMENT='SaaS enterprise wallet archive',
  MODIFY COLUMN `enterprise_id` bigint DEFAULT NULL COMMENT 'enterprise id',
  MODIFY COLUMN `balance_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT 'available balance',
  MODIFY COLUMN `frozen_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT 'frozen amount',
  MODIFY COLUMN `currency` varchar(10) NOT NULL DEFAULT 'CNY' COMMENT 'currency',
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '1 normal 0 frozen',
  MODIFY COLUMN `created_at` datetime DEFAULT NULL COMMENT 'created time',
  MODIFY COLUMN `updated_at` datetime DEFAULT NULL COMMENT 'updated time',
  MODIFY COLUMN `updated_by` bigint DEFAULT NULL COMMENT 'updated by',
  MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT 'soft delete';

ALTER TABLE `saas_recharge_order` COMMENT='SaaS recharge order',
  MODIFY COLUMN `recharge_no` varchar(64) DEFAULT NULL COMMENT 'recharge order no',
  MODIFY COLUMN `enterprise_id` bigint DEFAULT NULL COMMENT 'enterprise id',
  MODIFY COLUMN `user_id` bigint DEFAULT NULL COMMENT 'operator user id',
  MODIFY COLUMN `amount` decimal(12,2) DEFAULT NULL COMMENT 'recharge amount',
  MODIFY COLUMN `pay_channel` varchar(32) DEFAULT NULL COMMENT 'pay channel',
  MODIFY COLUMN `pay_trade_no` varchar(100) DEFAULT NULL COMMENT 'third-party trade no',
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '1 pending 2 paid 3 canceled 4 failed',
  MODIFY COLUMN `paid_at` datetime DEFAULT NULL COMMENT 'paid time',
  MODIFY COLUMN `created_at` datetime DEFAULT NULL COMMENT 'created time',
  MODIFY COLUMN `updated_at` datetime DEFAULT NULL COMMENT 'updated time',
  MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT 'soft delete';

ALTER TABLE `saas_recharge_order_archive` COMMENT='SaaS recharge order archive',
  MODIFY COLUMN `recharge_no` varchar(64) DEFAULT NULL COMMENT 'recharge order no',
  MODIFY COLUMN `enterprise_id` bigint DEFAULT NULL COMMENT 'enterprise id',
  MODIFY COLUMN `user_id` bigint DEFAULT NULL COMMENT 'operator user id',
  MODIFY COLUMN `amount` decimal(12,2) DEFAULT NULL COMMENT 'recharge amount',
  MODIFY COLUMN `pay_channel` varchar(32) DEFAULT NULL COMMENT 'pay channel',
  MODIFY COLUMN `pay_trade_no` varchar(100) DEFAULT NULL COMMENT 'third-party trade no',
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '1 pending 2 paid 3 canceled 4 failed',
  MODIFY COLUMN `paid_at` datetime DEFAULT NULL COMMENT 'paid time',
  MODIFY COLUMN `created_at` datetime DEFAULT NULL COMMENT 'created time',
  MODIFY COLUMN `updated_at` datetime DEFAULT NULL COMMENT 'updated time',
  MODIFY COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT 'soft delete';

ALTER TABLE `saas_wallet_transaction` COMMENT='SaaS wallet transaction ledger',
  MODIFY COLUMN `enterprise_id` bigint DEFAULT NULL COMMENT 'enterprise id',
  MODIFY COLUMN `wallet_id` bigint DEFAULT NULL COMMENT 'wallet id',
  MODIFY COLUMN `user_id` bigint DEFAULT NULL COMMENT 'operator user id',
  MODIFY COLUMN `transaction_no` varchar(64) DEFAULT NULL COMMENT 'transaction no',
  MODIFY COLUMN `direction` varchar(10) DEFAULT NULL COMMENT 'IN or OUT',
  MODIFY COLUMN `transaction_type` varchar(32) DEFAULT NULL COMMENT 'RECHARGE BUY_PLAN RENEW_PLAN AUTO_RENEW CHANGE_PLAN REFUND ADJUST',
  MODIFY COLUMN `amount` decimal(12,2) DEFAULT NULL COMMENT 'change amount',
  MODIFY COLUMN `balance_before` decimal(12,2) DEFAULT NULL COMMENT 'balance before',
  MODIFY COLUMN `balance_after` decimal(12,2) DEFAULT NULL COMMENT 'balance after',
  MODIFY COLUMN `related_order_id` bigint DEFAULT NULL COMMENT 'related plan order id',
  MODIFY COLUMN `related_recharge_order_id` bigint DEFAULT NULL COMMENT 'related recharge order id',
  MODIFY COLUMN `related_subscription_id` bigint DEFAULT NULL COMMENT 'related subscription id',
  MODIFY COLUMN `remark` varchar(500) DEFAULT NULL COMMENT 'remark',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time';

ALTER TABLE `saas_order`
  MODIFY COLUMN `order_type` varchar(32) DEFAULT NULL COMMENT 'BUY RENEW AUTO_RENEW CHANGE_PLAN',
  MODIFY COLUMN `pay_type` varchar(32) DEFAULT NULL COMMENT 'BALANCE ONLINE',
  MODIFY COLUMN `price_amount` decimal(12,2) DEFAULT NULL COMMENT 'original price amount',
  MODIFY COLUMN `discount_amount` decimal(12,2) DEFAULT NULL COMMENT 'discount amount',
  MODIFY COLUMN `credit_amount` decimal(12,2) DEFAULT NULL COMMENT 'remaining value credit',
  MODIFY COLUMN `payable_amount` decimal(12,2) DEFAULT NULL COMMENT 'final payable amount',
  MODIFY COLUMN `refund_amount` decimal(12,2) DEFAULT NULL COMMENT 'refund to wallet amount',
  MODIFY COLUMN `wallet_transaction_id` bigint DEFAULT NULL COMMENT 'wallet transaction id',
  MODIFY COLUMN `original_subscription_id` bigint DEFAULT NULL COMMENT 'original subscription id',
  MODIFY COLUMN `old_plan_id` bigint DEFAULT NULL COMMENT 'old plan id',
  MODIFY COLUMN `new_plan_id` bigint DEFAULT NULL COMMENT 'new plan id',
  MODIFY COLUMN `auto_renew` tinyint NOT NULL DEFAULT 0 COMMENT 'auto renew order flag';

ALTER TABLE `saas_order_archive`
  MODIFY COLUMN `order_type` varchar(32) DEFAULT NULL COMMENT 'BUY RENEW AUTO_RENEW CHANGE_PLAN',
  MODIFY COLUMN `pay_type` varchar(32) DEFAULT NULL COMMENT 'BALANCE ONLINE',
  MODIFY COLUMN `price_amount` decimal(12,2) DEFAULT NULL COMMENT 'original price amount',
  MODIFY COLUMN `discount_amount` decimal(12,2) DEFAULT NULL COMMENT 'discount amount',
  MODIFY COLUMN `credit_amount` decimal(12,2) DEFAULT NULL COMMENT 'remaining value credit',
  MODIFY COLUMN `payable_amount` decimal(12,2) DEFAULT NULL COMMENT 'final payable amount',
  MODIFY COLUMN `refund_amount` decimal(12,2) DEFAULT NULL COMMENT 'refund to wallet amount',
  MODIFY COLUMN `wallet_transaction_id` bigint DEFAULT NULL COMMENT 'wallet transaction id',
  MODIFY COLUMN `original_subscription_id` bigint DEFAULT NULL COMMENT 'original subscription id',
  MODIFY COLUMN `old_plan_id` bigint DEFAULT NULL COMMENT 'old plan id',
  MODIFY COLUMN `new_plan_id` bigint DEFAULT NULL COMMENT 'new plan id',
  MODIFY COLUMN `auto_renew` tinyint NOT NULL DEFAULT 0 COMMENT 'auto renew order flag';

ALTER TABLE `saas_subscription`
  MODIFY COLUMN `auto_renew_enabled` tinyint NOT NULL DEFAULT 0 COMMENT 'auto renew enabled',
  MODIFY COLUMN `auto_renew_plan_id` bigint DEFAULT NULL COMMENT 'auto renew plan id',
  MODIFY COLUMN `next_renew_at` datetime DEFAULT NULL COMMENT 'next renew time',
  MODIFY COLUMN `last_renew_order_id` bigint DEFAULT NULL COMMENT 'last renew order id',
  MODIFY COLUMN `cancel_auto_renew_at` datetime DEFAULT NULL COMMENT 'cancel auto renew time';
