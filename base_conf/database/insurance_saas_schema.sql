USE `insurance_saas`;

CREATE TABLE IF NOT EXISTS `tenant_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(100) DEFAULT NULL COMMENT '登录账号，可兼容手机号',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `password` varchar(100) DEFAULT NULL COMMENT '加密密码',
  `real_name` varchar(100) DEFAULT NULL COMMENT '姓名',
  `id_num` varchar(100) DEFAULT NULL COMMENT '证件号',
  `avatar_file_id` bigint DEFAULT NULL COMMENT '头像文件ID',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业用户账号表';

CREATE TABLE IF NOT EXISTS `tenant_user_archive` LIKE `tenant_user`;

DROP TRIGGER IF EXISTS `trg_tenant_user_bi`;
CREATE TRIGGER `trg_tenant_user_bi` BEFORE INSERT ON `tenant_user`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_tenant_user_bu`;
CREATE TRIGGER `trg_tenant_user_bu` BEFORE UPDATE ON `tenant_user`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `tenant_enterprise` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(150) DEFAULT NULL COMMENT '企业名称',
  `code` varchar(50) DEFAULT NULL COMMENT '企业编码',
  `owner_user_id` bigint DEFAULT NULL COMMENT '当前企业拥有者用户ID',
  `contact_name` varchar(100) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1正常 2欠费限制 3停用',
  `source` tinyint DEFAULT NULL COMMENT '1用户自建 2后台创建',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业租户表';

CREATE TABLE IF NOT EXISTS `tenant_enterprise_archive` LIKE `tenant_enterprise`;

DROP TRIGGER IF EXISTS `trg_tenant_enterprise_bi`;
CREATE TRIGGER `trg_tenant_enterprise_bi` BEFORE INSERT ON `tenant_enterprise`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_tenant_enterprise_bu`;
CREATE TRIGGER `trg_tenant_enterprise_bu` BEFORE UPDATE ON `tenant_enterprise`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `tenant_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `role_code` varchar(32) DEFAULT NULL COMMENT 'OWNER ADMIN ISSUER',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1正常 0禁用 2待审核 3已退出',
  `joined_by_invite_id` bigint DEFAULT NULL COMMENT '来源邀请码',
  `joined_at` datetime DEFAULT NULL COMMENT '加入时间',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业成员表';

CREATE TABLE IF NOT EXISTS `tenant_member_archive` LIKE `tenant_member`;

DROP TRIGGER IF EXISTS `trg_tenant_member_bi`;
CREATE TRIGGER `trg_tenant_member_bi` BEFORE INSERT ON `tenant_member`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_tenant_member_bu`;
CREATE TRIGGER `trg_tenant_member_bu` BEFORE UPDATE ON `tenant_member`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `tenant_owner_transfer_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `from_user_id` bigint DEFAULT NULL COMMENT '原拥有者',
  `to_user_id` bigint DEFAULT NULL COMMENT '新拥有者',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1成功 2撤销 3失败',
  `transferred_at` datetime DEFAULT NULL COMMENT '转让时间',
  `created_by` bigint DEFAULT NULL COMMENT '操作人',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业拥有者转让记录';

