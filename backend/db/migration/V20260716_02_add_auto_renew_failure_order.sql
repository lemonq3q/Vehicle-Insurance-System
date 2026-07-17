ALTER TABLE `saas_order`
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '1待支付 2已支付 3已取消 4已退款 5已关闭 6自动续费失败',
  ADD COLUMN `failure_reason` varchar(500) DEFAULT NULL COMMENT '自动续费失败原因' AFTER `status`;

ALTER TABLE `saas_order_archive`
  MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '1待支付 2已支付 3已取消 4已退款 5已关闭 6自动续费失败',
  ADD COLUMN `failure_reason` varchar(500) DEFAULT NULL COMMENT '自动续费失败原因' AFTER `status`;
