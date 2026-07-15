-- Read-only preflight. Every target_rows value must be 0 before migration.
SELECT table_name,table_rows AS target_rows
FROM information_schema.tables
WHERE table_schema='insurance_saas'
  AND table_name IN ('tenant_user','tenant_enterprise','tenant_member','auth_role','auth_permission','auth_role_permission',
                     'biz_merchant','biz_merchant_area','biz_merchant_staff','biz_merchant_staff_role','biz_workorder');

SELECT type,COUNT(*) total FROM insurance.merchant WHERE is_delete=0 GROUP BY type ORDER BY total DESC;
SELECT code,COUNT(*) total FROM insurance.merchant GROUP BY code,is_delete HAVING COUNT(*)>1;
SELECT r.name role_name,u.merchant_id,COUNT(*) total
FROM insurance.`user` u JOIN insurance.user_role ur ON ur.user_id=u.id AND ur.is_delete=0
JOIN insurance.role r ON r.id=ur.role_id AND r.is_delete=0
WHERE u.is_delete=0 AND r.name IN ('联系人','收款人')
GROUP BY r.name,u.merchant_id HAVING COUNT(*)>1;
