package com.example.insurancesystem.saas.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/**
 * Stripe 充值适配器的数据访问入口。
 * 这里只负责 Checkout Session 绑定、金额复核和 Webhook 幂等审计；实际钱包入账仍由
 * FinanceService 在事务中处理，从而保持财务记账规则只有一个实现。
 */
@Mapper
public interface StripePaymentMapper {
  @Select("SELECT * FROM saas_recharge_order WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND deleted=0")
  Map<String, Object> findRechargeOrder(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId);

  @Select("SELECT * FROM saas_recharge_order WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND deleted=0 FOR UPDATE")
  Map<String, Object> lockRechargeOrder(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId);

  @Select("SELECT * FROM saas_recharge_order WHERE stripe_checkout_session_id=#{sessionId} AND deleted=0")
  Map<String, Object> findRechargeOrderBySession(String sessionId);

  /**
   * 退款不依赖 metadata，以服务器已绑定的原付款标识识别本系统充值订单。
   */
  @Select("SELECT * FROM saas_recharge_order WHERE stripe_payment_intent_id=#{paymentIntentId} AND deleted=0")
  Map<String, Object> findRechargeOrderByPaymentIntent(String paymentIntentId);

  @Update("UPDATE saas_recharge_order SET stripe_checkout_session_id=#{sessionId},stripe_session_expires_at=FROM_UNIXTIME(#{expiresAt}),updated_at=NOW() WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND status=1 AND deleted=0 AND (stripe_checkout_session_id IS NULL OR stripe_checkout_session_id=#{sessionId})")
  int bindCheckoutSession(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId, @Param("sessionId") String sessionId, @Param("expiresAt") Long expiresAt);

  @Update("UPDATE saas_recharge_order SET status=3,updated_at=NOW() WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND status=1 AND deleted=0")
  int cancelRechargeOrder(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId);

  @Update("UPDATE saas_recharge_order SET status=4,payment_failure_reason=#{reason},updated_at=NOW() WHERE stripe_checkout_session_id=#{sessionId} AND status IN (1,7) AND deleted=0")
  int markPaymentFailed(@Param("sessionId") String sessionId, @Param("reason") String reason);

  /** 将已经完成 Checkout、但资金尚未确认到账的订单推进到支付处理中。 */
  @Update("UPDATE saas_recharge_order SET status=7,payment_failure_reason=NULL,updated_at=NOW() WHERE stripe_checkout_session_id=#{sessionId} AND status=1 AND deleted=0")
  int markPaymentProcessing(String sessionId);

  /** Stripe 明确返回 expired 后，将仍待支付的本地订单更新为已过期。 */
  @Update("UPDATE saas_recharge_order SET status=5,payment_failure_reason=#{reason},updated_at=NOW() WHERE stripe_checkout_session_id=#{sessionId} AND status=1 AND deleted=0")
  int markSessionExpired(@Param("sessionId") String sessionId, @Param("reason") String reason);

  /**
   * 高性能批量关闭尚未创建 Stripe Session 且已超过本地支付窗口的订单。
   * 复合索引按 status、Session 是否为空及 created_at 定位，LIMIT 控制单次锁行规模。
   */
  @Update("UPDATE saas_recharge_order SET status=5,payment_failure_reason='超过30分钟未发起支付',updated_at=NOW() WHERE status=1 AND stripe_checkout_session_id IS NULL AND created_at <= #{cutoff} AND deleted=0 ORDER BY created_at,id LIMIT #{limit}")
  int expireOrdersWithoutSession(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);

  /**
   * 只分页读取已经到达 Stripe expires_at 的待支付候选，避免定时任务扫描全部历史订单。
   * 候选仍需逐笔查询 Stripe，防止 Checkout 已完成但异步资金仍在处理中时被误判过期。
   */
  @Select("SELECT id,stripe_checkout_session_id,stripe_session_expires_at FROM saas_recharge_order FORCE INDEX (idx_saas_recharge_pending_session_expiry) WHERE status=1 AND stripe_checkout_session_id IS NOT NULL AND stripe_session_expires_at <= NOW() AND deleted=0 ORDER BY stripe_session_expires_at,id LIMIT #{limit}")
  List<Map<String, Object>> findExpiredSessionCandidates(@Param("limit") int limit);

  @Insert("INSERT IGNORE INTO saas_stripe_webhook_event(event_id,event_type,checkout_session_id,payload_json,created_at) VALUES(#{eventId},#{eventType},#{sessionId},#{payload},NOW())")
  int insertWebhookEvent(@Param("eventId") String eventId, @Param("eventType") String eventType, @Param("sessionId") String sessionId, @Param("payload") String payload);
}
