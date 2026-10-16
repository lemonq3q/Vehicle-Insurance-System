-- 共享邮箱账号功能的增量部署脚本（MySQL 8.0）。
-- 目标：insurance_saas.tenant_user；不重建表、不修改已有邮箱、密码或企业关系。
-- 执行前备份数据库，在低峰期执行；ALTER TABLE 可能短暂占用表资源。
-- 使用能执行多条语句且遇到错误立即停止的客户端，在同一个连接中完整执行。
-- 已完成迁移的环境会跳过；不要再同时执行 V20261011_01_unique_active_user_email.sql。
USE `insurance_saas`;

-- 阶段一：检查有效账号的重复邮箱。不自动合并账号或清洗历史数据。
-- 返回记录时本脚本跳过变更，须人工核实归属后重新执行。
SELECT LOWER(TRIM(email)) AS normalized_email, COUNT(*) AS account_count
FROM tenant_user
WHERE deleted = 0 AND email IS NOT NULL AND TRIM(email) <> ''
GROUP BY LOWER(TRIM(email))
HAVING COUNT(*) > 1;

SET @email_deploy_duplicates = (
  SELECT COUNT(*) FROM (
    SELECT LOWER(TRIM(email)) AS normalized_email
    FROM tenant_user
    WHERE deleted = 0 AND email IS NOT NULL AND TRIM(email) <> ''
    GROUP BY LOWER(TRIM(email)) HAVING COUNT(*) > 1
  ) AS duplicate_groups
);

-- 阶段二：识别当前部署状态，兼容未迁移、已有生成列和已完整迁移的环境。
SET @email_deploy_column_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tenant_user'
    AND COLUMN_NAME = 'active_email'
);
SET @email_deploy_index_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tenant_user'
    AND INDEX_NAME = 'uk_tenant_user_active_email'
);

-- 新增列及索引使用一个 ALTER；有并发重复写入时唯一索引创建失败，不误报成功。
-- 已有对象不覆盖，脚本末尾必须确认生成列、索引及结果检查均正确。
SET @email_deploy_sql = CASE
  WHEN @email_deploy_duplicates > 0 THEN
    'SELECT ''BLOCKED_DUPLICATE_EMAIL: 请先处理重复邮箱，再执行迁移'' AS migration_status'
  WHEN @email_deploy_column_exists = 0 THEN
    'ALTER TABLE tenant_user ADD COLUMN active_email VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN NULLIF(LOWER(TRIM(email)), '''') ELSE NULL END) STORED, ADD UNIQUE INDEX uk_tenant_user_active_email (active_email)'
  WHEN @email_deploy_index_exists = 0 THEN
    'ALTER TABLE tenant_user ADD UNIQUE INDEX uk_tenant_user_active_email (active_email)'
  ELSE
    'SELECT ''SKIPPED_ALREADY_PRESENT: 已存在列和索引，请核对下方结果'' AS migration_status'
END;
PREPARE email_deploy_statement FROM @email_deploy_sql;
EXECUTE email_deploy_statement;
DEALLOCATE PREPARE email_deploy_statement;

-- 阶段三：部署验收。列应为 STORED GENERATED，表达式按 deleted/email 自动计算。
-- 索引必须只有一行，Non_unique=0、Column_name=active_email、Seq_in_index=1。
SELECT COLUMN_NAME, COLUMN_TYPE, EXTRA, GENERATION_EXPRESSION
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tenant_user'
  AND COLUMN_NAME = 'active_email';
SHOW INDEX FROM tenant_user WHERE Key_name = 'uk_tenant_user_active_email';

-- OK 表示无重复邮箱，且生成列和单列唯一索引均存在；否则不能视为部署成功。
SELECT CASE
  WHEN @email_deploy_duplicates = 0
    AND EXISTS (
      SELECT 1 FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tenant_user'
        AND COLUMN_NAME = 'active_email' AND EXTRA LIKE '%STORED GENERATED%'
    )
    AND (
      SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tenant_user'
        AND INDEX_NAME = 'uk_tenant_user_active_email'
    ) = 1
    AND EXISTS (
      SELECT 1 FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tenant_user'
        AND INDEX_NAME = 'uk_tenant_user_active_email'
        AND NON_UNIQUE = 0 AND COLUMN_NAME = 'active_email' AND SEQ_IN_INDEX = 1
        AND SUB_PART IS NULL
    )
  THEN 'OK: 邮箱唯一约束已就绪；请同时核对上方生成表达式'
  ELSE 'BLOCKED: 校验未通过，请检查重复邮箱、列及索引定义'
END AS deployment_result;

-- 数据检查仅在列存在时运行，重复邮箱阻止首次迁移时也能清楚返回状态。
SET @email_deploy_verify_sql = IF(
  EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tenant_user' AND COLUMN_NAME = 'active_email'
  ),
  'SELECT COUNT(*) AS total_users, SUM(NOT(active_email <=> CASE WHEN deleted=0 THEN NULLIF(LOWER(TRIM(email)), '''') ELSE NULL END)) AS generated_value_mismatches FROM tenant_user',
  'SELECT ''BLOCKED: active_email 尚未创建'' AS data_check'
);
PREPARE email_deploy_verify_statement FROM @email_deploy_verify_sql;
EXECUTE email_deploy_verify_statement;
DEALLOCATE PREPARE email_deploy_verify_statement;
-- generated_value_mismatches 必须为 0；空邮箱和软删除账号生成 NULL，可共存。
