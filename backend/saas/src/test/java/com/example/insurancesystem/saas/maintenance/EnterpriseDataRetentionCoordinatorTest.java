package com.example.insurancesystem.saas.maintenance;

import com.example.insurancesystem.saas.config.EnterpriseDataRetentionProperties;
import com.example.insurancesystem.saas.integration.client.InsuranceEnterpriseDataClient;
import com.example.insurancesystem.saas.mapper.EnterpriseDataRetentionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 SaaS 资料保留维护任务的阈值计算、逐企业调用和局部失败隔离。
 * 车险数据实际归档由内部服务负责，本测试只约束跨服务编排不会遗漏后续企业。
 */
class EnterpriseDataRetentionCoordinatorTest {
    private EnterpriseDataRetentionMapper mapper;
    private InsuranceEnterpriseDataClient client;
    private EnterpriseDataRetentionCoordinator coordinator;

    /**
     * 使用九十天配置和可控依赖建立待测协调器。
     */
    @BeforeEach
    void setUp() {
        mapper = mock(EnterpriseDataRetentionMapper.class);
        client = mock(InsuranceEnterpriseDataClient.class);
        EnterpriseDataRetentionProperties properties = new EnterpriseDataRetentionProperties();
        properties.setRetentionDays(90);
        coordinator = new EnterpriseDataRetentionCoordinator(mapper, client, properties);
    }

    /**
     * 维护任务应以当前时间前九十天作为截止点，并依次调用查询返回的全部企业。
     */
    @Test
    void purgesAllEnterprisesPastNinetyDayRetention() {
        when(mapper.findEnterpriseIdsPastRetention(any())).thenReturn(List.of(11L, 12L));
        LocalDateTime expected = LocalDateTime.now().minusDays(90);

        coordinator.purgeExpiredEnterpriseData();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).findEnterpriseIdsPastRetention(cutoff.capture());
        assertTrue(Math.abs(java.time.Duration.between(expected, cutoff.getValue()).toSeconds()) <= 1);
        verify(client).purgeEnterprise(11L);
        verify(client).purgeEnterprise(12L);
    }

    /**
     * 单个企业调用失败时仍应继续清理剩余企业，批次完成后再抛出汇总异常供 C 标记任务失败。
     */
    @Test
    void continuesAfterOneEnterpriseFailsAndReportsBatchFailure() {
        when(mapper.findEnterpriseIdsPastRetention(any())).thenReturn(List.of(21L, 22L));
        doThrow(new IllegalStateException("insurance unavailable"))
                .when(client).purgeEnterprise(21L);

        assertThrows(IllegalStateException.class, coordinator::purgeExpiredEnterpriseData);
        verify(client).purgeEnterprise(22L);
    }
}
