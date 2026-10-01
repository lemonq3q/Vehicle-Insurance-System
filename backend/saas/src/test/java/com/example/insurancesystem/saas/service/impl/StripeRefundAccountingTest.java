package com.example.insurancesystem.saas.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.saas.config.WorkorderOverageBillingProperties;
import com.example.insurancesystem.saas.mapper.*;
import com.example.insurancesystem.saas.payment.StripePaymentProperties;
import com.example.insurancesystem.saas.service.*;
import com.example.insurancesystem.saas.service.WalletBalanceService.BalanceChangeResult;
import com.example.insurancesystem.saas.support.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 验证退款业务幂等、部分退款累计限制及负余额回撤，不连接真实 Stripe 或数据库。
 * 以现有Webhook表的内存替身保存退款状态，覆盖不同事件重复通知及失败后余额恢复；
 * 数据库事务及行锁本身仍需部署环境验证。
 */
class StripeRefundAccountingTest {
  private FinanceMapper mapper;
  private WalletBalanceService balances;
  private FinanceServiceImpl service;
  private final Map<String, String> states = new HashMap<>();
  private final List<Map<String, Object>> transactions = new ArrayList<>();
  private BigDecimal balance;
  private Map<String, Object> order;

  /**
   * 构造已到账100元但只剩20元可用余额的企业，模拟退款状态持久化和统一钱包变更入口。
   */
  @BeforeEach
  void setUp() {
    mapper = mock(FinanceMapper.class);
    balances = mock(WalletBalanceService.class);
    StripePaymentProperties properties = new StripePaymentProperties();
    properties.setCurrency("CNY");
    BusinessCodeGenerator codes = mock(BusinessCodeGenerator.class);
    when(codes.transactionNo()).thenReturn("TX_REFUND_TEST");
    service = new FinanceServiceImpl(mapper, mock(EnterpriseMapper.class), mock(PortalContextService.class),
        codes, new com.example.insurancesystem.config.JacksonConfig().persistenceObjectMapper(), mock(MemberSeatService.class), mock(WorkorderOverageBillingMapper.class),
        new WorkorderOverageBillingProperties(), balances, properties);
    order = new LinkedHashMap<>(Map.of(
        "id", 1L, "enterpriseId", 9L, "status", 2, "amount", new BigDecimal("100.00"),
        "stripeCheckoutSessionId", "cs_test", "userId", 3L, "refundAmount", BigDecimal.ZERO));
    when(mapper.lockRechargeOrderByPaymentIntent("pi_test")).thenAnswer(i -> order);
    when(mapper.lockRechargeOrderByStripeSession("cs_test")).thenAnswer(i -> order);
    when(mapper.updateRechargeRefundSummary(eq(1L), eq(9L), any(), anyInt())).thenAnswer(i -> {
      order.put("refundAmount", i.getArgument(2));
      order.put("status", i.getArgument(3));
      return 1;
    });
    when(mapper.lockStripeRefundState(anyString())).thenAnswer(i -> states.get(i.getArgument(0)));
    when(mapper.findStripeRefundStates("cs_test")).thenAnswer(i -> new ArrayList<>(states.values()));
    when(mapper.insertStripeRefundState(anyString(), eq("cs_test"), anyString())).thenAnswer(i -> {
      states.put(i.getArgument(0), i.getArgument(2)); return 1;
    });
    when(mapper.updateStripeRefundState(anyString(), anyString())).thenAnswer(i -> {
      states.put(i.getArgument(0), i.getArgument(1)); return 1;
    });
    balance = new BigDecimal("20.00");
    when(balances.changeBalance(eq(9L), any(BigDecimal.class), isNull(), eq(true))).thenAnswer(i -> {
      BigDecimal before = balance;
      balance = balance.add(i.getArgument(1));
      return new BalanceChangeResult(7L, before, balance);
    });
    when(mapper.insertTransaction(anyMap())).thenAnswer(i -> {
      transactions.add(new HashMap<>(i.getArgument(0))); return 1;
    });
  }

