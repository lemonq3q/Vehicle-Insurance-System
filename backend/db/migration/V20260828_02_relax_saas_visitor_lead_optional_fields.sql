-- 官网联系表单只有姓名和联系方式必填；角色、月单量和合作诉求允许游客按意愿补充。
-- 现有 CHECK 约束对 NULL 结果不判定为失败，仍会约束所有非空值的枚举和数据范围。
ALTER TABLE `saas_visitor_lead`
  MODIFY COLUMN `role_code` VARCHAR(32) DEFAULT NULL COMMENT 'OPC_AGENT CAR_DEALER INSURANCE_AGENCY OTHER',
  MODIFY COLUMN `expected_monthly_orders` INT UNSIGNED DEFAULT NULL COMMENT '预计月单量，非负整数',
  MODIFY COLUMN `intent_codes` JSON DEFAULT NULL COMMENT '合作诉求编码数组：TRIAL DEMO CUSTOM_COOPERATION';
