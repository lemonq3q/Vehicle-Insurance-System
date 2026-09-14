-- 将原监控平台人工操作表升级为统一系统事件日志。
-- 该迁移会先复制 monitor_operation_log 的历史数据，再删除旧表；
-- 套餐专用审计表尚未投入使用，统一删除以避免重复日志模型。
CREATE TABLE `monitor_system_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `log_category` VARCHAR(32) NOT NULL COMMENT 'OPERATION MAINTENANCE SYSTEM SECURITY BUSINESS',
  `severity` VARCHAR(16) NOT NULL DEFAULT 'INFO' COMMENT 'DEBUG INFO WARN ERROR CRITICAL',
  `event_code` VARCHAR(64) NOT NULL COMMENT '稳定事件编码',
  `event_name` VARCHAR(100) NOT NULL COMMENT '事件展示名称',
  `source_system` VARCHAR(32) NOT NULL COMMENT 'MONITOR SAAS INSURANCE COORDINATOR',
  `source_module` VARCHAR(64) DEFAULT NULL COMMENT '来源业务模块',
  `result_status` VARCHAR(16) NOT NULL COMMENT 'SUCCESS FAILED PARTIAL RUNNING',
  `operator_type` VARCHAR(16) NOT NULL DEFAULT 'SYSTEM' COMMENT 'MONITOR_USER TENANT_USER SYSTEM JOB',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作人或任务记录ID',
  `operator_name_snapshot` VARCHAR(100) DEFAULT NULL COMMENT '操作主体名称快照',
  `enterprise_id` BIGINT DEFAULT NULL COMMENT '关联tenant_enterprise.id',
  `enterprise_name_snapshot` VARCHAR(150) DEFAULT NULL COMMENT '企业名称快照',
  `target_type` VARCHAR(32) DEFAULT NULL COMMENT 'ENTERPRISE SUBSCRIPTION PLAN USER REMINDER JOB SYSTEM',
  `target_id` VARCHAR(100) DEFAULT NULL COMMENT '数字ID、业务编号或批次号',
  `target_name_snapshot` VARCHAR(150) DEFAULT NULL COMMENT '操作目标名称快照',
  `operation_reason` VARCHAR(500) DEFAULT NULL COMMENT '人工操作原因',
  `summary` VARCHAR(500) NOT NULL COMMENT '可直接展示的事件摘要',
  `detail_json` JSON DEFAULT NULL COMMENT '事件扩展数据',
  `before_json` JSON DEFAULT NULL COMMENT '人工操作前快照',
  `after_json` JSON DEFAULT NULL COMMENT '人工操作后快照',
  `error_code` VARCHAR(64) DEFAULT NULL,
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '脱敏错误信息',
  `exception_digest` VARCHAR(64) DEFAULT NULL COMMENT '相同异常聚合摘要',
  `request_id` VARCHAR(100) DEFAULT NULL,
  `trace_id` VARCHAR(100) DEFAULT NULL,
  `job_execution_id` BIGINT DEFAULT NULL COMMENT 'monitor_job_execution.id',
  `ip_address` VARCHAR(64) DEFAULT NULL,
  `user_agent` VARCHAR(500) DEFAULT NULL,
  `occurred_at` DATETIME(3) NOT NULL COMMENT '事件实际发生时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_system_log_category_time` (`log_category`,`occurred_at`),
  KEY `idx_system_log_severity_time` (`severity`,`occurred_at`),
  KEY `idx_system_log_event_time` (`event_code`,`occurred_at`),
  KEY `idx_system_log_enterprise_time` (`enterprise_id`,`occurred_at`),
  KEY `idx_system_log_operator_time` (`operator_type`,`operator_id`,`occurred_at`),
  KEY `idx_system_log_target_time` (`target_type`,`target_id`,`occurred_at`),
  KEY `idx_system_log_request` (`request_id`),
  KEY `idx_system_log_job_execution` (`job_execution_id`),
  KEY `idx_system_log_exception` (`exception_digest`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='监控平台统一系统事件日志';

INSERT INTO `monitor_system_log` (
  log_category,severity,event_code,event_name,source_system,source_module,result_status,
  operator_type,operator_id,enterprise_id,target_type,target_id,operation_reason,summary,
  before_json,after_json,request_id,ip_address,occurred_at,created_at
)
SELECT 'OPERATION','INFO',action_code,action_code,'MONITOR','legacy','SUCCESS',
       'MONITOR_USER',operator_user_id,enterprise_id,target_type,CAST(target_id AS CHAR),reason,
       CONCAT('历史人工操作：',action_code),before_json,after_json,request_id,ip_address,
       created_at,created_at
FROM `monitor_operation_log`;

DROP TABLE `monitor_operation_log`;
DROP TABLE IF EXISTS `monitor_subscription_operation_audit`;
