package com.example.insurancesystem.saas.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.insurancesystem.saas.mapper.StripePaymentMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证充值订单定时补偿的批量上限、无密钥降级及逐 Session 故障隔离。
 * 测试不访问真实 Stripe 或数据库，只约束任务编排不会在配置缺失时制造远程请求，
 * 也不会因一笔异常而遗漏同批次后续订单。
 */
class StripeRechargeReconciliationJobTest {

  /** 未配置 Secret Key 时只执行本地无 Session 订单清理，不读取远端候选。 */
  @Test
  void skipsRemoteCandidatesWhenSecretKeyIsMissing() {
    StripePaymentMapper mapper = mock(StripePaymentMapper.class);
    StripePaymentService paymentService = mock(StripePaymentService.class);
    StripePaymentProperties properties = properties(null, 100);
    StripeRechargeReconciliationJob job =
        new StripeRechargeReconciliationJob(mapper, paymentService, properties);

    job.reconcile();

    verify(mapper).expireOrdersWithoutSession(any(LocalDateTime.class), eq(100));
  }

  /** 一笔远端补偿失败后仍继续处理同批次后续 Session，避免坏数据阻塞整个队列。 */
  @Test
  void continuesAfterOneSessionFails() {
    StripePaymentMapper mapper = mock(StripePaymentMapper.class);
    StripePaymentService paymentService = mock(StripePaymentService.class);
    StripePaymentProperties properties = properties("sk_test_example", 100);
    when(mapper.findExpiredSessionCandidates(100))
        .thenReturn(
            List.of(
                Map.of("stripe_checkout_session_id", "cs_failed"),
                Map.of("stripe_checkout_session_id", "cs_next")));
    doThrow(new IllegalStateException("temporary Stripe failure"))
        .when(paymentService)
        .reconcileExpiredSession("cs_failed");
    StripeRechargeReconciliationJob job =
        new StripeRechargeReconciliationJob(mapper, paymentService, properties);

    job.reconcile();

    verify(paymentService).reconcileExpiredSession("cs_failed");
    verify(paymentService).reconcileExpiredSession("cs_next");
  }

  private StripePaymentProperties properties(String secretKey, int batchSize) {
    StripePaymentProperties properties = new StripePaymentProperties();
    properties.setSecretKey(secretKey);
    properties.setReconciliationBatchSize(batchSize);
    return properties;
  }
}
