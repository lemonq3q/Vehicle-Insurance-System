package com.example.insurancesystem.saas.service.impl;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;

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
        when(mapper.findPendingSyncBatch(0, 100)).thenReturn(List.of(stored(
                "AUTO_RENEW_PLAN_UNAVAILABLE",
                "AUTO_RENEW_PLAN_UNAVAILABLE:subscription-8:renew-" + source.get("endAt"))));

        service.generateDailyReminders();

        verify(mapper).insert(argThat(value ->
                "AUTO_RENEW_PLAN_UNAVAILABLE".equals(value.get("reminderType"))));
        verify(monitor).merge(argThat(value ->
                String.valueOf(value.get("reminderKey")).startsWith("AUTO_RENEW_PLAN_UNAVAILABLE:subscription-8:renew-")));
    }

    /**
     * 套餐恢复上架不删除历史，周期复核发现稳定键不再满足条件后按版本失效。
     */
    @Test
    void deletesBothRemindersWhenRenewalPlanReturns() {
        Map<String, Object> source = candidate(1);
        Map<String, Object> stored = stored("AUTO_RENEW_PLAN_UNAVAILABLE",
                "AUTO_RENEW_PLAN_UNAVAILABLE:subscription-8:renew-" + source.get("endAt"));
        when(mapper.findActiveBatch(0, 100)).thenReturn(List.of(stored));
        when(mapper.findReminderCandidates()).thenReturn(List.of(source));
        service.generateDailyReminders();
        verify(mapper).invalidate(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());
        verify(monitor, never()).delete(anyLong(), anyString());
    }

    /**
     * 超过一个月的负余额提醒仍命中同一欠费周期，不因年龄或旧expiresAt而失效。
     */
    @Test
    void retainsOldReminderWhileBalanceIsNegative() {
        Map<String, Object> source = candidate(1);
        source.put("balanceAmount", -100);
        Map<String, Object> stored = stored("WALLET_BALANCE_NEGATIVE", "WALLET_BALANCE_NEGATIVE:wallet-1:cycle-current-negative");
        stored.put("lastTriggeredAt", LocalDateTime.now().minusMonths(3));
        stored.put("expiresAt", LocalDateTime.now().minusMonths(2));
        when(mapper.findActiveBatch(0, 100)).thenReturn(List.of(stored));
        when(mapper.findReminderCandidates()).thenReturn(List.of(source));
        service.generateDailyReminders();
        verify(mapper, never()).invalidate(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
    }

    /**
     * 暂停提醒以实际订阅状态为准；余额已补到0但状态尚未恢复时仍保留，避免隐藏尚未恢复的服务风险。
     */
    @Test
    void keepsSuspensionReminderAtZeroBalanceUntilSubscriptionRestores() {
        Map<String, Object> source = candidate(1);
        source.put("subscriptionStatus", 3); source.put("suspendReason", "ARREARS");
        source.put("suspendedAt", LocalDateTime.now().minusDays(1));
        Map<String, Object> row = stored("SUBSCRIPTION_SUSPENDED_ARREARS",
                "SUBSCRIPTION_SUSPENDED_ARREARS:subscription-8:suspended-" + source.get("suspendedAt"));
        when(mapper.findActiveBatch(0, 100)).thenReturn(List.of(row));
        when(mapper.findReminderCandidates()).thenReturn(List.of(source));
        service.generateDailyReminders();
        verify(mapper, never()).invalidate(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
    }

    /**
     * 充值恢复、企业删除或原业务周期不存在时，原稳定键不再命中；未知扩展类型保守保留。
     */
    @Test
    void invalidatesMissingRiskButKeepsUnknownTypes() {
        Map<String, Object> known = stored("WALLET_BALANCE_NEGATIVE", "old-cycle");
        Map<String, Object> unknown = stored("CUSTOM_RISK", "custom-key"); unknown.put("id", 10L);
        when(mapper.findActiveBatch(0, 100)).thenReturn(List.of(known, unknown));
        when(mapper.findReminderCandidates()).thenReturn(List.of(candidate(1)));
        service.generateDailyReminders();
        verify(mapper).invalidate(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).invalidate(org.mockito.ArgumentMatchers.eq(10L), anyLong(), org.mockito.ArgumentMatchers.any());
    }

    /**
     * 待同步含已失效记录；单条网络故障不确认版本，下一条仍同步成功并清除自己的待同步标记。
     */
    @Test
    void retriesInactiveSyncWithoutBlockingFollowingRows() {
        Map<String, Object> failed = stored("WALLET_BALANCE_NEGATIVE", "failed"); failed.put("isActive", 0);
        Map<String, Object> success = stored("WALLET_BALANCE_NEGATIVE", "success"); success.put("id", 10L);
        when(mapper.findPendingSyncBatch(0, 100)).thenReturn(List.of(failed, success));
        doThrow(new IllegalStateException("network unavailable")).when(monitor).merge(argThat(value -> "failed".equals(value.get("reminderKey"))));
        service.generateDailyReminders();
        verify(mapper, never()).acknowledgeSync(9L, 1L);
        verify(mapper).acknowledgeSync(10L, 1L);
        verify(mapper).findPendingSyncBatch(10, 100);
    }

    /**
     * 最近提醒接口只委托生效查询，保持原响应结构，不接受客户端通过时间参数隐藏旧风险。
     */
    @Test
    void recentQueryPreservesResponseFields() {
        assertEquals(List.of(), service.recent());
    }

    /**
     * 真实Mapper动态SQL必须正确解析工单时间比较及企业IN查询；生效列表不得再包含月份限制。
     */
    @Test
    void parsesReminderMapperSqlWithoutMonthFilter() {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.addMapper(ReminderMapper.class);
        String sql = configuration.getMappedStatement(ReminderMapper.class.getName() + ".findReminderCandidatesByEnterpriseIds")
                .getBoundSql(Map.of("enterpriseIds", List.of(1L, 2L))).getSql();
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("b.created_at<s.end_at"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("s.enterprise_id IN"));
        sql = configuration.getMappedStatement(ReminderMapper.class.getName() + ".findRecent")
                .getBoundSql(Map.of("enterpriseId", 1L)).getSql();
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("is_active=1"));
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("INTERVAL 1 MONTH"));
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("expires_at>"));
    }

    /**
     * 每日维护必须先清理旧周期，再更新当前周期；已存在且没有升级的提醒走降级条件SQL。
     */
    @Test
    void dailyMaintenanceInvalidatesBeforeAdjustingCurrentStage() {
        Map<String, Object> source = candidate(0);
        when(mapper.findReminderCandidates()).thenReturn(List.of(source));
        when(mapper.findActiveBatch(0, 100)).thenReturn(List.of(stored("WALLET_BALANCE_NEGATIVE", "old-cycle")));
        service.generateDailyReminders();
        org.mockito.InOrder ordered = org.mockito.Mockito.inOrder(mapper);
        ordered.verify(mapper).invalidate(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());
        ordered.verify(mapper).insert(org.mockito.ArgumentMatchers.anyMap());
        ordered.verify(mapper).upgrade(org.mockito.ArgumentMatchers.anyMap());
        ordered.verify(mapper).downgrade(argThat(row -> "AUTO_RENEW_PLAN_UNAVAILABLE".equals(row.get("reminderType"))));
        verify(mapper, never()).findReminderCandidatesByEnterpriseIds(org.mockito.ArgumentMatchers.anyList());
    }

    /**
     * 一次每日维护连续处理多个游标批次，已失效的前批不会使后批跳过；复核结束后才进入同步。
     */
    @Test
    void dailyMaintenanceScansAllBatchesBeforeSync() {
        Map<String, Object> first = stored("WALLET_BALANCE_NEGATIVE", "old-first");
        Map<String, Object> second = stored("WALLET_BALANCE_NEGATIVE", "old-second"); second.put("id", 20L);
        when(mapper.findActiveBatch(0, 100)).thenReturn(List.of(first));
        when(mapper.findActiveBatch(9, 100)).thenReturn(List.of(second));
        service.generateDailyReminders();
        org.mockito.InOrder ordered = org.mockito.Mockito.inOrder(mapper);
        ordered.verify(mapper).invalidate(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());
        ordered.verify(mapper).invalidate(org.mockito.ArgumentMatchers.eq(20L), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any());
        ordered.verify(mapper).findActiveBatch(20, 100);
        ordered.verify(mapper).findPendingSyncBatch(0, 100);
    }

    /**
     * 失效记录成功恢复或阶段升级后不应再调用降级，避免一次维护重复修改同一状态。
     */
    @Test
    void dailyMaintenanceDoesNotDowngradeAfterUpgradeOrReactivation() {
        when(mapper.findReminderCandidates()).thenReturn(List.of(candidate(0)));
        when(mapper.upgrade(org.mockito.ArgumentMatchers.anyMap())).thenReturn(1);
        service.generateDailyReminders();
        verify(mapper, never()).downgrade(org.mockito.ArgumentMatchers.anyMap());
    }

    /**
     * 降级SQL必须限制生效且阶段下降，并保持阅读、最近触发和触发次数；版本仍推进以同步监控。
     */
    @Test
    void downgradeSqlPreservesReadAndTriggerHistory() {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.addMapper(ReminderMapper.class);
        String sql = configuration.getMappedStatement(ReminderMapper.class.getName() + ".downgrade")
                .getBoundSql(new HashMap<>()).getSql();
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("stage_level>"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("is_active=1"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.contains("lifecycle_version=lifecycle_version+1"));
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("is_read="));
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("trigger_count="));
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("last_triggered_at="));
    }

    /**
     * 模拟持久化生命周期快照，编号及版本供条件更新和同步确认测试使用。
     */
    private Map<String, Object> stored(String type, String key) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 9L); row.put("enterpriseId", 1L); row.put("reminderType", type); row.put("reminderKey", key);
        row.put("lifecycleVersion", 1L); row.put("isActive", 1); row.put("lastTriggeredAt", LocalDateTime.now());
        row.put("businessDataJson", "{\"enterpriseName\":\"测试企业\",\"enterpriseCode\":\"ENT001\"}");
        row.put("severity", "WARNING"); row.put("title", "余额提醒"); row.put("content", "请及时充值");
        return row;
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
