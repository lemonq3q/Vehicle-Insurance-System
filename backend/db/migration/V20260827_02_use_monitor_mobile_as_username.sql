-- 监控平台账号统一以手机号码作为唯一登录标识。
-- 迁移先复用历史 phone 值覆盖 username，再删除重复列；唯一索引和 CHECK 约束
-- 会阻止重复手机号或非 11 位大陆手机号进入数据库，避免产生无法按新登录规则使用的账号。
USE insurance_saas;

ALTER TABLE monitor_user
  DROP INDEX uk_monitor_user_active_username;

UPDATE monitor_user
SET username = phone
WHERE phone REGEXP '^1[0-9]{10}$'
  AND username <> phone;

ALTER TABLE monitor_user
  DROP INDEX uk_monitor_user_active_phone,
  DROP COLUMN active_phone,
  DROP COLUMN phone,
  MODIFY COLUMN username VARCHAR(11) NOT NULL COMMENT '登录手机号，唯一登录标识',
  MODIFY COLUMN active_username VARCHAR(11)
    GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN username ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_monitor_user_active_username (active_username),
  ADD CONSTRAINT chk_monitor_user_username_phone CHECK (username REGEXP '^1[0-9]{10}$');
