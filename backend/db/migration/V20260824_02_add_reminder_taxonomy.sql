-- 提醒事实表继续保存稳定的 reminder_type 字符串；本迁移只增加可扩展的两级业务字典。
-- 类型和类别只允许停用，不允许删除或修改编码，确保历史提醒始终能够正确解释。
CREATE TABLE IF NOT EXISTS `sys_reminder_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_code` varchar(64) NOT NULL COMMENT '不可变的业务类别编码',
  `category_name` varchar(64) NOT NULL COMMENT '前端显示名称',
  `description` varchar(255) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `sort_no` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reminder_category_code` (`category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提醒业务类别字典';

CREATE TABLE IF NOT EXISTS `sys_reminder_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category_id` bigint NOT NULL COMMENT 'sys_reminder_category.id',
  `type_code` varchar(64) NOT NULL COMMENT '与提醒事实表 reminder_type 对应的不可变编码',
  `type_name` varchar(100) NOT NULL COMMENT '前端显示名称',
  `description` varchar(255) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `sort_no` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reminder_type_code` (`type_code`),
  KEY `idx_reminder_type_category` (`category_id`,`status`,`sort_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提醒具体类型字典';

INSERT INTO sys_reminder_category(category_code,category_name,description,status,sort_no) VALUES
('SUBSCRIPTION','套餐与续费','套餐到期、自动续费和欠费暂停相关提醒',1,10),
('WORKORDER_QUOTA','工单额度','套餐周期内工单额度消耗相关提醒',1,20),
('ACCOUNT_BALANCE','账户资金','钱包余额和欠费阈值相关提醒',1,30),
('DATA_LIFECYCLE','数据生命周期','套餐结束后的企业资料保留与清理提醒',1,40)
ON DUPLICATE KEY UPDATE category_name=VALUES(category_name),description=VALUES(description),status=VALUES(status),sort_no=VALUES(sort_no);

INSERT INTO sys_reminder_type(category_id,type_code,type_name,description,status,sort_no)
SELECT c.id,x.type_code,x.type_name,x.description,1,x.sort_no
FROM sys_reminder_category c JOIN (
  SELECT 'SUBSCRIPTION' category_code,'AUTO_RENEW_BALANCE_INSUFFICIENT' type_code,'自动续费余额不足' type_name,'自动续费前可用余额不足以支付续费金额' description,10 sort_no
  UNION ALL SELECT 'SUBSCRIPTION','SUBSCRIPTION_EXPIRING_NO_AUTO_RENEW','套餐即将到期','套餐临近到期且未开启自动续费',20
  UNION ALL SELECT 'SUBSCRIPTION','SUBSCRIPTION_SUSPENDED_ARREARS','欠费导致套餐暂停','套餐已经因企业欠费暂停',30
  UNION ALL SELECT 'WORKORDER_QUOTA','WORKORDER_QUOTA_NEAR_LIMIT','工单额度即将用尽','本周期工单使用量达到预警比例',10
  UNION ALL SELECT 'WORKORDER_QUOTA','WORKORDER_QUOTA_REACHED','工单额度已用尽','本周期工单使用量达到或超过套餐额度',20
  UNION ALL SELECT 'ACCOUNT_BALANCE','WALLET_BALANCE_NEGATIVE','账户余额为负','企业钱包余额进入负数',10
  UNION ALL SELECT 'ACCOUNT_BALANCE','WALLET_BALANCE_NEAR_SUSPENSION','余额接近停服阈值','欠费接近套餐暂停阈值',20
  UNION ALL SELECT 'DATA_LIFECYCLE','ENTERPRISE_DATA_DELETION_APPROACHING','企业数据即将清理','企业数据即将超过套餐结束后的保留期',10
) x ON x.category_code=c.category_code
ON DUPLICATE KEY UPDATE category_id=VALUES(category_id),type_name=VALUES(type_name),description=VALUES(description),status=VALUES(status),sort_no=VALUES(sort_no);

ALTER TABLE `monitor_enterprise_reminder`
  ADD KEY `idx_monitor_reminder_type_status_time` (`reminder_type`,`process_status`,`last_triggered_at`);
