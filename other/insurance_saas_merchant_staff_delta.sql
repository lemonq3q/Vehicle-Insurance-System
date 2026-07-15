USE `insurance_saas`;

CREATE TABLE `biz_merchant_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) NOT NULL COMMENT '稳定分类编码',
  `name` varchar(100) NOT NULL COMMENT '展示名称',
  `direction` varchar(20) NOT NULL COMMENT 'UPSTREAM DOWNSTREAM',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `sort_no` int NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` varchar(500) DEFAULT NULL COMMENT '说明',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_merchant_category_code` (`code`,`deleted`),
  KEY `idx_biz_merchant_category_direction` (`direction`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户分类表';

DROP TRIGGER IF EXISTS `trg_biz_merchant_category_bi`;
CREATE TRIGGER `trg_biz_merchant_category_bi` BEFORE INSERT ON `biz_merchant_category`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_merchant_category_bu`;
CREATE TRIGGER `trg_biz_merchant_category_bu` BEFORE UPDATE ON `biz_merchant_category`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

INSERT INTO `biz_merchant_category` (`code`,`name`,`direction`,`status`,`sort_no`,`remark`)
VALUES
  ('INSURANCE_ORG','保险机构','UPSTREAM',1,10,'原 merchant.type=机构'),
  ('DEALER_STORE','车商店铺','DOWNSTREAM',1,20,'原 merchant.type=车商店铺'),
  ('AUTO_REPAIR','汽修厂','DOWNSTREAM',1,30,'原 merchant.type=汽修厂'),
  ('AGENT','代理人','DOWNSTREAM',1,40,'原 merchant.type=代理人');

ALTER TABLE `biz_merchant`
  ADD COLUMN `category_id` bigint DEFAULT NULL COMMENT '商户分类ID' AFTER `name`,
  MODIFY COLUMN `bank` varchar(100) DEFAULT NULL COMMENT '商户开户行',
  MODIFY COLUMN `bank_card_num` varchar(100) DEFAULT NULL COMMENT '商户银行卡号',
  ADD COLUMN `service_phone` varchar(20) DEFAULT NULL COMMENT '商户公共服务电话' AFTER `channel`,
  DROP COLUMN `merchant_type`,
  DROP COLUMN `org_type`,
  DROP COLUMN `contact`,
  DROP COLUMN `phone`,
  ADD UNIQUE KEY `uk_biz_merchant_code` (`enterprise_id`,`code`,`deleted`),
  ADD KEY `idx_biz_merchant_category` (`enterprise_id`,`category_id`,`deleted`),
  ADD KEY `idx_biz_merchant_name` (`enterprise_id`,`name`);

ALTER TABLE `biz_merchant_archive`
  ADD COLUMN `category_id` bigint DEFAULT NULL COMMENT '商户分类ID' AFTER `name`,
  MODIFY COLUMN `bank` varchar(100) DEFAULT NULL COMMENT '商户开户行',
  MODIFY COLUMN `bank_card_num` varchar(100) DEFAULT NULL COMMENT '商户银行卡号',
  ADD COLUMN `service_phone` varchar(20) DEFAULT NULL COMMENT '商户公共服务电话' AFTER `channel`,
  DROP COLUMN `merchant_type`,
  DROP COLUMN `org_type`,
  DROP COLUMN `contact`,
  DROP COLUMN `phone`,
  ADD KEY `idx_biz_merchant_archive_category` (`enterprise_id`,`category_id`,`deleted`);

