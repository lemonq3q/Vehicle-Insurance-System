-- SaaS 业务编号永久唯一；软删除后也不得复用。
ALTER TABLE `tenant_enterprise`
  MODIFY COLUMN `code` varchar(50) NOT NULL COMMENT '企业编码',
  ADD CONSTRAINT `uk_tenant_enterprise_code` UNIQUE (`code`);

ALTER TABLE `tenant_invite_code`
  MODIFY COLUMN `code` varchar(64) NOT NULL COMMENT '邀请码',
  ADD CONSTRAINT `uk_tenant_invite_code` UNIQUE (`code`);

ALTER TABLE `saas_recharge_order`
  MODIFY COLUMN `recharge_no` varchar(64) NOT NULL COMMENT 'recharge order no',
  ADD CONSTRAINT `uk_saas_recharge_order_enterprise_no`
    UNIQUE (`enterprise_id`, `recharge_no`);

ALTER TABLE `saas_order`
  MODIFY COLUMN `order_no` varchar(64) NOT NULL COMMENT '订单号',
  ADD CONSTRAINT `uk_saas_order_enterprise_no`
    UNIQUE (`enterprise_id`, `order_no`);

ALTER TABLE `saas_wallet_transaction`
  MODIFY COLUMN `transaction_no` varchar(64) NOT NULL COMMENT 'transaction no',
  ADD CONSTRAINT `uk_saas_wallet_tx_enterprise_no`
    UNIQUE (`enterprise_id`, `transaction_no`);
