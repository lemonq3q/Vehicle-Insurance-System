-- Review-first migration. Target tables must be empty before execution.
-- Source: insurance; target: insurance_saas. MySQL 8.0 required.
USE insurance_saas;
START TRANSACTION;

INSERT INTO tenant_enterprise(id,name,code,status,source,created_at,updated_at,deleted)
VALUES(1,'小马e保存量业务','LEGACY_INSURANCE',1,2,NOW(),NOW(),0);

INSERT INTO auth_role(id,code,name,role_scope,builtin,status,remark,created_at,updated_at,deleted)
SELECT r.id, CASE r.name WHEN 'admin' THEN 'ADMIN' ELSE 'ISSUER' END, r.name, 'TENANT', 1,
       r.status, '由 insurance.role 迁移', FROM_UNIXTIME(r.create_time), FROM_UNIXTIME(r.update_time), r.is_delete
FROM insurance.role r WHERE r.name IN ('admin','出单员');

INSERT INTO auth_permission(id,system_code,type,code,name,sort_no,status,created_at,updated_at,deleted)
SELECT m.id,'INSURANCE','BUTTON',m.perms,COALESCE(m.remark,m.perms),m.id,m.status,NOW(),NOW(),0
FROM insurance.menu m WHERE m.perms IS NOT NULL AND m.perms<>'';

INSERT INTO auth_role_permission(id,role_id,permission_id,created_at,updated_by,deleted)
SELECT rm.id,rm.role_id,rm.menu_id,FROM_UNIXTIME(rm.create_time),rm.update_by,rm.is_delete
FROM insurance.role_menu rm JOIN insurance.role r ON r.id=rm.role_id
JOIN insurance.menu m ON m.id=rm.menu_id AND m.perms IS NOT NULL AND m.perms<>''
WHERE r.name IN ('admin','出单员');

INSERT INTO auth_role_permission(role_id,permission_id,created_at,deleted)
SELECT r.id,p.id,NOW(),0 FROM auth_role r CROSS JOIN auth_permission p
WHERE r.code='ADMIN' AND r.deleted=0
  AND NOT EXISTS(SELECT 1 FROM auth_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id AND rp.deleted=0);

INSERT INTO tenant_user(id,username,phone,email,password,real_name,id_num,status,created_at,updated_at,updated_by,deleted)
SELECT DISTINCT u.id,u.username,u.username,u.email,u.password,u.name,u.id_num,u.status,
       FROM_UNIXTIME(u.create_time),FROM_UNIXTIME(u.update_time),u.update_by,u.is_delete
FROM insurance.`user` u JOIN insurance.user_role ur ON ur.user_id=u.id AND ur.is_delete=0
JOIN insurance.role r ON r.id=ur.role_id AND r.name IN ('admin','出单员');

INSERT INTO tenant_member(id,enterprise_id,user_id,role_code,status,joined_at,created_at,updated_at,updated_by,deleted)
SELECT ur.id,1,u.id,CASE r.name WHEN 'admin' THEN 'ADMIN' ELSE 'ISSUER' END,
       CASE WHEN u.is_approval=1 THEN 1 ELSE 2 END,
       FROM_UNIXTIME(u.create_time),FROM_UNIXTIME(u.create_time),FROM_UNIXTIME(u.update_time),u.update_by,u.is_delete
FROM insurance.`user` u JOIN insurance.user_role ur ON ur.user_id=u.id AND ur.is_delete=0
JOIN insurance.role r ON r.id=ur.role_id AND r.name IN ('admin','出单员');

UPDATE tenant_enterprise SET owner_user_id=(SELECT MIN(user_id) FROM tenant_member WHERE role_code='ADMIN' AND deleted=0) WHERE id=1;

INSERT INTO biz_merchant(id,enterprise_id,code,name,category_id,location,address,contact,phone,bank,bank_card_num,channel,service_phone,
                         default_area_code,created_at,updated_at,updated_by,deleted)
