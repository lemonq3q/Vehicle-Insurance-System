-- 在 insurance_saas 数据库执行；须先审阅确认。脚本不修改用户已有邮箱、密码或企业归属。
-- 本文件仅适用于首次迁移；重复部署使用 deploy_20261011_email_accounts_incremental.sql。
USE `insurance_saas`;

-- 有效邮箱按小写与首尾去空白统一，无邮箱和软删除账号生成 NULL，不占用唯一值。
-- 先运行以下只读预检；如有结果，先核实账号归属，禁止自动合并或覆盖用户资料。
SELECT LOWER(TRIM(email)) AS normalized_email, COUNT(*) AS account_count
FROM tenant_user
WHERE deleted = 0 AND email IS NOT NULL AND TRIM(email) <> ''
GROUP BY LOWER(TRIM(email)) HAVING COUNT(*) > 1;

-- 仅在预检没有重复邮箱后执行一次。唯一约束从数据库层阻止两端并发注册或绑定同一邮箱。
ALTER TABLE tenant_user
  ADD COLUMN active_email VARCHAR(100)
    GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN NULLIF(LOWER(TRIM(email)), '') ELSE NULL END) STORED,
  ADD UNIQUE INDEX uk_tenant_user_active_email (active_email);

-- 迁移后只读检查索引与生成列。
SHOW INDEX FROM tenant_user WHERE Key_name = 'uk_tenant_user_active_email';
