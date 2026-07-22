-- Refactor active membership storage and generalize owner transfer history.

UPDATE `tenant_member`
SET `status` = 0, `deleted` = 1, `updated_at` = NOW()
WHERE `status` = 3 AND `deleted` = 0;

ALTER TABLE `tenant_member`
  MODIFY COLUMN `enterprise_id` bigint NOT NULL COMMENT '企业ID',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID',
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '0停用 1启用 2待审核',
  ADD UNIQUE KEY `uk_tenant_member_enterprise_user` (`enterprise_id`, `user_id`);

RENAME TABLE `tenant_owner_transfer_log` TO `tenant_member_change_log`;

ALTER TABLE `tenant_member_change_log`
  ADD COLUMN `event_type` varchar(32) DEFAULT NULL COMMENT 'JOIN EXIT KICK ROLE_CHANGE OWNER_TRANSFER' AFTER `enterprise_id`,
  ADD COLUMN `operator_user_id` bigint DEFAULT NULL COMMENT '操作人' AFTER `event_type`,
  ADD COLUMN `target_user_id` bigint DEFAULT NULL COMMENT '目标成员' AFTER `operator_user_id`,
  ADD COLUMN `operator_name_snapshot` varchar(100) DEFAULT NULL COMMENT '操作人姓名快照' AFTER `target_user_id`,
  ADD COLUMN `target_name_snapshot` varchar(100) DEFAULT NULL COMMENT '目标成员姓名快照' AFTER `operator_name_snapshot`,
  ADD COLUMN `before_role_code` varchar(32) DEFAULT NULL COMMENT '变更前角色' AFTER `target_name_snapshot`,
  ADD COLUMN `after_role_code` varchar(32) DEFAULT NULL COMMENT '变更后角色' AFTER `before_role_code`,
  ADD COLUMN `invite_id` bigint DEFAULT NULL COMMENT '来源邀请码' AFTER `after_role_code`,
  ADD COLUMN `occurred_at` datetime DEFAULT NULL COMMENT '发生时间' AFTER `invite_id`;

UPDATE `tenant_member_change_log` log
LEFT JOIN `tenant_user` operator_user ON operator_user.`id` = log.`from_user_id`
LEFT JOIN `tenant_user` target_user ON target_user.`id` = log.`to_user_id`
SET log.`event_type` = 'OWNER_TRANSFER',
    log.`operator_user_id` = log.`from_user_id`,
    log.`target_user_id` = log.`to_user_id`,
    log.`operator_name_snapshot` = operator_user.`real_name`,
    log.`target_name_snapshot` = target_user.`real_name`,
    log.`after_role_code` = 'OWNER',
    log.`occurred_at` = COALESCE(log.`transferred_at`, NOW());

ALTER TABLE `tenant_member_change_log`
  MODIFY COLUMN `enterprise_id` bigint NOT NULL COMMENT '企业ID',
  MODIFY COLUMN `event_type` varchar(32) NOT NULL COMMENT 'JOIN EXIT KICK ROLE_CHANGE OWNER_TRANSFER',
  MODIFY COLUMN `target_user_id` bigint NOT NULL COMMENT '目标成员',
  MODIFY COLUMN `occurred_at` datetime NOT NULL COMMENT '发生时间',
  DROP COLUMN `from_user_id`,
  DROP COLUMN `to_user_id`,
  DROP COLUMN `status`,
  DROP COLUMN `transferred_at`,
  DROP COLUMN `created_by`,
  ADD KEY `idx_member_change_enterprise_time` (`enterprise_id`, `occurred_at`),
  ADD KEY `idx_member_change_enterprise_type` (`enterprise_id`, `event_type`, `occurred_at`),
  COMMENT = '企业人员变动记录';
