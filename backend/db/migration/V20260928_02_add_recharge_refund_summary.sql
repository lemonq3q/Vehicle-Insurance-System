-- 审阅确认后执行：先备份数据库，暂停退款回调处理，完成迁移后部署并恢复服务。
-- 不新增表，不修改钱包或财务流水；refund_amount 仅为当前实际回撤的累计退款金额。
ALTER TABLE saas_recharge_order
  ADD COLUMN refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计已成功退款并回撤的金额' AFTER amount,
  MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '1待支付 2已支付 3已取消 4支付失败 5已过期 6已关闭 7支付处理中 8已部分退款 9已完全退款';

-- 历史退款回填：复用已有 saas.refund.state 中的 appliedAmount，不重新扣款。
-- 执行下一条 UPDATE 前先检查下面结果，发现无效数据必须停止迁移并核查。
SELECT event_id, checkout_session_id, payload_json
FROM saas_stripe_webhook_event
WHERE event_type = 'saas.refund.state'
  AND (JSON_VALID(payload_json) = 0 OR
    CASE WHEN JSON_VALID(payload_json) THEN
      COALESCE(JSON_TYPE(JSON_EXTRACT(payload_json, '$.appliedAmount')), '') NOT IN ('INTEGER','DOUBLE','DECIMAL')
    ELSE TRUE END);

-- 完成上述 JSON 检查后，再检查累计金额边界；有结果时停止并人工核查。
SELECT r.id, r.amount, f.refunded
FROM saas_recharge_order r
JOIN (
  SELECT checkout_session_id,
    SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.appliedAmount')) AS DECIMAL(12,2))) refunded
  FROM saas_stripe_webhook_event WHERE event_type = 'saas.refund.state'
  GROUP BY checkout_session_id
) f ON f.checkout_session_id = r.stripe_checkout_session_id
WHERE r.deleted = 0 AND (f.refunded < 0 OR f.refunded > r.amount OR f.refunded IS NULL);

-- 合法历史记录按 Session 汇总；超过充值额或负值不回填，需人工核查。
UPDATE saas_recharge_order r
JOIN (
  SELECT checkout_session_id,
    SUM(CASE WHEN JSON_VALID(payload_json) THEN
      CAST(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.appliedAmount')) AS DECIMAL(12,2)) ELSE NULL END) refunded
  FROM saas_stripe_webhook_event WHERE event_type = 'saas.refund.state'
  GROUP BY checkout_session_id
) f ON f.checkout_session_id = r.stripe_checkout_session_id
SET r.refund_amount = f.refunded,
    r.status = CASE WHEN f.refunded = 0 THEN 2 WHEN f.refunded = r.amount THEN 9 ELSE 8 END,
    r.updated_at = NOW()
WHERE r.deleted = 0 AND r.status IN (2,8,9) AND f.refunded BETWEEN 0 AND r.amount;
