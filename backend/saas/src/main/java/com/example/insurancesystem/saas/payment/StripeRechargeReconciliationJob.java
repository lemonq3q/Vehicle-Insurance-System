package com.example.insurancesystem.saas.payment;

import com.example.insurancesystem.saas.mapper.StripePaymentMapper;
import com.example.insurancesystem.saas.support.PortalMaps;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Stripe 充值订单的低负载定时补偿任务。
 * 每五分钟先通过复合索引批量过期“尚未创建 Session”的订单，再只读取已到 Stripe
 * 截止时间的小批候选并查询远端真实状态；单次批量有上限，避免积压时长事务、全表扫描
 * 或瞬间产生大量 Stripe API 请求。
 */
@Component
public class StripeRechargeReconciliationJob {
  private static final Logger log =
      LoggerFactory.getLogger(StripeRechargeReconciliationJob.class);
  private static final int LOCAL_ORDER_TIMEOUT_MINUTES = 30;
  private final StripePaymentMapper mapper;
  private final StripePaymentService paymentService;
  private final StripePaymentProperties properties;

  public StripeRechargeReconciliationJob(
      StripePaymentMapper mapper,
      StripePaymentService paymentService,
      StripePaymentProperties properties) {
    this.mapper = mapper;
    this.paymentService = paymentService;
    this.properties = properties;
  }

  /**
   * 扫描并修正超时订单。
   * 初始延迟避免应用刚启动时与其他初始化任务争抢资源；fixedDelay 从上次执行完成后计算，
   * 确保慢速 Stripe 请求不会导致同一实例内任务重叠。
   */
  @Scheduled(
      initialDelayString = "${saas.payment.stripe.reconciliation-initial-delay-ms:60000}",
      fixedDelayString = "${saas.payment.stripe.reconciliation-interval-ms:300000}")
  public void reconcile() {
    int batchSize = Math.max(1, Math.min(properties.getReconciliationBatchSize(), 500));
    LocalDateTime cutoff = LocalDateTime.now().minusMinutes(LOCAL_ORDER_TIMEOUT_MINUTES);
    int localExpired = mapper.expireOrdersWithoutSession(cutoff, batchSize);

    /*
     * 未配置 Stripe 密钥时仍可完成纯数据库的“未创建 Session”超时清理，但不能查询
     * 远端 Session。直接结束本轮可避免测试环境或关闭支付功能的环境每五分钟产生一批
     * 必然失败的远程调用与告警日志。
     */
    if (properties.getSecretKey() == null || properties.getSecretKey().isBlank()) {
      if (localExpired > 0)
        log.info(
            "Stripe recharge reconciliation completed without remote lookup: localExpired={}",
            localExpired);
      return;
    }

    List<Map<String, Object>> candidates =
        PortalMaps.camel(mapper.findExpiredSessionCandidates(batchSize));
    int reconciled = 0;
    for (Map<String, Object> candidate : candidates) {
      String sessionId = String.valueOf(candidate.get("stripeCheckoutSessionId"));
      try {
        paymentService.reconcileExpiredSession(sessionId);
        reconciled++;
      } catch (Exception exception) {
        /*
         * 单笔 Stripe 或数据库故障不能阻断整个批次。订单保持原状态并在下一轮重试，
         * 日志仅记录不可猜测的 Session ID，禁止记录 client secret 或支付资料。
         */
        log.warn("Failed to reconcile expired Stripe checkout session {}", sessionId, exception);
      }
    }
    if (localExpired > 0 || reconciled > 0)
      log.info(
          "Stripe recharge reconciliation completed: localExpired={}, sessionCandidates={}",
          localExpired,
          reconciled);
  }
}