SELECT m.id,1,CASE WHEN m.code_count>1 THEN CONCAT(LEFT(m.code,88),'_',m.id) ELSE m.code END,m.name,c.id,
       m.location,m.address,
       CASE WHEN c.direction='UPSTREAM' THEN m.contact END,
       CASE WHEN c.direction='UPSTREAM' THEN m.phone END,
       m.bank,m.bank_card_num,m.channel,m.phone,m.default_area_code,
       FROM_UNIXTIME(m.create_time),FROM_UNIXTIME(m.update_time),m.update_by,m.is_delete
FROM (SELECT source.*,COUNT(*) OVER(PARTITION BY code) code_count FROM insurance.merchant source) m
JOIN biz_merchant_category c ON c.code=CASE
  WHEN m.type IN ('机构','保司') THEN 'INSURANCE_ORG'
  WHEN m.type='汽修厂' THEN 'AUTO_REPAIR'
  WHEN m.type='代理人' THEN 'AGENT'
  ELSE 'DEALER_STORE' END AND c.deleted=0;

INSERT INTO biz_merchant_area(id,enterprise_id,merchant_id,area_code,created_at,updated_at,updated_by,deleted)
SELECT id,1,merchant_id,area_code,FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),update_by,is_delete
FROM insurance.merchant_area;

INSERT INTO biz_merchant_staff(id,enterprise_id,merchant_id,name,phone,email,id_num,status,remark,created_at,updated_at,updated_by,deleted)
SELECT DISTINCT u.id,1,u.merchant_id,u.name,u.username,u.email,u.id_num,u.status,'由旧 user 表拆分',
       FROM_UNIXTIME(u.create_time),FROM_UNIXTIME(u.update_time),u.update_by,u.is_delete
FROM insurance.`user` u JOIN insurance.user_role ur ON ur.user_id=u.id AND ur.is_delete=0
JOIN insurance.role r ON r.id=ur.role_id AND r.name IN ('联系人','店员','收款人')
WHERE u.merchant_id IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM biz_merchant bm
    JOIN biz_merchant_category bc ON bc.id=bm.category_id AND bc.deleted=0
    WHERE bm.id=u.merchant_id AND bc.direction='DOWNSTREAM'
  );

INSERT INTO biz_merchant_staff_role(id,enterprise_id,merchant_id,staff_id,role_code,is_default,created_at,updated_at,updated_by,deleted)
SELECT id,1,merchant_id,user_id,
       CASE WHEN role_name='联系人' AND role_rank=1 THEN 'CONTACT'
            WHEN role_name='收款人' THEN 'PAYEE' ELSE 'CLERK' END,
       CASE WHEN role_name='收款人' AND role_rank=1 THEN 1 ELSE 0 END,
       FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),update_by,is_delete
FROM (
  SELECT ur.id,u.merchant_id,u.id user_id,r.name role_name,ur.create_time,ur.update_time,ur.update_by,ur.is_delete,
         ROW_NUMBER() OVER(PARTITION BY u.merchant_id,r.name ORDER BY u.id) role_rank
  FROM insurance.`user` u JOIN insurance.user_role ur ON ur.user_id=u.id AND ur.is_delete=0
  JOIN insurance.role r ON r.id=ur.role_id AND r.name IN ('联系人','店员','收款人')
  WHERE u.merchant_id IS NOT NULL
    AND EXISTS (
      SELECT 1 FROM biz_merchant bm
      JOIN biz_merchant_category bc ON bc.id=bm.category_id AND bc.deleted=0
      WHERE bm.id=u.merchant_id AND bc.direction='DOWNSTREAM'
    )
) ranked;

INSERT INTO biz_insurance_product(id,enterprise_id,name,type,options_json,default_option_json,deductible_options_json,
                                  default_deductible_option_json,remark,created_at,updated_at,updated_by,deleted)
SELECT id,1,name,type,options_json,default_option_json,deductible_options_json,
       default_deductible_option_json,remark,
       FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),update_by,is_delete
