-- 服务器增量迁移失败后的只读核查，不修改或删除任何数据。
-- 在发生报错的 insurance_saas 连接中执行。先核对目标，再审阅异常及关联数量。
-- 本地清理授权不适用于服务器；不能仅按本地结果直接删除服务器记录。
SELECT DATABASE() AS database_name,@@hostname AS server_hostname,@@port AS server_port;

-- 对应增量脚本的阻断条件，分类缺失包含分类ID为空或引用不存在的分类。
SELECT COUNT(*) AS blocking_merchants,
       COALESCE(SUM(m.enterprise_id IS NULL),0) AS missing_enterprise,
       COALESCE(SUM(c.direction IS NULL),0) AS missing_category_or_direction,
       COALESCE(SUM(c.direction IS NOT NULL AND c.direction NOT IN ('UPSTREAM','DOWNSTREAM')),0) AS invalid_direction,
       COALESCE(SUM(NULLIF(TRIM(m.name),'') IS NULL),0) AS missing_name
FROM biz_merchant m LEFT JOIN biz_merchant_category c ON c.id=m.category_id
WHERE m.enterprise_id IS NULL OR c.direction IS NULL OR c.direction NOT IN ('UPSTREAM','DOWNSTREAM')
   OR NULLIF(TRIM(m.name),'') IS NULL;

-- 仅展示前100条，不导出联系人、手机号或银行等资料。ID转为字符串避免客户端数字精度丢失。
SELECT CAST(m.id AS CHAR) AS merchant_id,CAST(m.enterprise_id AS CHAR) AS enterprise_id,
       m.name,CAST(m.category_id AS CHAR) AS category_id,c.name AS category_name,c.direction,
       m.deleted,m.created_at
FROM biz_merchant m LEFT JOIN biz_merchant_category c ON c.id=m.category_id
WHERE m.enterprise_id IS NULL OR c.direction IS NULL OR c.direction NOT IN ('UPSTREAM','DOWNSTREAM')
   OR NULLIF(TRIM(m.name),'') IS NULL
ORDER BY m.id LIMIT 100;

-- 检查与本地同类的企业和分类均为空记录；不能仅凭数量相同推断内容相同。
SELECT COUNT(*) AS null_scope_and_category_merchants,COUNT(DISTINCT m.name) AS distinct_names
FROM biz_merchant m WHERE m.enterprise_id IS NULL AND m.category_id IS NULL;

-- 下列关联核查针对企业和分类均为空的记录，覆盖当前和归档业务。
SELECT 'current_workorder_references' AS check_name,COUNT(*) AS affected_count
FROM biz_workorder w WHERE EXISTS (SELECT 1 FROM biz_merchant m
    WHERE m.enterprise_id IS NULL AND m.category_id IS NULL
      AND m.id IN (w.create_merchant_id,w.handle_merchant_id,w.insurance_merchant_id))
UNION ALL
SELECT 'archive_workorder_references',COUNT(*) FROM biz_workorder_archive w
WHERE EXISTS (SELECT 1 FROM biz_merchant m WHERE m.enterprise_id IS NULL AND m.category_id IS NULL
    AND m.id IN (w.create_merchant_id,w.handle_merchant_id,w.insurance_merchant_id))
UNION ALL
SELECT 'current_area_references',COUNT(*) FROM biz_merchant_area a JOIN biz_merchant m ON m.id=a.merchant_id
WHERE m.enterprise_id IS NULL AND m.category_id IS NULL
UNION ALL
SELECT 'archive_area_references',COUNT(*) FROM biz_merchant_area_archive a JOIN biz_merchant m ON m.id=a.merchant_id
WHERE m.enterprise_id IS NULL AND m.category_id IS NULL
UNION ALL
SELECT 'current_staff_references',COUNT(*) FROM biz_merchant_staff s JOIN biz_merchant m ON m.id=s.merchant_id
WHERE m.enterprise_id IS NULL AND m.category_id IS NULL
UNION ALL
SELECT 'archive_staff_references',COUNT(*) FROM biz_merchant_staff_archive s JOIN biz_merchant m ON m.id=s.merchant_id
WHERE m.enterprise_id IS NULL AND m.category_id IS NULL;

-- 本次报错位于加字段之前：以下应为空；若非空，可能有其他执行历史，不能盲目重跑。
SELECT table_name,column_name FROM information_schema.columns
WHERE table_schema=DATABASE() AND
 ((table_name='biz_merchant' AND column_name IN ('direction','active_name'))
 OR (table_name='biz_workorder' AND column_name IN ('submitter_name','commercial_renewal_status','compulsory_renewal_status')));

SELECT trigger_name,action_statement FROM information_schema.triggers
WHERE trigger_schema=DATABASE() AND trigger_name IN
 ('trg_biz_merchant_bu','trg_biz_workorder_bu','trg_biz_workorder_payment_bu');
