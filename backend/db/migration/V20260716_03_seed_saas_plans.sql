-- Development seed data for the SaaS portal plan list.
-- Fixed IDs keep existing frontend examples and local integration data stable.
INSERT INTO `saas_plan` (
  `id`, `code`, `name`, `description`, `billing_period`, `duration_days`,
  `user_limit`, `price`, `original_price`, `status`, `sort_no`, `deleted`
) VALUES
  (50001, 'STARTER_MONTH', '轻量版', '适合小团队起步，覆盖基础成员协作和车险工单处理。', 'MONTH', 30, 5, 299.00, 399.00, 1, 1, 0),
  (50002, 'PRO_YEAR', '专业版', '适合稳定经营团队，支持更多成员、续保跟进和财务对账。', 'YEAR', 365, 30, 2999.00, 3999.00, 1, 2, 0),
  (50003, 'ENTERPRISE_YEAR', '企业版', '适合多网点企业，提供更高成员上限和专属服务支持。', 'YEAR', 365, 100, 8999.00, 10999.00, 1, 3, 0)
ON DUPLICATE KEY UPDATE
  `code` = VALUES(`code`),
  `name` = VALUES(`name`),
  `description` = VALUES(`description`),
  `billing_period` = VALUES(`billing_period`),
  `duration_days` = VALUES(`duration_days`),
  `user_limit` = VALUES(`user_limit`),
  `price` = VALUES(`price`),
  `original_price` = VALUES(`original_price`),
  `status` = VALUES(`status`),
  `sort_no` = VALUES(`sort_no`),
  `deleted` = 0;
