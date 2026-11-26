-- 仅在监控数据库执行，须先停止请求日志生产/消费并处理Redis及本地待投旧日志。
-- 按用户要求永久清空请求日志主表和详情表；不清理审计日志或其他业务表。
-- 注意：重复执行同样会再次清空日志。DDL存在性检查不代表本脚本可无损重复执行。
START TRANSACTION;
DELETE FROM monitor_request_log_detail;
DELETE FROM monitor_request_log;
COMMIT;

-- 清空完成后增加列与索引，不计算历史归属；DDL隐式提交，失败时已删除日志不会恢复。
SET @identity_column_exists = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='monitor_request_log' AND column_name='identity_type');
SET @identity_ddl = IF(@identity_column_exists=0,'ALTER TABLE monitor_request_log ADD COLUMN identity_type VARCHAR(24) NOT NULL DEFAULT ''UNKNOWN'' COMMENT ''TENANT_USER MONITOR_USER SYSTEM ANONYMOUS UNKNOWN''','SELECT 1');
PREPARE identity_stmt FROM @identity_ddl;
EXECUTE identity_stmt;
DEALLOCATE PREPARE identity_stmt;
SET @identity_index_exists = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='monitor_request_log' AND index_name='idx_request_identity_user');
SET @identity_ddl = IF(@identity_index_exists=0,'ALTER TABLE monitor_request_log ADD INDEX idx_request_identity_user(identity_type,user_id,started_at,id)','SELECT 1');
PREPARE identity_stmt FROM @identity_ddl;
EXECUTE identity_stmt;
DEALLOCATE PREPARE identity_stmt;

SELECT COUNT(*) AS remaining_request_logs FROM monitor_request_log;