FROM insurance.insurance;

INSERT INTO sys_file(id,enterprise_id,path,file_name,is_linked,created_at,updated_at,updated_by,deleted)
SELECT id,1,path,file_name,is_linked,FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),update_by,is_delete
FROM insurance.system_file;

INSERT INTO biz_workorder(id,enterprise_id,code,type,owner_type,owner_name,owner_phone,owner_id_num,organization_name,
                          social_credit_code,create_merchant_id,source_staff_id,handle_merchant_id,insurance_merchant_id,area_code,
                          commercial_insurance_start_time,compulsory_insurance_start_time,status,
                          remind_status,renewal_status_cycle,renewal_reminder_disabled,follow_up_res,remark,created_by,handle_by,updated_by,created_at,updated_at,deleted)
SELECT id,1,code,type,owner_type,owner_name,owner_phone,owner_id_num,organization_name,social_credit_code,
       create_merchant_id,
       CASE WHEN EXISTS(SELECT 1 FROM biz_merchant_staff s WHERE s.id=insurance.workorder.create_by) THEN create_by END,
       handle_merchant_id,insurance_merchant_id,area_code,commercial_insurance_start_time,compulsory_insurance_start_time,
       status,remind_status,0,0,follow_up_res,remark,
       CASE WHEN EXISTS(SELECT 1 FROM tenant_user u WHERE u.id=insurance.workorder.create_by) THEN create_by END,
       CASE WHEN EXISTS(SELECT 1 FROM tenant_user u WHERE u.id=insurance.workorder.handle_by) THEN handle_by END,
       CASE WHEN EXISTS(SELECT 1 FROM tenant_user u WHERE u.id=insurance.workorder.update_by) THEN update_by END,
       FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),is_delete
FROM insurance.workorder;

INSERT INTO biz_workorder_quote(enterprise_id,workorder_id,quotation_no,commercial_amount,compulsory_amount,vehicle_and_tax_amount,
                                non_motor_amount,non_motor_insurance_name,non_motor_coverage_amount,quotation_remark,
                                quotation_failed_remark,quotation_time,created_at,updated_at,deleted)
SELECT 1,id,quotation_no,commercial_amount,compulsory_amount,vehicle_and_tax_amount,non_motor_amount,non_motor_insurance_name,
       non_motor_coverage_amount,quotation_remark,quotation_failed_remark,FROM_UNIXTIME(quotation_time),
       FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),is_delete FROM insurance.workorder;

INSERT INTO biz_workorder_commission(enterprise_id,workorder_id,side,compute_type,commercial_percentage,compulsory_percentage,
 vehicle_tax_percentage,non_motor_percentage,commercial_amount,compulsory_amount,vehicle_tax_amount,non_motor_amount,created_at,updated_at,deleted)
SELECT 1,id,'UPSTREAM',upstream_compute_type,upstream_commercial_percentage,upstream_compulsory_percentage,
 upstream_vehicle_and_vessel_tax_percentage,upstream_non_motor_percentage,upstream_commercial_amount,upstream_compulsory_amount,
 upstream_vehicle_and_vessel_tax_amount,upstream_non_motor_amount,FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),is_delete
FROM insurance.workorder
UNION ALL
SELECT 1,id,'DOWNSTREAM',downstream_compute_type,downstream_commercial_percentage,downstream_compulsory_percentage,
 downstream_vehicle_and_vessel_tax_percentage,downstream_non_motor_percentage,downstream_commercial_amount,downstream_compulsory_amount,
 downstream_vehicle_and_vessel_tax_amount,downstream_non_motor_amount,FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),is_delete
FROM insurance.workorder;

INSERT INTO biz_workorder_payment(enterprise_id,workorder_id,required_pay_amount,payee_staff_id,payee_name,payee_phone,
 merchant_bank,merchant_bank_card_num,pay_remark,pay_failed_remark,created_at,updated_at,deleted)