CREATE TABLE IF NOT EXISTS `tenant_invite_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `code` varchar(64) DEFAULT NULL COMMENT '邀请码',
  `default_role_code` varchar(32) DEFAULT 'ISSUER' COMMENT '默认角色',
  `max_use_count` int DEFAULT NULL COMMENT '最大使用次数',
  `used_count` int NOT NULL DEFAULT 0 COMMENT '已使用次数',
  `expires_at` datetime DEFAULT NULL COMMENT '过期时间',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1可用 2已失效 3已撤销',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业邀请码表';

CREATE TABLE IF NOT EXISTS `tenant_invite_code_archive` LIKE `tenant_invite_code`;

DROP TRIGGER IF EXISTS `trg_tenant_invite_code_bi`;
CREATE TRIGGER `trg_tenant_invite_code_bi` BEFORE INSERT ON `tenant_invite_code`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_tenant_invite_code_bu`;
CREATE TRIGGER `trg_tenant_invite_code_bu` BEFORE UPDATE ON `tenant_invite_code`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `saas_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) DEFAULT NULL COMMENT '套餐编码',
  `name` varchar(100) DEFAULT NULL COMMENT '套餐名称',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `billing_period` varchar(20) DEFAULT NULL COMMENT 'MONTH YEAR DAY',
  `duration_days` int DEFAULT NULL COMMENT '有效天数',
  `user_limit` int DEFAULT NULL COMMENT '最大成员数',
  `ocr_quota` int DEFAULT NULL COMMENT 'OCR次数额度',
  `request_quota` int DEFAULT NULL COMMENT '请求次数额度',
  `price` decimal(12,2) DEFAULT NULL COMMENT '售价',
  `original_price` decimal(12,2) DEFAULT NULL COMMENT '原价',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  `sort_no` int DEFAULT 0 COMMENT '排序',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SaaS套餐表';

CREATE TABLE IF NOT EXISTS `saas_plan_archive` LIKE `saas_plan`;

DROP TRIGGER IF EXISTS `trg_saas_plan_bi`;
CREATE TRIGGER `trg_saas_plan_bi` BEFORE INSERT ON `saas_plan`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_saas_plan_bu`;
CREATE TRIGGER `trg_saas_plan_bu` BEFORE UPDATE ON `saas_plan`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `saas_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) DEFAULT NULL COMMENT '订单号',
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `buyer_user_id` bigint DEFAULT NULL COMMENT '购买人',
  `plan_id` bigint DEFAULT NULL COMMENT '套餐ID',
  `plan_snapshot_json` json DEFAULT NULL COMMENT '下单时套餐快照',
  `buy_user_limit` int DEFAULT NULL COMMENT '购买人数',
  `buy_duration_days` int DEFAULT NULL COMMENT '购买时长',
  `amount` decimal(12,2) DEFAULT NULL COMMENT '应付金额',
  `paid_amount` decimal(12,2) DEFAULT NULL COMMENT '实付金额',
  `pay_channel` varchar(32) DEFAULT NULL COMMENT '支付渠道',
  `pay_trade_no` varchar(100) DEFAULT NULL COMMENT '第三方交易号',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1待支付 2已支付 3已取消 4已退款 5已关闭',
  `paid_at` datetime DEFAULT NULL COMMENT '支付时间',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SaaS套餐订单表';

CREATE TABLE IF NOT EXISTS `saas_order_archive` LIKE `saas_order`;

DROP TRIGGER IF EXISTS `trg_saas_order_bi`;
CREATE TRIGGER `trg_saas_order_bi` BEFORE INSERT ON `saas_order`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_saas_order_bu`;
CREATE TRIGGER `trg_saas_order_bu` BEFORE UPDATE ON `saas_order`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `saas_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `plan_id` bigint DEFAULT NULL COMMENT '当前套餐',
  `order_id` bigint DEFAULT NULL COMMENT '来源订单',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1生效中 2已过期 3已暂停 4已取消',
  `user_limit` int DEFAULT NULL COMMENT '当前人数上限',
  `ocr_quota` int DEFAULT NULL COMMENT '当前周期OCR额度',
  `request_quota` int DEFAULT NULL COMMENT '当前周期请求额度',
  `start_at` datetime DEFAULT NULL COMMENT '生效时间',
  `end_at` datetime DEFAULT NULL COMMENT '到期时间',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='企业订阅表';

DROP TRIGGER IF EXISTS `trg_saas_subscription_bi`;
CREATE TRIGGER `trg_saas_subscription_bi` BEFORE INSERT ON `saas_subscription`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_saas_subscription_bu`;
CREATE TRIGGER `trg_saas_subscription_bu` BEFORE UPDATE ON `saas_subscription`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `saas_subscription_change_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `subscription_id` bigint DEFAULT NULL COMMENT '订阅ID',
  `order_id` bigint DEFAULT NULL COMMENT '订单ID',
  `change_type` varchar(32) DEFAULT NULL COMMENT 'CREATE RENEW UPGRADE DOWNGRADE EXPIRE CANCEL',
  `before_json` json DEFAULT NULL COMMENT '变更前',
  `after_json` json DEFAULT NULL COMMENT '变更后',
  `changed_at` datetime DEFAULT NULL COMMENT '变更时间',
  `changed_by` bigint DEFAULT NULL COMMENT '操作人',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订阅变更记录';

CREATE TABLE IF NOT EXISTS `platform_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(100) DEFAULT NULL COMMENT '后台登录账号',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `password` varchar(100) DEFAULT NULL COMMENT '加密密码',
  `real_name` varchar(100) DEFAULT NULL COMMENT '姓名',
  `department` varchar(100) DEFAULT NULL COMMENT '部门',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台平台用户表';

CREATE TABLE IF NOT EXISTS `platform_user_archive` LIKE `platform_user`;