  /** 成功退款即使钱包不够也全额扣回；另一条成功通知不再次扣款或写流水。 */
  @Test
  void successfulRefundIsAppliedOnceAndMayMakeBalanceNegative() {
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 10000, "succeeded");
    assertTrue(states.get("saas-refund-state:re_a").contains("\"rechargeOrderId\":1"));
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 10000, "succeeded");
    assertEquals(new BigDecimal("-80.00"), balance);
    assertEquals(1, transactions.size());
    assertEquals("OUT", transactions.get(0).get("direction"));
    assertEquals("REFUND", transactions.get(0).get("transactionType"));
    assertEquals(9, order.get("status"));
    assertEquals(new BigDecimal("100.00"), order.get("refundAmount"));
    service.completeStripeRecharge("cs_test", "pi_test");
    verify(balances, times(1)).changeBalance(eq(9L), any(), isNull(), eq(true));
  }

  /**
   * 模拟生产环境保存字符串订单 ID 的处理中退款，随后成功通知应完成余额回撤。
   * 同一退款重复成功通知只生成一次流水，兼容历史数字 ID 的现有测试保持不变。
   */
  @Test
  void stringOrderIdFromPendingRefundCanBeCompletedExactlyOnce() {
    states.put("saas-refund-state:re_a", "{\"rechargeOrderId\":\"1\",\"paymentIntentId\":\"pi_test\","
        + "\"currency\":\"cny\",\"amount\":30.00,\"appliedAmount\":0,\"status\":\"pending\"}");
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "succeeded");
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "succeeded");
    assertEquals(new BigDecimal("-10.00"), balance);
    assertEquals(1, transactions.size());
    assertEquals(8, order.get("status"));
  }

  /** 损坏的订单 ID 不允许截断转换或触发钱包扣减，等待修复后重试通知。 */
  @Test
  void invalidStoredOrderIdDoesNotDebitBalance() {
    for (String id : List.of("1.5", "9223372036854775808", "invalid")) {
      states.put("saas-refund-state:re_a", "{\"rechargeOrderId\":\"" + id + "\"}");
      assertThrows(BusinessException.class,
          () -> service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "succeeded"));
    }
    verifyNoInteractions(balances);
  }

  /** 创建但未成功的退款只记录状态，不扣余额；失败通知也不凭空补钱。 */
  @Test
  void pendingAndFailedRefundsWithoutDebitDoNotChangeBalance() {
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "pending");
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "failed");
    assertEquals(new BigDecimal("20.00"), balance);
    assertTrue(transactions.isEmpty());
    verifyNoInteractions(balances);
  }

  /** 已成功退款后来失败时只补回一次；再次等待操作并成功时按差额重新回撤。 */
  @Test
  void returnedRefundFundsAreRestoredExactlyOnce() {
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "succeeded");
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "requires_action");
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "requires_action");
    assertEquals(new BigDecimal("20.00"), balance);
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "succeeded");
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "failed");
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "failed");
    assertEquals(new BigDecimal("20.00"), balance);
    assertEquals(4, transactions.size());
    assertEquals("IN", transactions.get(1).get("direction"));
    assertEquals(2, order.get("status"));
    assertEquals(0, ((BigDecimal) order.get("refundAmount")).signum());
  }

  /** 多笔部分退款各自幂等，累计达到原充值金额后不能再额外扣款。 */
  @Test
  void partialRefundsCannotExceedTheOriginalRecharge() {
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "succeeded");
    assertEquals(8, order.get("status"));
    assertEquals(new BigDecimal("30.00"), order.get("refundAmount"));
    service.completeStripeRecharge("cs_test", "pi_test");
    service.reconcileStripeRefund("re_b", "pi_test", "cny", 7000, "succeeded");
    assertEquals(9, order.get("status"));
    assertThrows(BusinessException.class,
        () -> service.reconcileStripeRefund("re_c", "pi_test", "cny", 100, "succeeded"));
    assertEquals(new BigDecimal("-80.00"), balance);
    assertEquals(2, transactions.size());
  }

  /** 同一个退款标识出现不同金额或币种时拒绝处理，防止状态损坏引发额外回撤。 */
  @Test
  void rejectsChangedAmountAndCurrency() {
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "succeeded");
    assertThrows(BusinessException.class,
        () -> service.reconcileStripeRefund("re_a", "pi_test", "cny", 4000, "succeeded"));
    assertThrows(BusinessException.class,
        () -> service.reconcileStripeRefund("re_b", "pi_test", "usd", 3000, "succeeded"));
    assertEquals(1, transactions.size());
  }

  /** 多笔退款中的一笔补回，只恢复对应金额，另一笔仍有效时维持部分退款。 */
  @Test
  void failedRefundRestoresPartialThenPaidSummary() {
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "succeeded");
    service.reconcileStripeRefund("re_b", "pi_test", "cny", 7000, "succeeded");
    service.reconcileStripeRefund("re_b", "pi_test", "cny", 7000, "failed");
    assertEquals(8, order.get("status"));
    assertEquals(new BigDecimal("30.00"), order.get("refundAmount"));
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "failed");
    assertEquals(2, order.get("status"));
    assertEquals(new BigDecimal("20.00"), balance);
  }

  /** 历史摘要缺失时由已有幂等记录修复显示状态，不增加钱包扣款次数。 */
  @Test
  void repairsSummaryWithoutRepeatingDebit() {
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "succeeded");
    order.put("status", 2);
    order.put("refundAmount", BigDecimal.ZERO);
    service.reconcileStripeRefund("re_a", "pi_test", "cny", 3000, "succeeded");
    assertEquals(8, order.get("status"));
    assertEquals(new BigDecimal("30.00"), order.get("refundAmount"));
    assertEquals(1, transactions.size());
  }
}
