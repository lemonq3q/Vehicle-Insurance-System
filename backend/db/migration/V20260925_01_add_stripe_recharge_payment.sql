-- SaaS 门户 Stripe 充值接入。
-- 本脚本只扩展支付平台关联字段和 Webhook 幂等记录，不修改既有钱包余额及历史充值数据。
SET NAMES utf8mb4;

ALTER TABLE saas_recharge_order
  ADD COLUMN stripe_checkout_session_id varchar(255) DEFAULT NULL COMMENT 'Stripe Checkout Session ID' AFTER pay_trade_no,
  ADD COLUMN stripe_payment_intent_id varchar(255) DEFAULT NULL COMMENT 'Stripe PaymentIntent ID' AFTER stripe_checkout_session_id,
  ADD COLUMN payment_failure_reason varchar(500) DEFAULT NULL COMMENT '第三方支付失败原因' AFTER stripe_payment_intent_id,
  ADD UNIQUE KEY uk_saas_recharge_stripe_session (stripe_checkout_session_id),
  ADD UNIQUE KEY uk_saas_recharge_stripe_payment_intent (stripe_payment_intent_id);

CREATE TABLE saas_stripe_webhook_event (
  id bigint NOT NULL AUTO_INCREMENT,
  event_id varchar(255) NOT NULL COMMENT 'Stripe Event ID，用于回调幂等',
  event_type varchar(100) NOT NULL COMMENT 'Stripe事件类型',
  checkout_session_id varchar(255) DEFAULT NULL COMMENT '关联Checkout Session ID',
  payload_json mediumtext NOT NULL COMMENT '验签后的原始事件负载，供审计排障',
  processed_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '业务处理完成时间',
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_saas_stripe_webhook_event_id (event_id),
  KEY idx_saas_stripe_webhook_session (checkout_session_id),
  KEY idx_saas_stripe_webhook_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Stripe Webhook幂等与审计事件';