DROP TRIGGER IF EXISTS `trg_platform_user_bi`;
CREATE TRIGGER `trg_platform_user_bi` BEFORE INSERT ON `platform_user`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_platform_user_bu`;
CREATE TRIGGER `trg_platform_user_bu` BEFORE UPDATE ON `platform_user`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `platform_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `platform_user_id` bigint DEFAULT NULL COMMENT '平台用户ID',
  `role_id` bigint DEFAULT NULL COMMENT '平台角色ID',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台用户角色关联表';

CREATE TABLE IF NOT EXISTS `platform_user_role_archive` LIKE `platform_user_role`;

CREATE TABLE IF NOT EXISTS `auth_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(50) DEFAULT NULL COMMENT '角色编码',
  `name` varchar(100) DEFAULT NULL COMMENT '角色名称',
  `role_scope` varchar(32) DEFAULT NULL COMMENT 'PLATFORM TENANT',
  `builtin` tinyint NOT NULL DEFAULT 0 COMMENT '是否系统内置',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `auth_role_archive` LIKE `auth_role`;

DROP TRIGGER IF EXISTS `trg_auth_role_bi`;
CREATE TRIGGER `trg_auth_role_bi` BEFORE INSERT ON `auth_role`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_auth_role_bu`;
CREATE TRIGGER `trg_auth_role_bu` BEFORE UPDATE ON `auth_role`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `auth_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL COMMENT '父权限',
  `system_code` varchar(32) DEFAULT NULL COMMENT 'PORTAL INSURANCE ADMIN',
  `type` varchar(20) DEFAULT NULL COMMENT 'MENU BUTTON API',
  `code` varchar(100) DEFAULT NULL COMMENT '权限编码',
  `name` varchar(100) DEFAULT NULL COMMENT '权限名称',
  `route_path` varchar(200) DEFAULT NULL COMMENT '前端路由',
  `component` varchar(200) DEFAULT NULL COMMENT '前端组件',
  `sort_no` int DEFAULT 0 COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';

CREATE TABLE IF NOT EXISTS `auth_permission_archive` LIKE `auth_permission`;

DROP TRIGGER IF EXISTS `trg_auth_permission_bi`;
CREATE TRIGGER `trg_auth_permission_bi` BEFORE INSERT ON `auth_permission`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_auth_permission_bu`;
CREATE TRIGGER `trg_auth_permission_bu` BEFORE UPDATE ON `auth_permission`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `auth_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint DEFAULT NULL COMMENT '角色ID',
  `permission_id` bigint DEFAULT NULL COMMENT '权限ID',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';

CREATE TABLE IF NOT EXISTS `auth_role_permission_archive` LIKE `auth_role_permission`;

CREATE TABLE IF NOT EXISTS `saas_usage_daily` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stat_date` date DEFAULT NULL COMMENT '统计日期',
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `metric_code` varchar(50) DEFAULT NULL COMMENT 'REQUEST OCR',
  `dimension` varchar(100) DEFAULT NULL COMMENT '接口路径 OCR类型或模块',
  `count_value` bigint NOT NULL DEFAULT 0 COMMENT '次数',
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
  `archive_batch_no` varchar(64) DEFAULT NULL COMMENT '归档批次',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每日用量汇总表';