SELECT 1,w.id,w.required_pay_amount,
 (SELECT s.id FROM biz_merchant_staff s
  WHERE s.merchant_id=w.create_merchant_id AND s.deleted=0 AND s.name=w.pay_name
    AND (s.phone=w.pay_id_num OR s.id_num=w.pay_id_num)
  ORDER BY s.id LIMIT 1),
 w.pay_name,w.pay_id_num,w.pay_bank,w.pay_bank_card_num,w.pay_remark,w.pay_failed_remark,
 FROM_UNIXTIME(w.create_time),FROM_UNIXTIME(w.update_time),w.is_delete FROM insurance.workorder w;

INSERT INTO biz_workorder_underwriting(enterprise_id,workorder_id,underwriting_remark,underwriting_failed_remark,underwriting_time,
 commercial_policy_no,compulsory_policy_no,accept_insurance_remark,accept_insurance_failed_remark,finish_time,created_at,updated_at,deleted)
SELECT 1,id,underwriting_remark,underwriting_failed_remark,FROM_UNIXTIME(underwriting_time),commercial_policy_no,
 compulsory_policy_no,accept_insurance_remark,accept_insurance_failed_remark,FROM_UNIXTIME(finish_time),
 FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),is_delete FROM insurance.workorder;

INSERT INTO biz_workorder_logistics(enterprise_id,workorder_id,tracking_num,logistics_company,created_at,updated_at,deleted)
SELECT 1,id,tracking_num,logistics_company,FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),is_delete FROM insurance.workorder;

INSERT INTO biz_workorder_insurance(id,enterprise_id,workorder_id,insurance_id,option_json,deductible_option_json,created_at,updated_at,updated_by,deleted)
SELECT id,1,workorder_id,insurance_id,option_json,deductible_option_json,
       FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),update_by,is_delete
FROM insurance.workorder_insurance;

INSERT INTO biz_vehicle_license(id,enterprise_id,workorder_id,license_plate,vehicle_type,owner_name,usage_nature,brand_model,
 vehicle_code,engine_code,registration_date,issue_date,seats,approved_load_capacity,curb_weight,is_transfer,transfer_date,
 created_at,updated_at,updated_by,deleted)
SELECT id,1,workorder_id,license_plate,vehicle_type,owner_name,usage_nature,brand_model,vehicle_code,engine_code,
 FROM_UNIXTIME(registration_date),FROM_UNIXTIME(issue_date),seats,approved_load_capacity,curb_weight,is_transfer,
 FROM_UNIXTIME(transfer_date),FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),update_by,is_delete
FROM insurance.vehicle_license;

INSERT INTO biz_vehicle_invoice(id,enterprise_id,workorder_id,invoice_amount,buyer_name,buyer_id_num,vehicle_type,brand_model,
 vehicle_code,engine_code,seats,approved_load_capacity,created_at,updated_at,updated_by,deleted)
SELECT id,1,workorder_id,invoice_amount,buyer_name,buyer_id_num,vehicle_type,brand_model,vehicle_code,engine_code,seats,
 approved_load_capacity,FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),update_by,is_delete
FROM insurance.vehicle_invoice;

INSERT INTO biz_vehicle_certificate(id,enterprise_id,workorder_id,brand_model,vehicle_type,vehicle_code,engine_code,seats,
 curb_weight,displacement,approved_load_capacity,created_at,updated_at,updated_by,deleted)
SELECT id,1,workorder_id,brand_model,vehicle_type,vehicle_code,engine_code,seats,curb_weight,displacement,approved_load_capacity,
 FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),update_by,is_delete FROM insurance.vehicle_certificate;

INSERT INTO biz_workorder_file(id,enterprise_id,workorder_id,file_id,file_type,created_at,updated_at,updated_by,deleted)
SELECT id,1,workorder_id,file_id,type,FROM_UNIXTIME(create_time),FROM_UNIXTIME(update_time),update_by,is_delete
FROM insurance.workorder_file;

COMMIT;