CREATE TABLE `biz_merchant_staff` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `merchant_id` bigint DEFAULT NULL COMMENT '所属业务商户ID',
  `name` varchar(100) DEFAULT NULL COMMENT '姓名',
  `phone` varchar(20) DEFAULT NULL COMMENT '业务联系手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `id_num` varchar(100) DEFAULT NULL COMMENT '证件号',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1在用 0停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`),
  KEY `idx_merchant_staff_list` (`enterprise_id`,`merchant_id`,`status`,`deleted`),
  KEY `idx_merchant_staff_phone` (`enterprise_id`,`phone`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户人员表';

CREATE TABLE `biz_merchant_staff_archive` LIKE `biz_merchant_staff`;
ALTER TABLE `biz_merchant_staff_archive`
  ADD COLUMN `archive_batch_no` varchar(64) DEFAULT NULL COMMENT '归档批次号',
  ADD COLUMN `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
  ADD COLUMN `archive_reason` varchar(500) DEFAULT NULL COMMENT '归档原因';

DROP TRIGGER IF EXISTS `trg_biz_merchant_staff_bi`;
CREATE TRIGGER `trg_biz_merchant_staff_bi` BEFORE INSERT ON `biz_merchant_staff`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_merchant_staff_bu`;
CREATE TRIGGER `trg_biz_merchant_staff_bu` BEFORE UPDATE ON `biz_merchant_staff`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE `biz_merchant_staff_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `merchant_id` bigint DEFAULT NULL COMMENT '商户ID',
  `staff_id` bigint DEFAULT NULL COMMENT '商户人员ID',
  `role_code` varchar(32) NOT NULL COMMENT 'CONTACT CLERK PAYEE',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认，仅PAYEE有意义',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  `contact_merchant_key` bigint GENERATED ALWAYS AS (CASE WHEN `role_code` = 'CONTACT' AND `deleted` = 0 THEN `merchant_id` ELSE NULL END) STORED,
  `default_payee_merchant_key` bigint GENERATED ALWAYS AS (CASE WHEN `role_code` = 'PAYEE' AND `is_default` = 1 AND `deleted` = 0 THEN `merchant_id` ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_staff_role` (`enterprise_id`,`staff_id`,`role_code`,`deleted`),
  UNIQUE KEY `uk_merchant_contact` (`contact_merchant_key`),
  UNIQUE KEY `uk_merchant_default_payee` (`default_payee_merchant_key`),
  KEY `idx_merchant_staff_role_list` (`enterprise_id`,`merchant_id`,`role_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户人员业务角色表';

CREATE TABLE `biz_merchant_staff_role_archive` LIKE `biz_merchant_staff_role`;
ALTER TABLE `biz_merchant_staff_role_archive`
  DROP INDEX `uk_merchant_staff_role`,
  DROP INDEX `uk_merchant_contact`,
  DROP INDEX `uk_merchant_default_payee`,
  ADD COLUMN `archive_batch_no` varchar(64) DEFAULT NULL COMMENT '归档批次号',
  ADD COLUMN `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
  ADD COLUMN `archive_reason` varchar(500) DEFAULT NULL COMMENT '归档原因';

DROP TRIGGER IF EXISTS `trg_biz_merchant_staff_role_bi`;
CREATE TRIGGER `trg_biz_merchant_staff_role_bi` BEFORE INSERT ON `biz_merchant_staff_role`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_merchant_staff_role_bu`;
CREATE TRIGGER `trg_biz_merchant_staff_role_bu` BEFORE UPDATE ON `biz_merchant_staff_role`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

ALTER TABLE `biz_workorder`
  ADD COLUMN `source_staff_id` bigint DEFAULT NULL COMMENT '业务来源商户人员' AFTER `create_merchant_id`,
  ADD KEY `idx_workorder_source_staff` (`enterprise_id`,`source_staff_id`,`created_at`);

ALTER TABLE `biz_workorder_archive`
  ADD COLUMN `source_staff_id` bigint DEFAULT NULL COMMENT '业务来源商户人员' AFTER `create_merchant_id`,
  ADD KEY `idx_workorder_archive_source_staff` (`enterprise_id`,`source_staff_id`,`created_at`);

ALTER TABLE `biz_workorder_payment`
  ADD COLUMN `payee_staff_id` bigint DEFAULT NULL COMMENT '默认收款人员ID' AFTER `required_pay_amount`,
  CHANGE COLUMN `pay_name` `payee_name` varchar(100) DEFAULT NULL COMMENT '收款人姓名快照',
  ADD COLUMN `payee_phone` varchar(20) DEFAULT NULL COMMENT '收款人手机号快照' AFTER `payee_name`,
  CHANGE COLUMN `pay_id_num` `payee_id_num` varchar(100) DEFAULT NULL COMMENT '收款人证件号快照',
  CHANGE COLUMN `pay_bank` `merchant_bank` varchar(100) DEFAULT NULL COMMENT '创建方商户开户行快照',
  CHANGE COLUMN `pay_bank_card_num` `merchant_bank_card_num` varchar(100) DEFAULT NULL COMMENT '创建方商户银行卡号快照',
  ADD KEY `idx_workorder_payment_workorder` (`enterprise_id`,`workorder_id`,`deleted`),
  ADD KEY `idx_workorder_payment_payee` (`enterprise_id`,`payee_staff_id`);

ALTER TABLE `biz_workorder_payment_archive`
  ADD COLUMN `payee_staff_id` bigint DEFAULT NULL COMMENT '默认收款人员ID' AFTER `required_pay_amount`,
  CHANGE COLUMN `pay_name` `payee_name` varchar(100) DEFAULT NULL COMMENT '收款人姓名快照',
  ADD COLUMN `payee_phone` varchar(20) DEFAULT NULL COMMENT '收款人手机号快照' AFTER `payee_name`,
  CHANGE COLUMN `pay_id_num` `payee_id_num` varchar(100) DEFAULT NULL COMMENT '收款人证件号快照',
  CHANGE COLUMN `pay_bank` `merchant_bank` varchar(100) DEFAULT NULL COMMENT '创建方商户开户行快照',
  CHANGE COLUMN `pay_bank_card_num` `merchant_bank_card_num` varchar(100) DEFAULT NULL COMMENT '创建方商户银行卡号快照',
  ADD KEY `idx_workorder_payment_archive_workorder` (`enterprise_id`,`workorder_id`,`deleted`),
  ADD KEY `idx_workorder_payment_archive_payee` (`enterprise_id`,`payee_staff_id`);
