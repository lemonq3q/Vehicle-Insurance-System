package com.example.insurancesystem.monitor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.insurancesystem.monitor.domain.ReminderMergeRequest;
import com.example.insurancesystem.monitor.mapper.MonitorReminderMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** 验证客服提醒首次创建、重复忽略和更高阶段重新打开所依赖的服务分支。 */
class MonitorReminderServiceTest {
    @Mock private MonitorReminderMapper mapper;
    private MonitorReminderService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new MonitorReminderService(mapper);
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
