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

  @Update(
      "UPDATE saas_recharge_order SET status=2,paid_at=NOW(),updated_at=NOW() WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND status=1 AND deleted=0")
  int completeRechargeOrder(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId);

  @Update(
      "UPDATE saas_recharge_order SET status=3,updated_at=NOW() WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND status=1 AND deleted=0")
  int cancelRechargeOrder(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId);

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

  @Insert(
      "INSERT INTO saas_order(order_no,order_type,enterprise_id,buyer_user_id,plan_id,plan_snapshot_json,buy_user_limit,buy_duration_days,pay_type,amount,price_amount,discount_amount,credit_amount,payable_amount,refund_amount,paid_amount,original_subscription_id,old_plan_id,new_plan_id,auto_renew,status,paid_at,created_at,updated_at,deleted) "
          + "VALUES(#{orderNo},#{orderType},#{enterpriseId},#{userId},#{planId},#{planSnapshotJson},#{userLimit},#{durationDays},'BALANCE',#{payableAmount},#{priceAmount},0,#{creditAmount},#{payableAmount},#{refundAmount},#{payableAmount},#{subscriptionId},#{oldPlanId},#{planId},#{autoRenew},2,NOW(),NOW(),NOW(),0)")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertOrder(Map<String, Object> order);

  @Insert(
      "INSERT INTO saas_order(order_no,order_type,enterprise_id,buyer_user_id,plan_id,plan_snapshot_json,buy_user_limit,buy_duration_days,pay_type,amount,price_amount,discount_amount,credit_amount,payable_amount,refund_amount,paid_amount,original_subscription_id,old_plan_id,new_plan_id,auto_renew,status,failure_reason,created_at,updated_at,deleted) "
          + "VALUES(#{orderNo},'AUTO_RENEW',#{enterpriseId},#{userId},#{planId},#{planSnapshotJson},#{userLimit},#{durationDays},'BALANCE',#{payableAmount},#{priceAmount},0,0,#{payableAmount},0,0,#{subscriptionId},#{oldPlanId},#{planId},1,6,#{failureReason},NOW(),NOW(),0)")
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
      "UPDATE saas_subscription SET plan_id=#{planId},order_id=#{orderId},status=1,user_limit=#{userLimit},ocr_quota=#{ocrQuota},request_quota=#{requestQuota},start_at=#{startAt},end_at=#{endAt},auto_renew_enabled=#{autoRenew},auto_renew_plan_id=CASE WHEN #{autoRenew}=1 THEN #{planId} ELSE NULL END,next_renew_at=#{nextRenewAt},last_renew_order_id=#{orderId},cancel_auto_renew_at=CASE WHEN #{autoRenew}=0 THEN NOW() ELSE NULL END,updated_at=NOW() WHERE enterprise_id=#{enterpriseId}")
  int updateSubscription(Map<String, Object> subscription);

  @Update(
      "UPDATE saas_subscription SET auto_renew_enabled=#{enabled},auto_renew_plan_id=CASE WHEN #{enabled}=1 THEN plan_id ELSE NULL END,next_renew_at=CASE WHEN #{enabled}=1 THEN end_at ELSE NULL END,cancel_auto_renew_at=CASE WHEN #{enabled}=0 THEN NOW() ELSE NULL END,updated_at=NOW() WHERE id=#{id}")
  int updateAutoRenew(@Param("id") Long id, @Param("enabled") int enabled);

  @Select(
      "<script>SELECT o.*,p.name plan_name FROM saas_order o LEFT JOIN saas_plan p ON p.id=o.plan_id WHERE o.enterprise_id=#{enterpriseId} AND o.deleted=0 "
          + "<if test=\"orderNo != null and orderNo != ''\">AND o.order_no LIKE CONCAT('%',#{orderNo},'%')</if><if test=\"orderType != null and orderType != ''\">AND o.order_type=#{orderType}</if><if test=\"startTime != null\">AND o.created_at &gt;= #{startTime}</if><if test=\"endTime != null\">AND o.created_at &lt;= #{endTime}</if> ORDER BY o.created_at DESC LIMIT #{offset},#{pageSize}</script>")
  List<Map<String, Object>> findOrders(Map<String, Object> query);

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
