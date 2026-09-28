-- 回退本会话的 V20260927_01_add_stripe_subscription_billing.sql，适用于 MySQL 8。
-- 仅生成供审阅，本会话未执行此回退脚本。
-- 执行前备份 insurance_saas，停止 SaaS 服务、定时任务和正在发布的新版本，避免并发写入。
-- 本脚本保留既有 Stripe 余额充值表、Webhook 审计表、钱包流水、已付订单和已付权益日期。
-- 删除新增字段和两张新表会丢失其中的 Stripe 标识、周期、授权、宽限和订阅历史数据。
-- SQL 无法取消 Stripe 平台的订阅或 Checkout；有相关交易时须先在 Stripe 完成取消、
-- 使未完成的套餐付款/授权 Checkout 过期，并核对异步付款、已付账单及退款。
-- DDL 会隐式提交，整个脚本不能依靠 ROLLBACK 恢复；不支持重复执行或部分迁移状态。

USE insurance_saas;
SET NAMES utf8mb4;

-- 默认不允许移除未核对的外部对象。只有实际完成上面的 Stripe 核对后才能改为 1。
-- 如果从未成功创建套餐 Stripe 支付或续费对象，保持 0 即可执行。
SET @stripe_external_objects_settled = 0;

DROP PROCEDURE IF EXISTS rb_20260928_stripe_subscription_billing;
DELIMITER $$
CREATE PROCEDURE rb_20260928_stripe_subscription_billing()
BEGIN
  DECLARE v_column_count INT DEFAULT 0;
  DECLARE v_index_count INT DEFAULT 0;
  DECLARE v_table_count INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  -- 核对目标库及完整迁移状态；异常时在任何业务写入和结构删除前停止。
  IF DATABASE() <> 'insurance_saas' THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Wrong target database; expected insurance_saas';
  END IF;

  SELECT COUNT(*) INTO v_column_count
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND ((table_name = 'saas_order' AND column_name IN (
      'stripe_checkout_session_id', 'stripe_payment_intent_id', 'stripe_invoice_id',
      'service_period_start_at', 'service_period_end_at', 'workorder_overage_ids_json'))
      OR (table_name = 'saas_subscription' AND column_name IN (
      'renewal_method', 'stripe_customer_id', 'stripe_subscription_id',
      'stripe_subscription_status', 'stripe_renewal_amount', 'stripe_setup_session_id', 'grace_until')));

  SELECT COUNT(*) INTO v_index_count
  FROM (
    SELECT table_name, index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND ((table_name = 'saas_order' AND index_name IN (
        'uk_saas_order_stripe_checkout', 'uk_saas_order_stripe_intent', 'uk_saas_order_stripe_invoice',
        'idx_saas_order_period', 'idx_saas_order_stripe_pending'))
        OR (table_name = 'saas_subscription' AND index_name IN (
        'uk_saas_subscription_stripe_subscription', 'uk_saas_subscription_stripe_setup',
        'idx_saas_subscription_grace', 'idx_saas_subscription_stripe_due')))
    GROUP BY table_name, index_name
  ) AS migrated_indexes;

  SELECT COUNT(*) INTO v_table_count
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name IN ('saas_stripe_subscription_link', 'saas_stripe_billing_exception');

  IF v_column_count <> 13 OR v_index_count <> 9 OR v_table_count <> 2 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Migration incomplete or already rolled back; inspect schema first';
  END IF;

  -- 本地有关联时，要求操作者先完成 Stripe 侧核对；关闭本地开关不能阻止外部收费。
  IF COALESCE(@stripe_external_objects_settled, 0) <> 1 AND (
    EXISTS (SELECT 1 FROM saas_subscription
      WHERE stripe_setup_session_id IS NOT NULL
        OR (stripe_subscription_id IS NOT NULL
          AND COALESCE(stripe_subscription_status, '') NOT IN ('canceled', 'incomplete_expired')))
    OR EXISTS (SELECT 1 FROM saas_stripe_subscription_link
      WHERE stripe_subscription_id IS NOT NULL AND status <> 'CANCELED')
    OR EXISTS (SELECT 1 FROM saas_order
      WHERE pay_channel = 'STRIPE' AND status = 1 AND deleted = 0)
  ) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Settle Stripe subscriptions, setup sessions and pending payments before rollback';
  END IF;

  -- 未核对的已付款异常账单不能随表一起删除；先完成财务处理并导出记录。
  IF EXISTS (SELECT 1 FROM saas_stripe_billing_exception WHERE status = 'PENDING') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Resolve and archive pending Stripe billing exceptions before rollback';
  END IF;

  -- 保留常用交易标识到原有字段，防止删除新增列后无法追溯已付款订单。
  IF EXISTS (SELECT 1 FROM saas_order
    WHERE (pay_trade_no IS NULL OR pay_trade_no = '')
      AND CHAR_LENGTH(COALESCE(stripe_payment_intent_id, stripe_invoice_id)) > 100) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stripe trade identifier exceeds pay_trade_no length; archive it before rollback';
  END IF;

  START TRANSACTION;
  UPDATE saas_order
  SET pay_trade_no = COALESCE(stripe_payment_intent_id, stripe_invoice_id)
  WHERE (pay_trade_no IS NULL OR pay_trade_no = '')
    AND (stripe_payment_intent_id IS NOT NULL OR stripe_invoice_id IS NOT NULL);

  -- 只关闭原 Stripe 续费责任，不把它悄悄改为钱包扣款；end_at 和钱包续费设置保持原值。
  UPDATE saas_subscription
  SET auto_renew_enabled = 0,
      auto_renew_plan_id = NULL,
      next_renew_at = NULL,
      cancel_auto_renew_at = NOW(),
      updated_at = NOW()
  WHERE renewal_method = 'STRIPE';

  -- 外部未完成交易已由操作者处理后，关闭旧版本不再支持的套餐待付订单。
  UPDATE saas_order
  SET status = 5, auto_renew = 0,
      failure_reason = 'Stripe 套餐支付功能回退，订单已关闭', updated_at = NOW()
  WHERE pay_channel = 'STRIPE' AND status = 1 AND deleted = 0;

  DELETE FROM saas_enterprise_reminder WHERE reminder_type = 'STRIPE_RENEWAL_PAYMENT_FAILED';
  DELETE FROM sys_reminder_type WHERE type_code = 'STRIPE_RENEWAL_PAYMENT_FAILED';
  COMMIT;

  -- 精确移除本次订单迁移新增的五个索引和六个字段，保留订单本身和原有结构。
  ALTER TABLE saas_order
    DROP INDEX uk_saas_order_stripe_checkout,
    DROP INDEX uk_saas_order_stripe_intent,
    DROP INDEX uk_saas_order_stripe_invoice,
    DROP INDEX idx_saas_order_period,
    DROP INDEX idx_saas_order_stripe_pending,
    DROP COLUMN stripe_checkout_session_id,
    DROP COLUMN stripe_payment_intent_id,
    DROP COLUMN stripe_invoice_id,
    DROP COLUMN service_period_start_at,
    DROP COLUMN service_period_end_at,
    DROP COLUMN workorder_overage_ids_json;

  -- 精确移除本次订阅迁移新增的四个索引和七个字段，保留已付套餐权益。
  ALTER TABLE saas_subscription
    DROP INDEX uk_saas_subscription_stripe_subscription,
    DROP INDEX uk_saas_subscription_stripe_setup,
    DROP INDEX idx_saas_subscription_grace,
    DROP INDEX idx_saas_subscription_stripe_due,
    DROP COLUMN renewal_method,
    DROP COLUMN stripe_customer_id,
    DROP COLUMN stripe_subscription_id,
    DROP COLUMN stripe_subscription_status,
    DROP COLUMN stripe_renewal_amount,
    DROP COLUMN stripe_setup_session_id,
    DROP COLUMN grace_until;

  DROP TABLE saas_stripe_billing_exception;
  DROP TABLE saas_stripe_subscription_link;
END$$
DELIMITER ;

CALL rb_20260928_stripe_subscription_billing();
DROP PROCEDURE IF EXISTS rb_20260928_stripe_subscription_billing;

-- 执行成功后这三个数都应为 0；余额充值结构不在本脚本回退范围内。
SELECT COUNT(*) AS remaining_subscription_columns
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND ((table_name = 'saas_order' AND column_name IN (
    'stripe_checkout_session_id', 'stripe_payment_intent_id', 'stripe_invoice_id',
    'service_period_start_at', 'service_period_end_at', 'workorder_overage_ids_json'))
    OR (table_name = 'saas_subscription' AND column_name IN (
    'renewal_method', 'stripe_customer_id', 'stripe_subscription_id',
    'stripe_subscription_status', 'stripe_renewal_amount', 'stripe_setup_session_id', 'grace_until')));
SELECT COUNT(*) AS remaining_subscription_tables
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('saas_stripe_subscription_link', 'saas_stripe_billing_exception');
SELECT COUNT(*) AS remaining_reminder_types
FROM sys_reminder_type WHERE type_code = 'STRIPE_RENEWAL_PAYMENT_FAILED';
