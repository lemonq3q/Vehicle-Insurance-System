-- Test-environment rebuild: saas_subscription stores exactly one current state per enterprise.
-- Historical subscription actions remain in saas_order and saas_wallet_transaction.

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE `saas_subscription`;

ALTER TABLE `saas_subscription`
  MODIFY COLUMN `enterprise_id` bigint NOT NULL COMMENT '企业ID',
  MODIFY COLUMN `plan_id` bigint DEFAULT NULL COMMENT '当前或最近一次套餐',
  MODIFY COLUMN `order_id` bigint DEFAULT NULL COMMENT '最近一次生效订单',
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '0未订阅 1生效中 2已过期 3已暂停 4已取消',
  MODIFY COLUMN `user_limit` int NOT NULL DEFAULT 0 COMMENT '当前可启用成员上限',
  ADD COLUMN `ocr_quota` int NOT NULL DEFAULT 0 COMMENT '当前周期OCR额度' AFTER `user_limit`,
  ADD COLUMN `request_quota` int NOT NULL DEFAULT 0 COMMENT '当前周期请求额度' AFTER `ocr_quota`,
  ADD UNIQUE KEY `uk_saas_subscription_enterprise` (`enterprise_id`);

INSERT INTO `saas_subscription` (
  `enterprise_id`, `plan_id`, `order_id`, `status`, `user_limit`, `ocr_quota`,
  `request_quota`, `start_at`, `end_at`, `auto_renew_enabled`, `auto_renew_plan_id`,
  `next_renew_at`, `last_renew_order_id`, `cancel_auto_renew_at`, `created_at`, `updated_at`
)
SELECT
  e.`id`, NULL, NULL, 0, 0, 0, 0, NULL, NULL, 0, NULL, NULL, NULL, NULL, NOW(), NOW()
FROM `tenant_enterprise` e
WHERE e.`deleted` = 0;

SET FOREIGN_KEY_CHECKS = 1;
