package com.example.insurancesystem.saas.service;

import com.example.insurancesystem.domain.encapsulate.TableData;
import java.util.List;
import java.util.Map;

public interface FinanceService {
  Map<String, Object> overview();

  List<Map<String, Object>> plans();

  Map<String, Object> createRecharge(Map<String, Object> body);

  Map<String, Object> rechargeDetail(Long id);

  /**
   * 根据已经通过 Stripe 验签的 Checkout Session 完成充值入账。
   * 调用方只能是支付回调服务；方法以 Session ID 锁定本地订单并保证已支付订单不会重复增加余额。
   *
   * @param checkoutSessionId Stripe Checkout Session ID
   * @param paymentIntentId Stripe PaymentIntent ID，可能在少数异步场景暂时为空
   * @return 完成入账后的充值订单和余额结果
   */
  Map<String, Object> completeStripeRecharge(
      String checkoutSessionId, String paymentIntentId);

  /**
   * 根据验签后重新查询的 Stripe 退款快照回撤原充值所属企业的钱包，余额允许为负数。
   * 仅 succeeded 回撤；退款失败、取消或重新等待客户操作时补回已回撤金额。
   * 订单锁和 Refund 记账记录保证同一退款的不同事件不会重复扣款或重复补回。
   *
   * @param refundId Stripe Refund ID，业务幂等键
   * @param paymentIntentId 原充值的 PaymentIntent ID
   * @param currency Stripe 原退款币种，需与充值配置一致
   * @param minorAmount Stripe 单笔退款金额，当前充值币种按两位小数转换
   * @param status 查询 Stripe 获得的最新退款状态
   */
  void reconcileStripeRefund(String refundId, String paymentIntentId, String currency,
      long minorAmount, String status);

  TableData<Map<String, Object>> recharges(
      int pageNum,
      int pageSize,
      String rechargeNo,
      Integer status,
      String startTime,
      String endTime);

  Map<String, Object> preview(Map<String, Object> body);

  Map<String, Object> subscribe(Map<String, Object> body);

  Map<String, Object> updateAutoRenew(Map<String, Object> body);

  TableData<Map<String, Object>> orders(
      int pageNum,
      int pageSize,
      String orderNo,
      String orderType,
      String startTime,
      String endTime);

  /**
   * 查询当前企业的一笔历史订阅订单，返回下单时的套餐权益快照及完整金额、支付和状态信息。
   *
   * @param id 订阅订单主键
   * @return 可供门户只读详情页展示的订单数据
   */
  Map<String, Object> orderDetail(Long id);

  TableData<Map<String, Object>> transactions(
      int pageNum,
      int pageSize,
      String transactionNo,
      String direction,
      String type,
      String startTime,
      String endTime);
}
