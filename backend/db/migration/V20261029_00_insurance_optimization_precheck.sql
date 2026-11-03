-- 只读预检。目标数据库必须由人工确认；不含 USE，不在本轮自动执行。
-- 所有结果均需审阅，尤其是重名、联系人冲突及旧续保状态；不能静默合并数据。
SELECT DATABASE() AS target_database, VERSION() AS database_version;
SELECT c.direction, m.enterprise_id, TRIM(m.name) AS normalized_name, COUNT(*) AS duplicate_count
FROM biz_merchant m JOIN biz_merchant_category c ON c.id=m.category_id
WHERE m.deleted=0 GROUP BY m.enterprise_id,c.direction,TRIM(m.name) HAVING COUNT(*)>1;

SELECT m.id,m.enterprise_id,m.name,m.contact,m.phone,s.name AS old_contact,s.phone AS old_phone
FROM biz_merchant m JOIN biz_merchant_category c ON c.id=m.category_id AND c.direction='DOWNSTREAM'
LEFT JOIN biz_merchant_staff_role r ON r.merchant_id=m.id AND r.enterprise_id=m.enterprise_id AND r.role_code='CONTACT' AND r.deleted=0
LEFT JOIN biz_merchant_staff s ON s.id=r.staff_id AND s.enterprise_id=m.enterprise_id AND s.deleted=0
WHERE m.deleted=0 AND (s.id IS NULL OR (NULLIF(TRIM(m.contact),'') IS NOT NULL AND m.contact<>s.name)
    OR (NULLIF(TRIM(m.phone),'') IS NOT NULL AND m.phone<>s.phone));

SELECT 'online' AS source,remind_status,renewal_status_cycle,COUNT(*) AS affected_count
FROM biz_workorder WHERE COALESCE(remind_status,0)<>0 GROUP BY remind_status,renewal_status_cycle
UNION ALL
SELECT 'archive',remind_status,renewal_status_cycle,COUNT(*) FROM biz_workorder_archive
WHERE COALESCE(remind_status,0)<>0 GROUP BY remind_status,renewal_status_cycle;

-- 区分可从来源人员恢复、仅能保留原页面显示姓名、完全无法恢复的旧工单。
SELECT CASE WHEN s.id IS NOT NULL THEN '来源人员可恢复' WHEN u.id IS NOT NULL THEN '仅保留旧页面显示的录入人姓名' ELSE '需人工核对' END AS recovery,
COUNT(*) AS affected_count FROM biz_workorder w
LEFT JOIN biz_merchant_staff s ON s.id=w.source_staff_id AND s.enterprise_id=w.enterprise_id
LEFT JOIN tenant_user u ON u.id=w.created_by GROUP BY recovery;

SELECT table_name,column_name,column_type FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN ('biz_merchant','biz_merchant_archive','biz_workorder','biz_workorder_archive','biz_workorder_payment','biz_workorder_payment_archive')
ORDER BY table_name,ordinal_position;

-- 唯一键中的企业、方向、名称不可缺失，所有异常需先人工处理。
SELECT m.id,m.enterprise_id,m.name,c.direction FROM biz_merchant m
LEFT JOIN biz_merchant_category c ON c.id=m.category_id
WHERE m.enterprise_id IS NULL OR c.direction NOT IN ('UPSTREAM','DOWNSTREAM')
   OR c.direction IS NULL OR NULLIF(TRIM(m.name),'') IS NULL;
SELECT merchant_id,enterprise_id,COUNT(*) AS contact_count FROM biz_merchant_staff_role
WHERE role_code='CONTACT' AND deleted=0 GROUP BY merchant_id,enterprise_id HAVING COUNT(*)>1;
SELECT trigger_name,event_object_table,action_statement FROM information_schema.triggers
WHERE trigger_schema=DATABASE() AND event_object_table IN
('biz_merchant','biz_merchant_archive','biz_workorder','biz_workorder_archive','biz_workorder_payment','biz_workorder_payment_archive');
