package com.example.insurancesystem.monitor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.insurancesystem.monitor.domain.ReminderMergeRequest;
import com.example.insurancesystem.monitor.mapper.MonitorReminderMapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** 验证客服提醒首次创建、重复忽略和更高阶段重新打开所依赖的服务分支。 */
class MonitorReminderServiceTest {
    @Mock private MonitorReminderMapper mapper;
    @Mock private MonitorSystemLogService systemLog;
    private MonitorReminderService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new MonitorReminderService(mapper, systemLog);
        /* 所有合并用例都使用已注册的内置类型，字典合法性不是这些阶段覆盖测试的变量。 */
        when(mapper.countEnabledType("SUBSCRIPTION_EXPIRING_NO_AUTO_RENEW")).thenReturn(1);
    }

    /** 同一合并键不存在时创建提醒，不执行升级 SQL。 */
    @Test
    void createsFirstStage() {
        ReminderMergeRequest request = request(10);
        when(mapper.lock(1L, request.reminderKey)).thenReturn(null);
        assertEquals("CREATED", service.merge(request));
        verify(mapper).insert(request);
        verify(mapper, never()).upgrade(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    /** 相同或更低阶段保持现状，避免维护重试反复重置客服处理状态。 */
    @Test
    void ignoresSameStage() {
        ReminderMergeRequest request = request(10);
        when(mapper.lock(1L, request.reminderKey)).thenReturn(Map.of("id", 8L, "stageLevel", 10));
        assertEquals("IGNORED", service.merge(request));
        verify(mapper, never()).upgrade(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    /** 更高阶段调用升级 SQL，由 Mapper 同步保存旧处理快照并恢复待处理。 */
    @Test
    void upgradesHigherStage() {
        ReminderMergeRequest request = request(20);
        when(mapper.lock(1L, request.reminderKey)).thenReturn(Map.of("id", 8L, "stageLevel", 10));
        when(mapper.upgrade(8L, request)).thenReturn(1);
        assertEquals("UPDATED", service.merge(request));
        verify(mapper).upgrade(8L, request);
    }

    /** JDBC 驱动保留下划线键名时，提醒类别选项仍应正确组装而不是产生空值异常。 */
    @Test
    void buildsFilterOptionsFromUnderscoreAliases() {
        when(mapper.findFilterOptions()).thenReturn(List.of(
                Map.of("option_kind", "TYPE", "category_code", "SUBSCRIPTION", "category_name", "套餐与续费",
                        "type_code", "SUBSCRIPTION_EXPIRING_NO_AUTO_RENEW", "type_name", "套餐即将到期")));
        Map<String, Object> result = service.filterOptions();
        assertEquals(1, ((List<?>) result.get("categories")).size());
    }

    /** 空关键词不查询企业全集，有效关键词原样传给受限的数据库模糊查询。 */
    @Test
    void searchesEnterprisesOnlyAfterKeywordInput() {
        assertEquals(List.of(), service.searchEnterprises("  "));
        verify(mapper, never()).searchEnterprises(org.mockito.ArgumentMatchers.anyString());
        when(mapper.searchEnterprises("示例")).thenReturn(List.of(Map.of("id", 1L, "name", "示例企业", "code", "ENT001")));
        assertEquals(1, service.searchEnterprises(" 示例 ").size());
        verify(mapper).searchEnterprises("示例");
    }

    /**
     * JDBC 返回下划线 Map 键时，列表必须转换为前端契约字段。
     * 该回归用例直接覆盖企业链接曾生成 NaN、待处理按钮被误判为已处理的共同根因。
     */
    @Test
    void normalizesReminderRowsForEnterpriseLinkAndProcessAction() {
        Map<String, Object> row = new HashMap<>(Map.of(
                "id", 8L, "enterprise_id", 1L, "enterprise_name_snapshot", "测试企业",
                "enterprise_code", "ENT001", "enterprise_contact_name", "张三",
                "enterprise_contact_phone", "13800138000", "process_status", 0,
                "last_triggered_at", LocalDateTime.now(), "revision", 2));
        when(mapper.countPage(null, null, null, null, null)).thenReturn(1L);
        when(mapper.findPage(null, null, null, null, null, 0, 10)).thenReturn(List.of(row));

        Map<String, Object> result = service.page(null, null, null, null, null, 1, 10);
        Map<?, ?> normalized = (Map<?, ?>) ((List<?>) result.get("list")).get(0);

        assertEquals(1L, normalized.get("enterpriseId"));
        assertEquals("测试企业", normalized.get("enterpriseNameSnapshot"));
        assertEquals("ENT001", normalized.get("enterpriseCode"));
        assertEquals("张三", normalized.get("enterpriseContactName"));
        assertEquals("13800138000", normalized.get("enterpriseContactPhone"));
        assertEquals(0, normalized.get("processStatus"));
    }

    /** 人工处理提醒成功后必须写入统一日志，且事件编码和操作人不能丢失。 */
    @Test
    void recordsLogAfterReminderProcessed() {
        when(mapper.lockForProcessing(8L)).thenReturn(Map.of(
                "id", 8L, "enterpriseId", 1L, "enterpriseNameSnapshot", "测试企业",
                "reminderType", "SUBSCRIPTION_EXPIRING_NO_AUTO_RENEW", "title", "套餐到期提醒",
                "processStatus", 0, "revision", 2));
        when(mapper.markProcessed(8L, 2, 1L, "已电话确认")).thenReturn(1);

        service.markProcessed(8L, 2, 1L, "lemon", "已电话确认");

        verify(systemLog).recordOperation(
                org.mockito.ArgumentMatchers.eq("REMINDER_PROCESSED"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("reminder"),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("lemon"),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("测试企业"),
                org.mockito.ArgumentMatchers.eq("REMINDER"), org.mockito.ArgumentMatchers.eq(8L),
                org.mockito.ArgumentMatchers.eq("套餐到期提醒"),
                org.mockito.ArgumentMatchers.eq("已电话确认"), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** 处理锁查询同样兼容下划线键，避免按钮恢复后提交动作再次因字段读取失败而报错。 */
    @Test
    void processesReminderWithUnderscoreMapKeys() {
        when(mapper.lockForProcessing(8L)).thenReturn(Map.of(
                "id", 8L, "enterprise_id", 1L, "enterprise_name_snapshot", "测试企业",
                "title", "套餐到期提醒", "process_status", 0, "revision", 2));
        when(mapper.markProcessed(8L, 2, 1L, null)).thenReturn(1);

        service.markProcessed(8L, 2, 1L, "lemon", null);

        verify(mapper).markProcessed(8L, 2, 1L, null);
    }

    /** 已处理提醒可按页面版本恢复为待处理，并写入恢复状态的数据库更新。 */
    @Test
    void restoresProcessedReminderToPending() {
        when(mapper.lockForProcessing(8L)).thenReturn(Map.of(
                "id", 8L, "enterprise_id", 1L, "enterprise_name_snapshot", "测试企业",
                "title", "套餐到期提醒", "process_status", 1, "revision", 3,
                "process_remark", "已电话确认"));
        when(mapper.restoreUnprocessed(8L, 3)).thenReturn(1);

        service.restoreUnprocessed(8L, 3, 1L, "lemon");

        verify(mapper).restoreUnprocessed(8L, 3);
        verify(systemLog).recordOperation(
                org.mockito.ArgumentMatchers.eq("REMINDER_REOPENED"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("reminder"),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("lemon"),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("测试企业"),
                org.mockito.ArgumentMatchers.eq("REMINDER"), org.mockito.ArgumentMatchers.eq(8L),
                org.mockito.ArgumentMatchers.eq("套餐到期提醒"), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** 套餐恢复上架后按企业和周期键删除对应提醒，重复清理返回忽略并保持幂等。 */
    @Test
    void deletesUnavailablePlanReminderIdempotently() {
        String key = "AUTO_RENEW_PLAN_UNAVAILABLE:subscription-8:renew-2026-09-01T00:00";
        when(mapper.deleteAutoRenewPlanUnavailable(1L, key)).thenReturn(1, 0);

        assertEquals("DELETED", service.deleteAutoRenewPlanUnavailable(1L, key));
        assertEquals("IGNORED", service.deleteAutoRenewPlanUnavailable(1L, key));
    }

    private ReminderMergeRequest request(int level) {
        ReminderMergeRequest request = new ReminderMergeRequest();
        request.enterpriseId = 1L;
        request.enterpriseName = "测试企业";
        request.reminderType = "SUBSCRIPTION_EXPIRING_NO_AUTO_RENEW";
        request.reminderKey = "subscription-1:end-2026-08-20";
        request.reminderStage = level == 20 ? "1D" : "7D";
        request.stageLevel = level;
        request.severity = "WARNING";
        request.title = "套餐到期提醒";
        request.content = "套餐即将到期";
        request.triggeredAt = LocalDateTime.now();
        return request;
    }
}
