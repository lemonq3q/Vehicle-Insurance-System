-- 企业数据保留特权由监控后台授权，0=按超期规则清理，1=保留车险业务数据。
-- 新建企业不传此列时由数据库固定为 0；归档表同步保留授权历史。
ALTER TABLE `tenant_enterprise`
  ADD COLUMN `data_retention_enabled` TINYINT NOT NULL DEFAULT 0
  COMMENT '数据保留特权：0无，1有，仅监控后台可修改'
  AFTER `source`;

ALTER TABLE `tenant_enterprise_archive`
  ADD COLUMN `data_retention_enabled` TINYINT NOT NULL DEFAULT 0
  COMMENT '数据保留特权：0无，1有，仅监控后台可修改'
  AFTER `source`;
