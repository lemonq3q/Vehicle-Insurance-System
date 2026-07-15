USE insurance_saas;

SELECT 'merchant' item,(SELECT COUNT(*) FROM insurance.merchant) source_count,(SELECT COUNT(*) FROM biz_merchant) target_count;
SELECT 'system_user' item,
       (SELECT COUNT(DISTINCT u.id) FROM insurance.`user` u JOIN insurance.user_role ur ON ur.user_id=u.id AND ur.is_delete=0 JOIN insurance.role r ON r.id=ur.role_id WHERE r.name IN ('admin','出单员')) source_count,
       (SELECT COUNT(*) FROM tenant_user) target_count;
SELECT 'merchant_staff' item,
       (SELECT COUNT(DISTINCT u.id) FROM insurance.`user` u
        JOIN insurance.user_role ur ON ur.user_id=u.id AND ur.is_delete=0
        JOIN insurance.role r ON r.id=ur.role_id
        JOIN insurance.merchant m ON m.id=u.merchant_id
        WHERE r.name IN ('联系人','店员','收款人') AND m.type NOT IN ('机构','保司')) source_count,
       (SELECT COUNT(*) FROM biz_merchant_staff WHERE remark='由旧 user 表拆分') target_count;
SELECT 'workorder' item,(SELECT COUNT(*) FROM insurance.workorder) source_count,(SELECT COUNT(*) FROM biz_workorder) target_count;
SELECT 'workorder_quote' item,(SELECT COUNT(*) FROM insurance.workorder) source_count,(SELECT COUNT(*) FROM biz_workorder_quote) target_count;
SELECT 'workorder_upstream_commission' item,(SELECT COUNT(*) FROM insurance.workorder) source_count,(SELECT COUNT(*) FROM biz_workorder_commission WHERE side='UPSTREAM') target_count;
SELECT 'workorder_downstream_commission' item,(SELECT COUNT(*) FROM insurance.workorder) source_count,(SELECT COUNT(*) FROM biz_workorder_commission WHERE side='DOWNSTREAM') target_count;
SELECT 'workorder_payment' item,(SELECT COUNT(*) FROM insurance.workorder) source_count,(SELECT COUNT(*) FROM biz_workorder_payment) target_count;
SELECT 'workorder_underwriting' item,(SELECT COUNT(*) FROM insurance.workorder) source_count,(SELECT COUNT(*) FROM biz_workorder_underwriting) target_count;
SELECT 'workorder_logistics' item,(SELECT COUNT(*) FROM insurance.workorder) source_count,(SELECT COUNT(*) FROM biz_workorder_logistics) target_count;
SELECT w.id,w.code,w.created_at,FROM_UNIXTIME(src.create_time) expected_created_at
FROM biz_workorder w JOIN insurance.workorder src ON src.id=w.id
WHERE NOT (w.created_at <=> FROM_UNIXTIME(src.create_time))
   OR NOT (w.updated_at <=> FROM_UNIXTIME(src.update_time))
   OR NOT (w.commercial_insurance_start_time <=> src.commercial_insurance_start_time)
   OR NOT (w.compulsory_insurance_start_time <=> src.compulsory_insurance_start_time);
SELECT merchant_id,COUNT(*) active_contacts FROM biz_merchant_staff_role WHERE role_code='CONTACT' AND deleted=0 GROUP BY merchant_id HAVING COUNT(*)>1;
SELECT merchant_id,COUNT(*) default_payees FROM biz_merchant_staff_role WHERE role_code='PAYEE' AND is_default=1 AND deleted=0 GROUP BY merchant_id HAVING COUNT(*)>1;
SELECT workorder_id,COUNT(*) duplicate_quotes FROM biz_workorder_quote GROUP BY workorder_id HAVING COUNT(*)>1;
SELECT workorder_id,COUNT(*) duplicate_payments FROM biz_workorder_payment GROUP BY workorder_id HAVING COUNT(*)>1;
SELECT workorder_id,side,COUNT(*) duplicate_commissions FROM biz_workorder_commission GROUP BY workorder_id,side HAVING COUNT(*)>1;
SELECT w.id,w.created_by FROM biz_workorder w LEFT JOIN tenant_user u ON u.id=w.created_by WHERE w.created_by IS NOT NULL AND u.id IS NULL;
SELECT w.id,w.handle_by FROM biz_workorder w LEFT JOIN tenant_user u ON u.id=w.handle_by WHERE w.handle_by IS NOT NULL AND u.id IS NULL;
SELECT w.id,w.source_staff_id FROM biz_workorder w LEFT JOIN biz_merchant_staff s ON s.id=w.source_staff_id WHERE w.source_staff_id IS NOT NULL AND s.id IS NULL;
SELECT t.id,t.option_json,s.option_json source_option
FROM biz_workorder_insurance t JOIN insurance.workorder_insurance s ON s.id=t.id
WHERE NOT (t.option_json <=> s.option_json)
   OR NOT (t.deductible_option_json <=> s.deductible_option_json);
