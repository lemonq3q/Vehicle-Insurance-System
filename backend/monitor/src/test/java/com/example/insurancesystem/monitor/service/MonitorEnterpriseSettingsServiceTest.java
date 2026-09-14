package com.example.insurancesystem.monitor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.insurancesystem.handler.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 企业保留特权设置测试。使用隔离 JDBC 验证非法请求不会触碰数据库，
 * 有效状态变更会落库并写审计，相同状态重复提交保持幂等。
 */
class MonitorEnterpriseSettingsServiceTest {
    private JdbcTemplate jdbc;
    private MonitorSystemLogService systemLog;
    private MonitorEnterpriseService service;

    /** 每个场景重建依赖，避免上一个企业的模拟状态影响当前断言。 */
    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        systemLog = mock(MonitorSystemLogService.class);
        service = new MonitorEnterpriseService(jdbc, mock(NamedParameterJdbcTemplate.class), systemLog);
    }

    /** 设置接口仅接受数字 0/1，拒绝客户端伪造其他企业字段或布尔状态。 */
    @Test
    void rejectsUnknownOrNonNumericSettingsBeforeDatabaseAccess() {
        assertThrows(BusinessException.class, () -> service.updateSettings(9L,
                Map.of("dataRetentionEnabled", true), 1L, "管理员"));
        assertThrows(BusinessException.class, () -> service.updateSettings(9L,
                Map.of("dataRetentionEnabled", 1, "name", "伪造名称"), 1L, "管理员"));
        verifyNoInteractions(jdbc, systemLog);
    }

    /** 授权状态由 0 改为 1 时，数据库更新和监控审计应记录同一企业及操作者。 */
    @Test
    void updatesRetentionAndWritesAudit() {
        when(jdbc.queryForList(anyString(), eq(9L))).thenReturn(List.of(Map.of(
                "id", 9L, "name", "测试企业", "dataRetentionEnabled", 0)));

        Map<String, Object> result = service.updateSettings(9L,
                Map.of("dataRetentionEnabled", 1), 1L, "管理员");

        assertEquals(1, result.get("dataRetentionEnabled"));
        verify(jdbc).update(contains("UPDATE tenant_enterprise SET data_retention_enabled"),
                eq(1), eq(1L), eq(9L));
        verify(systemLog).recordOperation(eq("ENTERPRISE_SETTINGS_UPDATE"), anyString(),
                anyString(), eq(1L), eq("管理员"), eq(9L), eq("测试企业"),
                eq("ENTERPRISE"), eq(9L), eq("测试企业"), isNull(), anyString(),
                eq(Map.of("dataRetentionEnabled", 0)), eq(Map.of("dataRetentionEnabled", 1)));
    }

    /** 重复提交当前状态不改变更新时间，也不制造无意义的审计日志。 */
    @Test
    void sameSettingDoesNotWriteAgain() {
        when(jdbc.queryForList(anyString(), eq(9L))).thenReturn(List.of(Map.of(
                "id", 9L, "name", "测试企业", "dataRetentionEnabled", 1)));

        assertEquals(1, service.updateSettings(9L,
                Map.of("dataRetentionEnabled", 1), 1L, "管理员").get("dataRetentionEnabled"));
        verify(jdbc, never()).update(anyString(), any(), any(), any());
        verifyNoInteractions(systemLog);
    }
}
