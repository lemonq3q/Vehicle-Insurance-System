-- SaaS 门户官网游客“联系我们”表单主记录。
-- 当前只承载游客提交和监控系统查询，不包含二维码、线索分配或客服跟进状态。
CREATE TABLE IF NOT EXISTS `saas_visitor_lead` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `lead_no` VARCHAR(32) NOT NULL COMMENT '对外游客编号，格式为VL+日期+随机字符',
  `name` VARCHAR(100) NOT NULL COMMENT '游客姓名',
  `contact` VARCHAR(100) NOT NULL COMMENT '手机号、微信号或邮箱等联系方式',
  `role_code` VARCHAR(32) DEFAULT NULL COMMENT 'OPC_AGENT CAR_DEALER INSURANCE_AGENCY OTHER',
  `expected_monthly_orders` INT UNSIGNED DEFAULT NULL COMMENT '预计月单量，非负整数',
  `intent_codes` JSON DEFAULT NULL COMMENT '合作诉求编码数组：TRIAL DEMO CUSTOM_COOPERATION',
  `remark` VARCHAR(1000) DEFAULT NULL COMMENT '游客备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0正常 1软删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_saas_visitor_lead_no` (`lead_no`),
  KEY `idx_saas_visitor_lead_created` (`deleted`,`created_at`,`id`),
  CONSTRAINT `chk_saas_visitor_lead_role`
    CHECK (`role_code` IN ('OPC_AGENT','CAR_DEALER','INSURANCE_AGENCY','OTHER')),
  CONSTRAINT `chk_saas_visitor_lead_monthly_orders`
    CHECK (`expected_monthly_orders` >= 0),
  CONSTRAINT `chk_saas_visitor_lead_intent_codes`
    CHECK (
      JSON_TYPE(`intent_codes`) = 'ARRAY'
      AND JSON_LENGTH(`intent_codes`) BETWEEN 1 AND 3
      AND JSON_CONTAINS(
        JSON_ARRAY('TRIAL','DEMO','CUSTOM_COOPERATION'),
        `intent_codes`
      ) = 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='SaaS门户官网游客线索';
