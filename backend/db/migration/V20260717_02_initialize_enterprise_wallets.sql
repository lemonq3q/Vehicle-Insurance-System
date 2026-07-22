-- Test-environment wallet initialization: every active enterprise owns exactly one wallet.

ALTER TABLE `saas_wallet`
  MODIFY COLUMN `enterprise_id` bigint NOT NULL COMMENT '企业ID',
  ADD UNIQUE KEY `uk_saas_wallet_enterprise` (`enterprise_id`);

INSERT INTO `saas_wallet` (
  `enterprise_id`, `balance_amount`, `frozen_amount`, `currency`, `status`,
  `created_at`, `updated_at`, `updated_by`, `deleted`
)
SELECT e.`id`, 0, 0, 'CNY', 1, NOW(), NOW(), e.`owner_user_id`, 0
FROM `tenant_enterprise` e
LEFT JOIN `saas_wallet` w ON w.`enterprise_id` = e.`id` AND w.`deleted` = 0
WHERE e.`deleted` = 0 AND w.`id` IS NULL;
