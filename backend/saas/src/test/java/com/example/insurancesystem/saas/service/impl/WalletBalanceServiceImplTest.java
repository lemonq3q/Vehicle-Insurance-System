package com.example.insurancesystem.saas.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.insurancesystem.saas.config.BalanceAccessProperties;
import com.example.insurancesystem.saas.mapper.FinanceMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 验证统一余额入口的负余额策略和有效套餐状态边界。
 * 测试使用 Mapper mock 隔离数据库，重点确保工单扣费可透支、普通付款不可透支、双阈值采用严格比较，
 * 且过期套餐与非欠费暂停不会被自动改写。
 */
class WalletBalanceServiceImplTest {
  private FinanceMapper mapper;
  private WalletBalanceServiceImpl service;

  @BeforeEach
  void setUp() {
    mapper = mock(FinanceMapper.class);
    BalanceAccessProperties properties = new BalanceAccessProperties();
    properties.setSuspendThreshold(new BigDecimal("-100.00"));
    properties.setRestoreThreshold(BigDecimal.ZERO);
    service = new WalletBalanceServiceImpl(
        mapper, properties, mock(ApplicationEventPublisher.class));
    when(mapper.updateWallet(anyMap())).thenReturn(1);
  }

  @Test
  void allowsWorkorderChargeToMakeBalanceNegativeAndSuspendsActivePlanBelowThreshold() {
    stubState(1, null, LocalDateTime.now().plusDays(10), new BigDecimal("-90.00"));

    var result = service.changeBalance(20L, new BigDecimal("-20.00"), null, true);

    assertEquals(new BigDecimal("-110.00"), result.balanceAfter());
    verify(mapper).suspendSubscriptionForArrears(7L, "ARREARS");
  }

  @Test
  void rejectsOrdinaryPaymentThatWouldMakeBalanceNegative() {
    stubState(1, null, LocalDateTime.now().plusDays(10), new BigDecimal("10.00"));

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> service.changeBalance(20L, new BigDecimal("-20.00"), 9L, false));

    assertEquals("企业余额不足", exception.getMessage());
    verify(mapper, never()).updateWallet(anyMap());
  }

  @Test
  void restoresOnlyArrearsSuspensionWhenBalanceIsStrictlyAboveRestoreThreshold() {
    stubState(3, "ARREARS", LocalDateTime.now().plusDays(10), new BigDecimal("-10.00"));

    service.changeBalance(20L, new BigDecimal("10.01"), 9L, false);

    verify(mapper).restoreArrearsSubscription(7L, "ARREARS");
  }

  @Test
  void doesNotSwitchStateAtExactThresholds() {
    stubState(1, null, LocalDateTime.now().plusDays(10), new BigDecimal("-90.00"));
    service.changeBalance(20L, new BigDecimal("-10.00"), null, true);
    verify(mapper, never()).suspendSubscriptionForArrears(anyLong(), anyString());

    reset(mapper);
    when(mapper.updateWallet(anyMap())).thenReturn(1);
    stubState(3, "ARREARS", LocalDateTime.now().plusDays(10), new BigDecimal("-10.00"));
    service.changeBalance(20L, new BigDecimal("10.00"), 9L, false);
    verify(mapper, never()).restoreArrearsSubscription(anyLong(), anyString());
  }

  @Test
  void leavesExpiredAndManualSuspensionsUntouched() {
    stubState(1, null, LocalDateTime.now().minusSeconds(1), new BigDecimal("-90.00"));
    service.changeBalance(20L, new BigDecimal("-20.00"), null, true);
    verify(mapper, never()).suspendSubscriptionForArrears(anyLong(), anyString());

    reset(mapper);
    when(mapper.updateWallet(anyMap())).thenReturn(1);
    stubState(3, "MANUAL", LocalDateTime.now().plusDays(10), new BigDecimal("-10.00"));
    service.changeBalance(20L, new BigDecimal("20.00"), 9L, false);
    verify(mapper, never()).restoreArrearsSubscription(anyLong(), anyString());
  }

  /** 构造企业唯一订阅和钱包锁查询结果，使每个测试只表达待验证的业务边界。 */
  private void stubState(int status, String reason, LocalDateTime endAt, BigDecimal balance) {
    Map<String, Object> subscription = new LinkedHashMap<>();
    subscription.put("id", 7L);
    subscription.put("enterprise_id", 20L);
    subscription.put("plan_id", 5L);
    subscription.put("status", status);
    subscription.put("suspend_reason", reason);
    subscription.put("end_at", endAt);
    Map<String, Object> wallet = new LinkedHashMap<>();
    wallet.put("id", 8L);
    wallet.put("enterprise_id", 20L);
    wallet.put("balance_amount", balance);
    when(mapper.lockSubscription(20L)).thenReturn(subscription);
    when(mapper.lockWallet(20L)).thenReturn(wallet);
  }
}
