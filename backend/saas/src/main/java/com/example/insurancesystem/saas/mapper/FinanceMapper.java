package com.example.insurancesystem.saas.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FinanceMapper {
  @Select("SELECT * FROM saas_wallet WHERE enterprise_id=#{enterpriseId} AND deleted=0 LIMIT 1")
  Map<String, Object> findWallet(Long enterpriseId);

  @Select(
      "SELECT * FROM saas_wallet WHERE enterprise_id=#{enterpriseId} AND deleted=0 LIMIT 1 FOR UPDATE")
  Map<String, Object> lockWallet(Long enterpriseId);

  @Select("SELECT enterprise_id FROM saas_wallet WHERE deleted=0 ORDER BY enterprise_id")
  List<Long> findWalletEnterpriseIds();

  @Select("SELECT * FROM saas_plan WHERE id=#{id} AND status=1 AND deleted=0")
  Map<String, Object> findPlan(Long id);

  @Select("SELECT * FROM saas_plan WHERE status=1 AND deleted=0 ORDER BY sort_no,id")
  List<Map<String, Object>> findPlans();

  @Select("SELECT * FROM saas_subscription WHERE enterprise_id=#{enterpriseId} LIMIT 1")
  Map<String, Object> findSubscription(Long enterpriseId);

  @Select("SELECT * FROM saas_subscription WHERE enterprise_id=#{enterpriseId} FOR UPDATE")
  Map<String, Object> lockSubscription(Long enterpriseId);

  @Insert(
      "INSERT INTO saas_recharge_order(recharge_no,enterprise_id,user_id,amount,pay_channel,pay_trade_no,status,created_at,updated_at,deleted) "
          + "VALUES(#{rechargeNo},#{enterpriseId},#{userId},#{amount},#{payChannel},#{payTradeNo},1,NOW(),NOW(),0)")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertRecharge(Map<String, Object> order);

  @Select(
      "SELECT * FROM saas_recharge_order WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND deleted=0")
  Map<String, Object> findRechargeOrder(
      @Param("id") Long id, @Param("enterpriseId") Long enterpriseId);

  @Select(
      "SELECT * FROM saas_recharge_order WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND deleted=0 FOR UPDATE")
  Map<String, Object> lockRechargeOrder(
      @Param("id") Long id, @Param("enterpriseId") Long enterpriseId);

  /**
   * Stripe 回调按不可猜测的 Checkout Session ID 锁定充值订单。
   * 该查询不依赖登录企业上下文，只允许在 Webhook 验签成功后的事务中调用。
   */
  @Select(
      "SELECT * FROM saas_recharge_order WHERE stripe_checkout_session_id=#{checkoutSessionId} AND deleted=0 FOR UPDATE")
  Map<String, Object> lockRechargeOrderByStripeSession(String checkoutSessionId);

  /**
   * 外部退款按原 PaymentIntent 锁定充值订单，串行化同一付款的多笔部分退款。
   * 仅由验签后的支付服务调用，不依赖用户登录上下文。
   */
  @Select("SELECT * FROM saas_recharge_order WHERE stripe_payment_intent_id=#{paymentIntentId} AND deleted=0 FOR UPDATE")
  Map<String, Object> lockRechargeOrderByPaymentIntent(String paymentIntentId);

  /**
   * 复用 Webhook 审计表保存本地退款幂等状态；该键使用独立命名空间，不与 evt_ 事件冲突。
   * 调用方已持有原充值订单锁，状态记录与钱包变更同一事务提交，不新增表结构。
   */
  @Select("SELECT payload_json FROM saas_stripe_webhook_event WHERE event_id=#{stateKey} AND event_type='saas.refund.state' FOR UPDATE")
  String lockStripeRefundState(String stateKey);

  /**
   * 按已有 Session 索引读取本充值的所有退款状态。锁定读避免事务快照遗漏并发刚提交的退款，
   * 上层按 JSON 中的已回撤额校验累计金额，实际 Stripe 原始事件仍作为独立审计记录保留。
   */
  @Select("SELECT payload_json FROM saas_stripe_webhook_event WHERE checkout_session_id=#{sessionId} AND event_type='saas.refund.state' FOR UPDATE")
  List<String> findStripeRefundStates(String sessionId);

  /**
   * 保存首次退款的本地业务状态，event_id 唯一索引与充值订单锁防止跨事件重复回撤。
   */
  @Insert("INSERT INTO saas_stripe_webhook_event(event_id,event_type,checkout_session_id,payload_json,created_at) VALUES(#{stateKey},'saas.refund.state',#{sessionId},#{payload},NOW())")
  int insertStripeRefundState(@Param("stateKey") String stateKey, @Param("sessionId") String sessionId, @Param("payload") String payload);

  /**
   * 与钱包和流水在同一事务更新已回撤金额；事务失败时不能留下假成功标记。
   */
  @Update("UPDATE saas_stripe_webhook_event SET payload_json=#{payload},processed_at=NOW() WHERE event_id=#{stateKey} AND event_type='saas.refund.state'")
  int updateStripeRefundState(@Param("stateKey") String stateKey, @Param("payload") String payload);

  /**
   * 将仍待支付的 Stripe 充值订单原子更新为已支付并保存最终 PaymentIntent。
   * status 条件与行锁共同保证重复 Webhook 不会重复入账。
   */
  @Update(
      "UPDATE saas_recharge_order SET status=2,pay_trade_no=#{paymentIntentId},stripe_payment_intent_id=#{paymentIntentId},payment_failure_reason=NULL,paid_at=NOW(),updated_at=NOW() WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND status IN (1,7) AND deleted=0")
  int completeStripeRechargeOrder(
      @Param("id") Long id,
      @Param("enterpriseId") Long enterpriseId,
      @Param("paymentIntentId") String paymentIntentId);

  /**
   * 在原充值行锁保护下更新累计成功退款额及对应状态。
   * 与钱包回撤、退款状态记录处于同一事务，只允许修改已经入账的订单。
   * @param id 原充值主键
   * @param enterpriseId 原充值所属企业
   * @param refundAmount 全部退款当前实际回撤的累计金额
   * @param status 汇总计算的已支付、部分退款或完全退款状态
   * @return 更新的订单数量
   */
  @Update("UPDATE saas_recharge_order SET refund_amount=#{refundAmount},status=#{status},updated_at=NOW() WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND status IN (2,8,9) AND deleted=0")
  int updateRechargeRefundSummary(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId,
      @Param("refundAmount") java.math.BigDecimal refundAmount, @Param("status") int status);

  @Select(
      "<script>SELECT * FROM saas_recharge_order WHERE enterprise_id=#{enterpriseId} AND deleted=0 "
          + "<if test=\"rechargeNo != null and rechargeNo != ''\">AND recharge_no LIKE CONCAT('%',#{rechargeNo},'%')</if><if test=\"status != null\">AND status=#{status}</if><if test=\"startTime != null\">AND created_at &gt;= #{startTime}</if><if test=\"endTime != null\">AND created_at &lt;= #{endTime}</if> "
          + "ORDER BY created_at DESC LIMIT #{offset},#{pageSize}</script>")
  List<Map<String, Object>> findRecharges(Map<String, Object> query);

  @Select(
      "<script>SELECT COUNT(1) FROM saas_recharge_order WHERE enterprise_id=#{enterpriseId} AND deleted=0 "
          + "<if test=\"rechargeNo != null and rechargeNo != ''\">AND recharge_no LIKE CONCAT('%',#{rechargeNo},'%')</if><if test=\"status != null\">AND status=#{status}</if><if test=\"startTime != null\">AND created_at &gt;= #{startTime}</if><if test=\"endTime != null\">AND created_at &lt;= #{endTime}</if></script>")
  long countRecharges(Map<String, Object> query);

  @Update(
      "UPDATE saas_wallet SET balance_amount=#{balanceAfter},updated_at=NOW(),updated_by=#{userId} WHERE id=#{walletId} AND balance_amount=#{balanceBefore} AND deleted=0")
  int updateWallet(Map<String, Object> values);

  @Update(
      "UPDATE saas_subscription SET status=3,suspend_reason=#{reason},suspended_at=NOW(),resumed_at=NULL,updated_at=NOW() WHERE id=#{id} AND status=1 AND plan_id IS NOT NULL AND end_at>NOW()")
  int suspendSubscriptionForArrears(@Param("id") Long id, @Param("reason") String reason);

  @Update(
      "UPDATE saas_subscription SET status=1,suspend_reason=NULL,resumed_at=NOW(),updated_at=NOW() WHERE id=#{id} AND status=3 AND suspend_reason=#{reason} AND plan_id IS NOT NULL AND end_at>NOW()")
  int restoreArrearsSubscription(@Param("id") Long id, @Param("reason") String reason);

  @Insert(
      "INSERT INTO saas_order(order_no,order_type,enterprise_id,buyer_user_id,plan_id,plan_snapshot_json,buy_user_limit,buy_workorder_limit,buy_duration_days,workorder_overage_count,workorder_overage_amount,pay_type,amount,price_amount,discount_amount,credit_amount,payable_amount,refund_amount,paid_amount,original_subscription_id,old_plan_id,new_plan_id,auto_renew,status,paid_at,created_at,updated_at,deleted) "
          + "VALUES(#{orderNo},#{orderType},#{enterpriseId},#{userId},#{planId},#{planSnapshotJson},#{userLimit},#{workorderLimit},#{durationDays},#{workorderOverageCount},#{workorderOverageAmount},'BALANCE',#{payableAmount},#{priceAmount},0,#{creditAmount},#{payableAmount},#{refundAmount},#{payableAmount},#{subscriptionId},#{oldPlanId},#{planId},#{autoRenew},2,NOW(),NOW(),NOW(),0)")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertOrder(Map<String, Object> order);

  @Insert(
      "INSERT INTO saas_order(order_no,order_type,enterprise_id,buyer_user_id,plan_id,plan_snapshot_json,buy_user_limit,buy_workorder_limit,buy_duration_days,workorder_overage_count,workorder_overage_amount,pay_type,amount,price_amount,discount_amount,credit_amount,payable_amount,refund_amount,paid_amount,original_subscription_id,old_plan_id,new_plan_id,auto_renew,status,failure_reason,created_at,updated_at,deleted) "
          + "VALUES(#{orderNo},'AUTO_RENEW',#{enterpriseId},#{userId},#{planId},#{planSnapshotJson},#{userLimit},#{workorderLimit},#{durationDays},0,0,'BALANCE',#{payableAmount},#{priceAmount},0,0,#{payableAmount},0,0,#{subscriptionId},#{oldPlanId},#{planId},1,6,#{failureReason},NOW(),NOW(),0)")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertAutoRenewFailureOrder(Map<String, Object> order);

  @Insert(
      "INSERT INTO saas_wallet_transaction(enterprise_id,wallet_id,user_id,transaction_no,direction,transaction_type,amount,balance_before,balance_after,related_order_id,related_recharge_order_id,related_subscription_id,remark,created_at) "
          + "VALUES(#{enterpriseId},#{walletId},#{userId},#{transactionNo},#{direction},#{transactionType},#{amount},#{balanceBefore},#{balanceAfter},#{orderId},#{rechargeOrderId},#{subscriptionId},#{remark},NOW())")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertTransaction(Map<String, Object> transaction);

  @Update("UPDATE saas_order SET wallet_transaction_id=#{transactionId} WHERE id=#{orderId}")
  int bindOrderTransaction(
      @Param("orderId") Long orderId, @Param("transactionId") Long transactionId);

  @Update(
      "UPDATE saas_subscription SET plan_id=#{planId},order_id=#{orderId},resumed_at=CASE WHEN status=3 AND suspend_reason='ARREARS' THEN NOW() ELSE resumed_at END,status=1,suspend_reason=NULL,user_limit=#{userLimit},workorder_limit=#{workorderLimit},ocr_quota=#{ocrQuota},request_quota=#{requestQuota},start_at=#{startAt},end_at=#{endAt},auto_renew_enabled=#{autoRenew},auto_renew_plan_id=CASE WHEN #{autoRenew}=1 THEN #{planId} ELSE NULL END,next_renew_at=#{nextRenewAt},last_renew_order_id=#{orderId},cancel_auto_renew_at=CASE WHEN #{autoRenew}=0 THEN NOW() ELSE NULL END,updated_at=NOW() WHERE enterprise_id=#{enterpriseId}")
  int updateSubscription(Map<String, Object> subscription);

  @Update(
      "UPDATE saas_subscription SET auto_renew_enabled=#{enabled},auto_renew_plan_id=CASE WHEN #{enabled}=1 THEN plan_id ELSE NULL END,next_renew_at=CASE WHEN #{enabled}=1 THEN end_at ELSE NULL END,cancel_auto_renew_at=CASE WHEN #{enabled}=0 THEN NOW() ELSE NULL END,updated_at=NOW() WHERE id=#{id}")
  int updateAutoRenew(@Param("id") Long id, @Param("enabled") int enabled);

  @Select(
      "<script>SELECT o.*,p.name plan_name FROM saas_order o LEFT JOIN saas_plan p ON p.id=o.plan_id WHERE o.enterprise_id=#{enterpriseId} AND o.deleted=0 "
          + "<if test=\"orderNo != null and orderNo != ''\">AND o.order_no LIKE CONCAT('%',#{orderNo},'%')</if><if test=\"orderType != null and orderType != ''\">AND o.order_type=#{orderType}</if><if test=\"startTime != null\">AND o.created_at &gt;= #{startTime}</if><if test=\"endTime != null\">AND o.created_at &lt;= #{endTime}</if> ORDER BY o.created_at DESC LIMIT #{offset},#{pageSize}</script>")
  List<Map<String, Object>> findOrders(Map<String, Object> query);

  /**
   * 查询当前企业的一笔订阅订单及其套餐名称。
   * 企业条件与逻辑删除条件用于隔离租户数据；详情仍保留订单中的套餐快照，避免套餐后续调整影响历史展示。
   *
   * @param id 订阅订单主键，来源于门户订单列表路由
   * @param enterpriseId 当前登录账号选择的企业主键
   * @return 匹配的订阅订单，订单不存在或不属于当前企业时返回 null
   */
  @Select(
      "SELECT o.*,p.name plan_name FROM saas_order o LEFT JOIN saas_plan p ON p.id=o.plan_id "
          + "WHERE o.id=#{id} AND o.enterprise_id=#{enterpriseId} AND o.deleted=0")
  Map<String, Object> findOrder(
      @Param("id") Long id, @Param("enterpriseId") Long enterpriseId);

  @Select(
      "<script>SELECT COUNT(1) FROM saas_order o WHERE o.enterprise_id=#{enterpriseId} AND o.deleted=0 <if test=\"orderNo != null and orderNo != ''\">AND o.order_no LIKE CONCAT('%',#{orderNo},'%')</if><if test=\"orderType != null and orderType != ''\">AND o.order_type=#{orderType}</if><if test=\"startTime != null\">AND o.created_at &gt;= #{startTime}</if><if test=\"endTime != null\">AND o.created_at &lt;= #{endTime}</if></script>")
  long countOrders(Map<String, Object> query);

  @Select(
      "<script>SELECT * FROM saas_wallet_transaction WHERE enterprise_id=#{enterpriseId} <if test=\"transactionNo != null and transactionNo != ''\">AND transaction_no LIKE CONCAT('%',#{transactionNo},'%')</if><if test=\"direction != null and direction != ''\">AND direction=#{direction}</if><if test=\"transactionType != null and transactionType != ''\">AND transaction_type=#{transactionType}</if><if test=\"startTime != null\">AND created_at &gt;= #{startTime}</if><if test=\"endTime != null\">AND created_at &lt;= #{endTime}</if> ORDER BY created_at DESC LIMIT #{offset},#{pageSize}</script>")
  List<Map<String, Object>> findTransactions(Map<String, Object> query);

  @Select(
      "<script>SELECT COUNT(1) FROM saas_wallet_transaction WHERE enterprise_id=#{enterpriseId} <if test=\"transactionNo != null and transactionNo != ''\">AND transaction_no LIKE CONCAT('%',#{transactionNo},'%')</if><if test=\"direction != null and direction != ''\">AND direction=#{direction}</if><if test=\"transactionType != null and transactionType != ''\">AND transaction_type=#{transactionType}</if><if test=\"startTime != null\">AND created_at &gt;= #{startTime}</if><if test=\"endTime != null\">AND created_at &lt;= #{endTime}</if></script>")
  long countTransactions(Map<String, Object> query);
}
