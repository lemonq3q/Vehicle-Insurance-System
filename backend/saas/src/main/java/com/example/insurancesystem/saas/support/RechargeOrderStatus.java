package com.example.insurancesystem.saas.support;

/**
 * SaaS 企业余额充值订单的完整生命周期状态。
 * 状态由本地创建、用户取消、Stripe Webhook 与定时补偿共同推进；任何资金入账只能从
 * 待支付或支付处理中进入已支付，终态订单不能重新进入支付流程。
 * 已入账订单的退款状态按累计实际回撤额在已支付、部分退款、完全退款之间变化。
 */
public final class RechargeOrderStatus {
  public static final int PENDING = 1;
  public static final int PAID = 2;
  public static final int CANCELED = 3;
  public static final int FAILED = 4;
  public static final int EXPIRED = 5;
  public static final int CLOSED = 6;
  public static final int PROCESSING = 7;
  public static final int PARTIALLY_REFUNDED = 8;
  public static final int FULLY_REFUNDED = 9;

  /**
   * 判断原充值是否已经入账，包括后续发生退款的订单。
   * 付款补偿和迟到的付款通知据此跳过重复充值，退款不会重新开启付款生命周期。
   * @param status 数据库充值订单状态
   * @return 原充值已完成入账时返回 true
   */
  public static boolean isPaid(int status) {
    return status == PAID || status == PARTIALLY_REFUNDED || status == FULLY_REFUNDED;
  }

  private RechargeOrderStatus() {}
}
