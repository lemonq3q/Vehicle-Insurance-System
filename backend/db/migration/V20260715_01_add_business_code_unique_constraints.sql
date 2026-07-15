-- 系统生成编号在同一企业内永久唯一；软删除后也不得复用。
ALTER TABLE `biz_merchant`
  DROP INDEX `uk_biz_merchant_code`,
  MODIFY COLUMN `code` varchar(100) NOT NULL COMMENT '商户编码',
  ADD CONSTRAINT `uk_biz_merchant_enterprise_code` UNIQUE (`enterprise_id`, `code`);

ALTER TABLE `biz_workorder`
  MODIFY COLUMN `code` varchar(100) NOT NULL COMMENT '工单编号',
  ADD CONSTRAINT `uk_biz_workorder_enterprise_code` UNIQUE (`enterprise_id`, `code`);
