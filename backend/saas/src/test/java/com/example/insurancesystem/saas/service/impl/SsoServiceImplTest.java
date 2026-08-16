package com.example.insurancesystem.saas.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.saas.mapper.EnterpriseMapper;
import com.example.insurancesystem.saas.mapper.FinanceMapper;
import com.example.insurancesystem.saas.service.PortalAuthService;
import com.example.insurancesystem.saas.support.PortalContextService;
import com.example.insurancesystem.utils.RedisCache;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 验证门户点击进入车险系统时在签发 code 前执行账号、成员、企业和套餐校验。
 */
class SsoServiceImplTest {
  private EnterpriseMapper enterprises;
  private FinanceMapper finance;
  private RedisCache redis;
  private SsoServiceImpl service;

  @BeforeEach
  void setUp() {
    PortalContextService context = mock(PortalContextService.class);
    when(context.userId()).thenReturn(9L);
    enterprises = mock(EnterpriseMapper.class);
    finance = mock(FinanceMapper.class);
    redis = mock(RedisCache.class);
    service = new SsoServiceImpl(
        context,
        enterprises,
        finance,
        redis,
        mock(PortalAuthService.class),
        "http://localhost:8888",
        "http://localhost:8889",
        "secret",
        60);
  }

  @Test
  void rejectsArrearsSuspendedSubscriptionBeforeIssuingCode() {
    stubEnabledIdentity();
    when(finance.findSubscription(20L)).thenReturn(subscription(3, "ARREARS"));

    BusinessException exception = assertThrows(BusinessException.class, service::authorize);

    assertEquals(403, exception.getCode());
    assertEquals("企业套餐因余额欠费已暂停，请先充值后再进入车险系统", exception.getMsg());
    verifyNoInteractions(redis);
  }

  @Test
  void issuesCodeOnlyForEnabledIdentityWithActiveUnexpiredPlan() {
    stubEnabledIdentity();
    when(finance.findSubscription(20L)).thenReturn(subscription(1, null));

    Map<String, Object> result = service.authorize();

    assertTrue(String.valueOf(result.get("redirectUrl")).startsWith("http://localhost:8888/sso/callback?code="));
    verify(redis).setCacheObject(startsWith("sso:insurance:code:"), anyMap(), eq(60), any());
  }

  private void stubEnabledIdentity() {
    when(enterprises.findCurrentMember(9L)).thenReturn(Map.of(
        "id", 8L, "enterprise_id", 20L, "user_id", 9L, "status", 1, "user_status", 1));
    when(enterprises.findEnterprise(20L)).thenReturn(Map.of("id", 20L, "status", 1));
  }

  private Map<String, Object> subscription(int status, String reason) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", 7L);
    value.put("enterprise_id", 20L);
    value.put("plan_id", 5L);
    value.put("status", status);
    value.put("suspend_reason", reason);
    value.put("end_at", LocalDateTime.now().plusDays(1));
    return value;
  }
}
