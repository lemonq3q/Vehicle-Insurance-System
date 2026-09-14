-- 监控平台销售推广目标主数据。
-- 当前仅存储目标主数据，不存储推广任务、发送结果或供应商回执。
CREATE TABLE IF NOT EXISTS `monitor_promotion_target` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL COMMENT '推广目标名称',
  `phone` VARCHAR(32) DEFAULT NULL COMMENT '电话，允许国家区号和分机号',
  `email` VARCHAR(254) DEFAULT NULL COMMENT '邮箱地址',
  `extra_fields` JSON DEFAULT NULL COMMENT '已在接口契约中声明的扩展推广字段',
  `source_type` VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL EXCEL_IMPORT',
  `import_batch_no` VARCHAR(64) DEFAULT NULL COMMENT 'Excel导入批次号，手工录入为空',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1可推广 0停用',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '运营备注',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人monitor_user.id，系统导入可为空',
  `updated_by` BIGINT DEFAULT NULL COMMENT '最后修改人monitor_user.id',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1软删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_promotion_target_list` (`deleted`,`status`,`id`),
  KEY `idx_promotion_target_name` (`deleted`,`name`,`id`),
  KEY `idx_promotion_target_phone` (`deleted`,`phone`,`id`),
  KEY `idx_promotion_target_email` (`deleted`,`email`,`id`),
  KEY `idx_promotion_target_batch` (`import_batch_no`,`id`),
  KEY `idx_promotion_target_created` (`deleted`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='监控平台销售推广目标';
