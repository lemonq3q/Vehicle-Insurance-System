-- Monitor platform schema baseline.
-- REVIEW REQUIRED: this file drops and rebuilds tables unused by the two existing systems.
-- Target: insurance_saas (MySQL 8.0)

USE insurance_saas;

-- ---------------------------------------------------------------------------
-- 1. Remove abandoned monitor drafts.
-- Code inspection confirmed that backend/insruance and backend/saas do not use
-- these tables. Their current row counts are all zero in the inspected database.
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS saas_usage_archive_job;
DROP TABLE IF EXISTS saas_usage_monthly;
DROP TABLE IF EXISTS saas_usage_daily;
DROP TABLE IF EXISTS platform_user_role_archive;
DROP TABLE IF EXISTS platform_user_role;
DROP TABLE IF EXISTS platform_user_archive;
DROP TABLE IF EXISTS platform_user;

-- ---------------------------------------------------------------------------
-- 2. Monitor users and roles.
-- These accounts are independent from tenant_user and the existing tenant
-- auth_role/auth_permission model. There is no public registration endpoint.
-- ---------------------------------------------------------------------------

CREATE TABLE monitor_role (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL COMMENT 'ADMIN CUSTOMER_SERVICE',
  name VARCHAR(64) NOT NULL COMMENT '角色名称',
  description VARCHAR(255) DEFAULT NULL,
  builtin TINYINT NOT NULL DEFAULT 1 COMMENT '1内置角色',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_monitor_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='监控平台角色';

CREATE TABLE monitor_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL COMMENT '登录账号',
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt密码摘要',
  real_name VARCHAR(64) NOT NULL COMMENT '姓名',
  phone VARCHAR(20) DEFAULT NULL,
  email VARCHAR(100) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  last_login_at DATETIME DEFAULT NULL,
  password_changed_at DATETIME DEFAULT NULL,
  created_by BIGINT DEFAULT NULL COMMENT '创建人monitor_user.id，首个账号为空',
  updated_by BIGINT DEFAULT NULL,
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1软删除',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  active_username VARCHAR(64)
    GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN username ELSE NULL END) STORED,
  active_phone VARCHAR(20)
    GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN NULLIF(phone, '') ELSE NULL END) STORED,
  active_email VARCHAR(100)
    GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN NULLIF(email, '') ELSE NULL END) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_monitor_user_active_username (active_username),
  UNIQUE KEY uk_monitor_user_active_phone (active_phone),
  UNIQUE KEY uk_monitor_user_active_email (active_email),
  KEY idx_monitor_user_status_created (deleted, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='监控平台用户';

CREATE TABLE monitor_user_role (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  created_by BIGINT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_monitor_user_role (user_id, role_id),
  KEY idx_monitor_user_role_role (role_id, user_id),
  CONSTRAINT fk_monitor_user_role_user FOREIGN KEY (user_id) REFERENCES monitor_user(id),
  CONSTRAINT fk_monitor_user_role_role FOREIGN KEY (role_id) REFERENCES monitor_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='监控平台用户角色关联';

INSERT INTO monitor_role(code, name, description, builtin, status)
VALUES
  ('ADMIN', '管理员', '包含监控平台账号管理权限', 1, 1),
  ('CUSTOMER_SERVICE', '售后客服', '除账号管理外的全部业务权限', 1, 1);

-- ---------------------------------------------------------------------------
-- 3. Enterprise and system statistics.
-- One row per enterprise and business day. Redis absolute counters overwrite
-- these columns every 15-20 minutes, making retries naturally idempotent.
-- ---------------------------------------------------------------------------

CREATE TABLE monitor_enterprise_daily_usage (
  id BIGINT NOT NULL AUTO_INCREMENT,
  stat_date DATE NOT NULL COMMENT '统计日，Asia/Shanghai 04:00边界',
  enterprise_id BIGINT NOT NULL COMMENT 'tenant_enterprise.id',
  workorder_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '出单量',
  request_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '后端调用次数',
  ocr_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'OCR供应商调用次数',
  last_flushed_at DATETIME DEFAULT NULL COMMENT '最近Redis覆盖写入时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_monitor_enterprise_usage_day (stat_date, enterprise_id),
  KEY idx_monitor_enterprise_usage_query (enterprise_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='企业每日用量统计';

CREATE TABLE monitor_system_daily_stat (
  id BIGINT NOT NULL AUTO_INCREMENT,
  stat_date DATE NOT NULL COMMENT '统计日，Asia/Shanghai 04:00边界',
  workorder_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '全系统出单量',
  request_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '全系统后端调用量',
  ocr_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '全系统OCR使用量',
  calculated_at DATETIME NOT NULL COMMENT '04:00汇总完成时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_monitor_system_daily_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='全系统每日用量统计';

CREATE TABLE monitor_monthly_revenue (
  id BIGINT NOT NULL AUTO_INCREMENT,
  stat_month CHAR(7) NOT NULL COMMENT '月份yyyy-MM',
  recharge_amount DECIMAL(16,2) NOT NULL DEFAULT 0.00 COMMENT '已支付充值订单总额，不含套餐订阅',
  recharge_order_count BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已支付充值订单数',
  calculated_at DATETIME NOT NULL COMMENT '最近计算时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_monitor_monthly_revenue_month (stat_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='每月充值流水统计';

-- ---------------------------------------------------------------------------
-- 4. Scheduler execution and sensitive-operation audit.
-- ---------------------------------------------------------------------------

CREATE TABLE monitor_job_execution (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_type VARCHAR(32) NOT NULL COMMENT 'USAGE_FLUSH DAILY_MAINTENANCE STARTUP_SNAPSHOT',
  batch_no VARCHAR(64) NOT NULL,
  stat_date DATE DEFAULT NULL,
  status VARCHAR(16) NOT NULL COMMENT 'RUNNING SUCCESS FAILED',
  processed_count INT UNSIGNED NOT NULL DEFAULT 0,
  error_message VARCHAR(1000) DEFAULT NULL,
  started_at DATETIME NOT NULL,
  finished_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_monitor_job_batch (batch_no),
  KEY idx_monitor_job_type_time (job_type, started_at),
  KEY idx_monitor_job_status_time (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='监控统计任务执行记录';

CREATE TABLE monitor_operation_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  operator_user_id BIGINT NOT NULL COMMENT 'monitor_user.id',
  action_code VARCHAR(64) NOT NULL COMMENT 'BALANCE_ADJUST SUBSCRIPTION_SET SUBSCRIPTION_CANCEL PLAN_UPDATE USER_CREATE USER_STATUS USER_ROLE USER_DELETE',
  target_type VARCHAR(32) NOT NULL COMMENT 'ENTERPRISE WALLET SUBSCRIPTION PLAN MONITOR_USER',
  target_id BIGINT NOT NULL,
  enterprise_id BIGINT DEFAULT NULL COMMENT '关联tenant_enterprise.id',
  before_json JSON DEFAULT NULL,
  after_json JSON DEFAULT NULL,
  reason VARCHAR(500) NOT NULL COMMENT '操作原因',
  request_id VARCHAR(100) DEFAULT NULL,
  ip_address VARCHAR(64) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_monitor_operation_operator_time (operator_user_id, created_at),
  KEY idx_monitor_operation_target (target_type, target_id, created_at),
  KEY idx_monitor_operation_enterprise_time (enterprise_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='监控平台敏感操作审计';

-- ---------------------------------------------------------------------------
-- 5. Read-side indexes on existing business tables.
-- No existing business table is dropped or rebuilt below.
-- ---------------------------------------------------------------------------

ALTER TABLE tenant_enterprise
  ADD KEY idx_tenant_enterprise_status_created (deleted, status, created_at);

ALTER TABLE tenant_user
  ADD KEY idx_tenant_user_status_created (deleted, status, created_at);

ALTER TABLE tenant_member
  ADD KEY idx_tenant_member_enterprise_status (enterprise_id, deleted, status, user_id);

ALTER TABLE saas_recharge_order
  ADD KEY idx_recharge_paid_rollup (deleted, status, paid_at, enterprise_id),
  ADD KEY idx_recharge_enterprise_created (enterprise_id, deleted, created_at);

ALTER TABLE saas_order
  ADD KEY idx_saas_order_enterprise_created (enterprise_id, deleted, created_at);

ALTER TABLE saas_wallet_transaction
  ADD KEY idx_wallet_tx_enterprise_created (enterprise_id, created_at);

ALTER TABLE biz_ocr_record
  ADD KEY idx_ocr_enterprise_created (enterprise_id, created_at);