CREATE TABLE IF NOT EXISTS `saas_usage_monthly` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stat_month` char(7) DEFAULT NULL COMMENT '统计月份',
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `metric_code` varchar(50) DEFAULT NULL COMMENT 'REQUEST OCR',
  `dimension` varchar(100) DEFAULT NULL COMMENT '接口路径 OCR类型或模块',
  `count_value` bigint NOT NULL DEFAULT 0 COMMENT '次数',
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每月用量汇总表';

CREATE TABLE IF NOT EXISTS `saas_usage_archive_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(64) DEFAULT NULL COMMENT '批次号',
  `stat_date` date DEFAULT NULL COMMENT '归档日期',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1执行中 2成功 3失败',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间',
  `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '错误信息',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用量归档任务表';

CREATE TABLE IF NOT EXISTS `biz_merchant_category` (
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

CREATE TABLE IF NOT EXISTS `biz_merchant` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '所属企业租户',
  `code` varchar(100) DEFAULT NULL COMMENT '商户编码',
  `name` varchar(100) DEFAULT NULL COMMENT '商户名称',
  `category_id` bigint DEFAULT NULL COMMENT '商户分类ID',
  `location` varchar(20) DEFAULT NULL COMMENT '所在地区',
  `address` varchar(100) DEFAULT NULL COMMENT '地址',
  `contact` varchar(100) DEFAULT NULL COMMENT '上游机构联系人',
  `phone` varchar(20) DEFAULT NULL COMMENT '上游机构联系电话',
  `bank` varchar(100) DEFAULT NULL COMMENT '商户开户行',
  `bank_card_num` varchar(100) DEFAULT NULL COMMENT '商户银行卡号',
  `channel` varchar(100) DEFAULT NULL COMMENT '渠道',
  `service_phone` varchar(20) DEFAULT NULL COMMENT '商户公共服务电话',
  `default_area_code` varchar(20) DEFAULT NULL COMMENT '默认区域',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_merchant_code` (`enterprise_id`,`code`,`deleted`),
  KEY `idx_biz_merchant_category` (`enterprise_id`,`category_id`,`deleted`),
  KEY `idx_biz_merchant_name` (`enterprise_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务商户表';

CREATE TABLE IF NOT EXISTS `biz_merchant_archive` LIKE `biz_merchant`;
ALTER TABLE `biz_merchant_archive`
  DROP INDEX `uk_biz_merchant_code`,
  DROP INDEX `idx_biz_merchant_category`,
  DROP INDEX `idx_biz_merchant_name`,
  ADD KEY `idx_biz_merchant_archive_category` (`enterprise_id`,`category_id`,`deleted`);

DROP TRIGGER IF EXISTS `trg_biz_merchant_bi`;
CREATE TRIGGER `trg_biz_merchant_bi` BEFORE INSERT ON `biz_merchant`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_merchant_bu`;
CREATE TRIGGER `trg_biz_merchant_bu` BEFORE UPDATE ON `biz_merchant`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `biz_merchant_staff` (
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

CREATE TABLE IF NOT EXISTS `biz_merchant_staff_archive` LIKE `biz_merchant_staff`;
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

CREATE TABLE IF NOT EXISTS `biz_merchant_staff_role` (
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

CREATE TABLE IF NOT EXISTS `biz_merchant_staff_role_archive` LIKE `biz_merchant_staff_role`;
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

CREATE TABLE IF NOT EXISTS `biz_merchant_area` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `merchant_id` bigint DEFAULT NULL COMMENT '商户ID',
  `area_code` varchar(20) DEFAULT NULL COMMENT '区域编码',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户业务区域表';

CREATE TABLE IF NOT EXISTS `biz_merchant_area_archive` LIKE `biz_merchant_area`;

DROP TRIGGER IF EXISTS `trg_biz_merchant_area_bi`;
CREATE TRIGGER `trg_biz_merchant_area_bi` BEFORE INSERT ON `biz_merchant_area`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_merchant_area_bu`;
CREATE TRIGGER `trg_biz_merchant_area_bu` BEFORE UPDATE ON `biz_merchant_area`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `biz_workorder` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `code` varchar(100) DEFAULT NULL COMMENT '工单编号',
  `type` tinyint DEFAULT 0 COMMENT '0旧车 1新车',
  `owner_type` tinyint DEFAULT 0 COMMENT '0个人 1组织',
  `owner_name` varchar(100) DEFAULT NULL COMMENT '车主姓名',
  `owner_phone` varchar(20) DEFAULT NULL COMMENT '车主电话',
  `owner_id_num` varchar(50) DEFAULT NULL COMMENT '证件号',
  `organization_name` varchar(100) DEFAULT NULL COMMENT '组织名称',
  `social_credit_code` varchar(100) DEFAULT NULL COMMENT '统一社会信用代码',
  `create_merchant_id` bigint DEFAULT NULL COMMENT '创建方下游商户',
  `source_staff_id` bigint DEFAULT NULL COMMENT '业务来源商户人员',
  `handle_merchant_id` bigint DEFAULT NULL COMMENT '处理方商户',
  `insurance_merchant_id` bigint DEFAULT NULL COMMENT '承保上游机构',
  `area_code` varchar(20) DEFAULT NULL COMMENT '业务区域',
  `commercial_insurance_start_time` bigint DEFAULT NULL COMMENT '商业险起保时间（Unix秒）',
  `compulsory_insurance_start_time` bigint DEFAULT NULL COMMENT '交强险起保时间（Unix秒）',
  `status` tinyint DEFAULT 1 COMMENT '工单状态',
  `remind_status` tinyint DEFAULT 0 COMMENT '续保跟进状态',
  `renewal_status_cycle` int NOT NULL DEFAULT 0 COMMENT '当前续保状态所属年度周期，0表示未记录',
  `renewal_reminder_disabled` tinyint NOT NULL DEFAULT 0 COMMENT '是否永久关闭续保提醒：0否 1是',
  `follow_up_res` varchar(1000) DEFAULT NULL COMMENT '跟进结果',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_by` bigint DEFAULT NULL COMMENT '创建用户',
  `handle_by` bigint DEFAULT NULL COMMENT '处理用户',
  `updated_by` bigint DEFAULT NULL COMMENT '更新用户',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`),
  KEY `idx_workorder_source_staff` (`enterprise_id`,`source_staff_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单主表';

CREATE TABLE IF NOT EXISTS `biz_workorder_archive` LIKE `biz_workorder`;
ALTER TABLE `biz_workorder_archive`
  DROP INDEX `idx_workorder_source_staff`,
  ADD KEY `idx_workorder_archive_source_staff` (`enterprise_id`,`source_staff_id`,`created_at`);

DROP TRIGGER IF EXISTS `trg_biz_workorder_bi`;
CREATE TRIGGER `trg_biz_workorder_bi` BEFORE INSERT ON `biz_workorder`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_workorder_bu`;
CREATE TRIGGER `trg_biz_workorder_bu` BEFORE UPDATE ON `biz_workorder`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `biz_workorder_quote` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `workorder_id` bigint DEFAULT NULL COMMENT '工单ID',
  `quotation_no` varchar(100) DEFAULT NULL COMMENT '报价单号',
  `commercial_amount` decimal(12,2) DEFAULT NULL COMMENT '商业险金额',
  `compulsory_amount` decimal(12,2) DEFAULT NULL COMMENT '交强险金额',
  `vehicle_and_tax_amount` decimal(12,2) DEFAULT NULL COMMENT '车船税',
  `non_motor_amount` decimal(12,2) DEFAULT NULL COMMENT '非车险金额',
  `non_motor_insurance_name` varchar(100) DEFAULT NULL COMMENT '非车险名称',
  `non_motor_coverage_amount` decimal(12,2) DEFAULT NULL COMMENT '非车保障金额',
  `quotation_remark` varchar(500) DEFAULT NULL COMMENT '报价备注',
  `quotation_failed_remark` varchar(500) DEFAULT NULL COMMENT '报价失败原因',
  `quotation_time` datetime DEFAULT NULL COMMENT '报价时间',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单报价表';

CREATE TABLE IF NOT EXISTS `biz_workorder_quote_archive` LIKE `biz_workorder_quote`;

DROP TRIGGER IF EXISTS `trg_biz_workorder_quote_bi`;
CREATE TRIGGER `trg_biz_workorder_quote_bi` BEFORE INSERT ON `biz_workorder_quote`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_workorder_quote_bu`;
CREATE TRIGGER `trg_biz_workorder_quote_bu` BEFORE UPDATE ON `biz_workorder_quote`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `biz_workorder_commission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `workorder_id` bigint DEFAULT NULL COMMENT '工单ID',
  `side` varchar(20) DEFAULT NULL COMMENT 'UPSTREAM DOWNSTREAM',
  `compute_type` tinyint DEFAULT 0 COMMENT '0税前 1税后',
  `commercial_percentage` decimal(8,2) DEFAULT NULL COMMENT '商业险比例',
  `compulsory_percentage` decimal(8,2) DEFAULT NULL COMMENT '交强险比例',
  `vehicle_tax_percentage` decimal(8,2) DEFAULT NULL COMMENT '车船税比例',
  `non_motor_percentage` decimal(8,2) DEFAULT NULL COMMENT '非车险比例',
  `commercial_amount` decimal(12,2) DEFAULT NULL COMMENT '商业险费用',
  `compulsory_amount` decimal(12,2) DEFAULT NULL COMMENT '交强险费用',
  `vehicle_tax_amount` decimal(12,2) DEFAULT NULL COMMENT '车船税费用',
  `non_motor_amount` decimal(12,2) DEFAULT NULL COMMENT '非车险费用',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单上下游费用表';

CREATE TABLE IF NOT EXISTS `biz_workorder_commission_archive` LIKE `biz_workorder_commission`;

DROP TRIGGER IF EXISTS `trg_biz_workorder_commission_bi`;
CREATE TRIGGER `trg_biz_workorder_commission_bi` BEFORE INSERT ON `biz_workorder_commission`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_workorder_commission_bu`;
CREATE TRIGGER `trg_biz_workorder_commission_bu` BEFORE UPDATE ON `biz_workorder_commission`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `biz_workorder_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `workorder_id` bigint DEFAULT NULL COMMENT '工单ID',
  `required_pay_amount` decimal(12,2) DEFAULT NULL COMMENT '应付金额',
  `payee_staff_id` bigint DEFAULT NULL COMMENT '默认收款人员ID',
  `payee_name` varchar(100) DEFAULT NULL COMMENT '收款人姓名快照',
  `payee_phone` varchar(20) DEFAULT NULL COMMENT '收款人手机号快照',
  `payee_id_num` varchar(100) DEFAULT NULL COMMENT '收款人证件号快照',
  `merchant_bank` varchar(100) DEFAULT NULL COMMENT '创建方商户开户行快照',
  `merchant_bank_card_num` varchar(100) DEFAULT NULL COMMENT '创建方商户银行卡号快照',
  `pay_remark` varchar(500) DEFAULT NULL COMMENT '支付备注',
  `pay_failed_remark` varchar(500) DEFAULT NULL COMMENT '支付失败原因',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`),
  KEY `idx_workorder_payment_workorder` (`enterprise_id`,`workorder_id`,`deleted`),
  KEY `idx_workorder_payment_payee` (`enterprise_id`,`payee_staff_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单支付表';

CREATE TABLE IF NOT EXISTS `biz_workorder_payment_archive` LIKE `biz_workorder_payment`;
ALTER TABLE `biz_workorder_payment_archive`
  DROP INDEX `idx_workorder_payment_workorder`,
  DROP INDEX `idx_workorder_payment_payee`,
  ADD KEY `idx_workorder_payment_archive_workorder` (`enterprise_id`,`workorder_id`,`deleted`),
  ADD KEY `idx_workorder_payment_archive_payee` (`enterprise_id`,`payee_staff_id`);

DROP TRIGGER IF EXISTS `trg_biz_workorder_payment_bi`;
CREATE TRIGGER `trg_biz_workorder_payment_bi` BEFORE INSERT ON `biz_workorder_payment`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_workorder_payment_bu`;
CREATE TRIGGER `trg_biz_workorder_payment_bu` BEFORE UPDATE ON `biz_workorder_payment`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `biz_workorder_underwriting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `workorder_id` bigint DEFAULT NULL COMMENT '工单ID',
  `underwriting_remark` varchar(500) DEFAULT NULL COMMENT '核保备注',
  `underwriting_failed_remark` varchar(500) DEFAULT NULL COMMENT '核保失败原因',
  `underwriting_time` datetime DEFAULT NULL COMMENT '核保时间',
  `commercial_policy_no` varchar(100) DEFAULT NULL COMMENT '商业险保单号',
  `compulsory_policy_no` varchar(100) DEFAULT NULL COMMENT '交强险保单号',
  `accept_insurance_remark` varchar(500) DEFAULT NULL COMMENT '承保备注',
  `accept_insurance_failed_remark` varchar(500) DEFAULT NULL COMMENT '承保失败原因',
  `finish_time` datetime DEFAULT NULL COMMENT '完成时间',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单核保与出单表';

CREATE TABLE IF NOT EXISTS `biz_workorder_underwriting_archive` LIKE `biz_workorder_underwriting`;

DROP TRIGGER IF EXISTS `trg_biz_workorder_underwriting_bi`;
CREATE TRIGGER `trg_biz_workorder_underwriting_bi` BEFORE INSERT ON `biz_workorder_underwriting`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_workorder_underwriting_bu`;
CREATE TRIGGER `trg_biz_workorder_underwriting_bu` BEFORE UPDATE ON `biz_workorder_underwriting`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `biz_workorder_logistics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `workorder_id` bigint DEFAULT NULL COMMENT '工单ID',
  `tracking_num` varchar(100) DEFAULT NULL COMMENT '快递单号',
  `logistics_company` varchar(100) DEFAULT NULL COMMENT '快递公司',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单物流表';

CREATE TABLE IF NOT EXISTS `biz_workorder_logistics_archive` LIKE `biz_workorder_logistics`;

DROP TRIGGER IF EXISTS `trg_biz_workorder_logistics_bi`;
CREATE TRIGGER `trg_biz_workorder_logistics_bi` BEFORE INSERT ON `biz_workorder_logistics`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_workorder_logistics_bu`;
CREATE TRIGGER `trg_biz_workorder_logistics_bu` BEFORE UPDATE ON `biz_workorder_logistics`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `biz_vehicle_license` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `workorder_id` bigint DEFAULT NULL COMMENT '工单ID',
  `license_plate` varchar(50) DEFAULT NULL COMMENT '车牌号',
  `vehicle_type` varchar(100) DEFAULT NULL COMMENT '车辆类型',
  `owner_name` varchar(100) DEFAULT NULL COMMENT '所有人',
  `usage_nature` varchar(100) DEFAULT NULL COMMENT '使用性质',
  `brand_model` varchar(100) DEFAULT NULL COMMENT '品牌型号',
  `vehicle_code` varchar(100) DEFAULT NULL COMMENT '车架号',
  `engine_code` varchar(100) DEFAULT NULL COMMENT '发动机号',
  `registration_date` datetime DEFAULT NULL COMMENT '注册日期',
  `issue_date` datetime DEFAULT NULL COMMENT '发证日期',
  `seats` int DEFAULT NULL COMMENT '座位数',
  `approved_load_capacity` int DEFAULT NULL COMMENT '核定载质量',
  `curb_weight` int DEFAULT NULL COMMENT '整备质量',
  `is_transfer` varchar(20) DEFAULT NULL COMMENT '是否过户',
  `transfer_date` datetime DEFAULT NULL COMMENT '过户日期',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行驶证表';

CREATE TABLE IF NOT EXISTS `biz_vehicle_license_archive` LIKE `biz_vehicle_license`;

DROP TRIGGER IF EXISTS `trg_biz_vehicle_license_bi`;
CREATE TRIGGER `trg_biz_vehicle_license_bi` BEFORE INSERT ON `biz_vehicle_license`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_vehicle_license_bu`;
CREATE TRIGGER `trg_biz_vehicle_license_bu` BEFORE UPDATE ON `biz_vehicle_license`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `biz_vehicle_invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `workorder_id` bigint DEFAULT NULL COMMENT '工单ID',
  `invoice_amount` decimal(15,2) DEFAULT NULL COMMENT '发票金额',
  `buyer_name` varchar(100) DEFAULT NULL COMMENT '购买方名称',
  `buyer_id_num` varchar(50) DEFAULT NULL COMMENT '购买方证件号',
  `vehicle_type` varchar(100) DEFAULT NULL COMMENT '车辆类型',
  `brand_model` varchar(100) DEFAULT NULL COMMENT '品牌型号',
  `vehicle_code` varchar(100) DEFAULT NULL COMMENT '车架号',
  `engine_code` varchar(100) DEFAULT NULL COMMENT '发动机号',
  `seats` int DEFAULT NULL COMMENT '座位数',
  `approved_load_capacity` int DEFAULT NULL COMMENT '核定载质量',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='车辆发票表';

CREATE TABLE IF NOT EXISTS `biz_vehicle_invoice_archive` LIKE `biz_vehicle_invoice`;

DROP TRIGGER IF EXISTS `trg_biz_vehicle_invoice_bi`;
CREATE TRIGGER `trg_biz_vehicle_invoice_bi` BEFORE INSERT ON `biz_vehicle_invoice`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_vehicle_invoice_bu`;
CREATE TRIGGER `trg_biz_vehicle_invoice_bu` BEFORE UPDATE ON `biz_vehicle_invoice`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `biz_vehicle_certificate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `workorder_id` bigint DEFAULT NULL COMMENT '工单ID',
  `brand_model` varchar(100) DEFAULT NULL COMMENT '品牌型号',
  `vehicle_type` varchar(100) DEFAULT NULL COMMENT '车辆类型',
  `vehicle_code` varchar(100) DEFAULT NULL COMMENT '车架号',
  `engine_code` varchar(100) DEFAULT NULL COMMENT '发动机号',
  `seats` int DEFAULT NULL COMMENT '座位数',
  `curb_weight` int DEFAULT NULL COMMENT '整备质量',
  `displacement` int DEFAULT NULL COMMENT '排量',
  `approved_load_capacity` int DEFAULT NULL COMMENT '核定载质量',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='车辆合格证表';

CREATE TABLE IF NOT EXISTS `biz_vehicle_certificate_archive` LIKE `biz_vehicle_certificate`;

DROP TRIGGER IF EXISTS `trg_biz_vehicle_certificate_bi`;
CREATE TRIGGER `trg_biz_vehicle_certificate_bi` BEFORE INSERT ON `biz_vehicle_certificate`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_vehicle_certificate_bu`;
CREATE TRIGGER `trg_biz_vehicle_certificate_bu` BEFORE UPDATE ON `biz_vehicle_certificate`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `biz_ocr_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `user_id` bigint DEFAULT NULL COMMENT '调用用户',
  `file_id` bigint DEFAULT NULL COMMENT '文件ID',
  `ocr_type` varchar(50) DEFAULT NULL COMMENT 'OCR类型',
  `provider` varchar(50) DEFAULT NULL COMMENT '供应商',
  `request_id` varchar(100) DEFAULT NULL COMMENT '供应商请求ID',
  `success` tinyint DEFAULT NULL COMMENT '是否成功',
  `result_json` json DEFAULT NULL COMMENT '识别结果',
  `error_message` varchar(1000) DEFAULT NULL COMMENT '错误信息',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='OCR识别记录表';

CREATE TABLE IF NOT EXISTS `biz_insurance_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID，null表示平台公共',
  `name` varchar(100) DEFAULT NULL COMMENT '名称',
  `type` tinyint DEFAULT NULL COMMENT '险种类型',
  `options_json` json DEFAULT NULL COMMENT '可选项',
  `default_option_json` varchar(100) DEFAULT NULL COMMENT '默认选项值',
  `deductible_options_json` json DEFAULT NULL COMMENT '免赔选项',
  `default_deductible_option_json` varchar(100) DEFAULT NULL COMMENT '默认免赔选项值',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='保险产品险种配置表';

CREATE TABLE IF NOT EXISTS `biz_insurance_product_archive` LIKE `biz_insurance_product`;

DROP TRIGGER IF EXISTS `trg_biz_insurance_product_bi`;
CREATE TRIGGER `trg_biz_insurance_product_bi` BEFORE INSERT ON `biz_insurance_product`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_insurance_product_bu`;
CREATE TRIGGER `trg_biz_insurance_product_bu` BEFORE UPDATE ON `biz_insurance_product`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `biz_workorder_insurance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `workorder_id` bigint DEFAULT NULL COMMENT '工单ID',
  `insurance_id` bigint DEFAULT NULL COMMENT '险种ID',
  `option_json` varchar(100) DEFAULT NULL COMMENT '投保险种选项值',
  `deductible_option_json` varchar(100) DEFAULT NULL COMMENT '免赔选项值',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单险种关联表';

CREATE TABLE IF NOT EXISTS `biz_workorder_insurance_archive` LIKE `biz_workorder_insurance`;

DROP TRIGGER IF EXISTS `trg_biz_workorder_insurance_bi`;
CREATE TRIGGER `trg_biz_workorder_insurance_bi` BEFORE INSERT ON `biz_workorder_insurance`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_workorder_insurance_bu`;
CREATE TRIGGER `trg_biz_workorder_insurance_bu` BEFORE UPDATE ON `biz_workorder_insurance`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `sys_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID，平台文件可为空',
  `path` varchar(500) DEFAULT NULL COMMENT 'OSS路径',
  `file_name` varchar(100) DEFAULT NULL COMMENT '原始文件名',
  `content_type` varchar(100) DEFAULT NULL COMMENT 'MIME类型',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小',
  `is_linked` tinyint NOT NULL DEFAULT 0 COMMENT '是否已被业务引用',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件表';

CREATE TABLE IF NOT EXISTS `sys_file_archive` LIKE `sys_file`;

DROP TRIGGER IF EXISTS `trg_sys_file_bi`;
CREATE TRIGGER `trg_sys_file_bi` BEFORE INSERT ON `sys_file`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_sys_file_bu`;
CREATE TRIGGER `trg_sys_file_bu` BEFORE UPDATE ON `sys_file`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS `biz_workorder_file` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `enterprise_id` bigint DEFAULT NULL COMMENT '企业ID',
  `workorder_id` bigint DEFAULT NULL COMMENT '工单ID',
  `file_id` bigint DEFAULT NULL COMMENT '文件ID',
  `file_type` varchar(100) DEFAULT NULL COMMENT '附件类型',
  `created_at` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_at` datetime DEFAULT NULL COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '软删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单附件表';

CREATE TABLE IF NOT EXISTS `biz_workorder_file_archive` LIKE `biz_workorder_file`;

DROP TRIGGER IF EXISTS `trg_biz_workorder_file_bi`;
CREATE TRIGGER `trg_biz_workorder_file_bi` BEFORE INSERT ON `biz_workorder_file`
FOR EACH ROW SET NEW.`created_at` = COALESCE(NEW.`created_at`, CURRENT_TIMESTAMP), NEW.`updated_at` = COALESCE(NEW.`updated_at`, CURRENT_TIMESTAMP);
DROP TRIGGER IF EXISTS `trg_biz_workorder_file_bu`;
CREATE TRIGGER `trg_biz_workorder_file_bu` BEFORE UPDATE ON `biz_workorder_file`
FOR EACH ROW SET NEW.`updated_at` = CURRENT_TIMESTAMP;



