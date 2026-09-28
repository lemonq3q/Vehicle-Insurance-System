-- Stripe 充值订单生命周期补偿。
-- 状态约定：1待支付、2已支付、3已取消、4支付失败、5已过期、6已关闭、7支付处理中。
SET NAMES utf8mb4;

ALTER TABLE saas_recharge_order
  ADD COLUMN stripe_session_expires_at datetime DEFAULT NULL COMMENT 'Stripe Checkout Session 到期时间' AFTER stripe_checkout_session_id,
  MODIFY COLUMN status tinyint NOT NULL DEFAULT 1 COMMENT '1待支付 2已支付 3已取消 4支付失败 5已过期 6已关闭 7支付处理中',
  ADD KEY idx_saas_recharge_pending_created (status, stripe_checkout_session_id, created_at),
  ADD KEY idx_saas_recharge_pending_session_expiry (status, stripe_session_expires_at, id);
