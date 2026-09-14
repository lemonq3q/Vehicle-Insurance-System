package com.example.insurancesystem.saas.service.impl;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.insurancesystem.saas.config.BalanceAccessProperties;
import com.example.insurancesystem.saas.config.EnterpriseDataRetentionProperties;
import com.example.insurancesystem.saas.config.ReminderProperties;
import com.example.insurancesystem.saas.config.WorkorderOverageBillingProperties;
import com.example.insurancesystem.saas.integration.client.MonitorReminderClient;
import com.example.insurancesystem.saas.mapper.ReminderMapper;
import com.example.insurancesystem.saas.support.PortalContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 验证每日维护对自动续费套餐下架风险的双表同步。测试只覆盖本规则，候选订阅不配置余额和工单风险，
 * 避免其他提醒类别影响断言。
 */
class ReminderServiceImplTest {
    private ReminderMapper mapper;
    private MonitorReminderClient monitor;
    private ReminderServiceImpl service;

    /** 使用默认 7 天提醒阈值和独立 Mock 依赖创建提醒服务。 */
    @BeforeEach
    void setUp() {
        mapper = mock(ReminderMapper.class);
        monitor = mock(MonitorReminderClient.class);
        service = new ReminderServiceImpl(mapper, mock(PortalContextService.class), monitor,
                new ReminderProperties(), new BalanceAccessProperties(), new EnterpriseDataRetentionProperties(),
                new WorkorderOverageBillingProperties(), new ObjectMapper().findAndRegisterModules());
    }

    /** 已开启自动续费、七天内到期且目标套餐下架时，应写企业提醒并向监控端发送相同稳定键。 */
    @Test
    void emitsReminderWhenRenewalPlanIsUnavailable() {
        Map<String, Object> source = candidate(0);
        when(mapper.findReminderCandidates()).thenReturn(List.of(source));
        when(mapper.insert(org.mockito.ArgumentMatchers.anyMap())).thenReturn(1);

        service.generateDailyReminders();

        verify(mapper).insert(argThat(value ->
                "AUTO_RENEW_PLAN_UNAVAILABLE".equals(value.get("reminderType"))));
        verify(monitor).merge(argThat(value ->
                String.valueOf(value.get("reminderKey")).startsWith("AUTO_RENEW_PLAN_UNAVAILABLE:subscription-8:renew-")));
    }

    /** 同周期目标套餐恢复上架时，即使企业侧记录可能已不存在，也必须继续请求监控端幂等删除。 */
    @Test
    void deletesBothRemindersWhenRenewalPlanReturns() {
        Map<String, Object> source = candidate(1);
        when(mapper.findReminderCandidates()).thenReturn(List.of(source));

        service.generateDailyReminders();

        verify(mapper).deleteAutoRenewPlanUnavailable(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.startsWith("AUTO_RENEW_PLAN_UNAVAILABLE:subscription-8:renew-"));
        verify(monitor).delete(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.startsWith("AUTO_RENEW_PLAN_UNAVAILABLE:subscription-8:renew-"));
    }

    /** 生成一条处于七天提醒窗口内的有效自动续费订阅快照。 */
    private Map<String, Object> candidate(int renewalPlanStatus) {
        Map<String, Object> source = new HashMap<>();
        source.put("subscriptionId", 8L); source.put("enterpriseId", 1L);
        source.put("subscriptionStatus", 1); source.put("autoRenewEnabled", 1);
        source.put("startAt", LocalDateTime.now().minusMonths(1));
        source.put("endAt", LocalDateTime.now().plusDays(5));
        source.put("nextRenewAt", source.get("endAt"));
        source.put("enterpriseName", "测试企业"); source.put("enterpriseCode", "ENT001");
        source.put("planName", "专业版"); source.put("renewalPlanName", "专业版");
        source.put("renewalPlanStatus", renewalPlanStatus);
        source.put("workorderLimit", 0); source.put("balanceAmount", 0); source.put("frozenAmount", 0);
        source.put("renewalAmount", 0); source.put("walletId", 1L); source.put("arrearsCycleId", 0L);
        return source;
    }
}
