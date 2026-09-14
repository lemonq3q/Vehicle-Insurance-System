package com.example.insurancesystem.monitor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.insurancesystem.handler.exception.BusinessException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 企业财务服务测试。该测试覆盖监控财务汇总的真实表查询，以及人工调账的余额安全边界、
 * 钱包与流水原子写入和审计协作；数据库依赖使用 mock，避免测试修改真实企业资金。
 */
class MonitorEnterpriseFinanceServiceTest {
    private JdbcTemplate jdbc;
    private MonitorSystemLogService systemLog;
    private MonitorEnterpriseService service;

    /**
     * 每个用例创建隔离的 JDBC、命名参数 JDBC 和审计服务，使金额边界与写入次数不会互相污染。
     */
    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        systemLog = mock(MonitorSystemLogService.class);
        service = new MonitorEnterpriseService(jdbc, mock(NamedParameterJdbcTemplate.class), systemLog);
    }

    /**
     * 财务汇总应先确认企业存在，再原样返回真实聚合 SQL 的四项结果，避免重新使用前端模拟数据。
     */
    @Test
    void financeSummaryReturnsDatabaseAggregation() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(9L))).thenReturn(1);
        Map<String, Object> expected = Map.of(
                "balance", new BigDecimal("120.00"),
                "totalRecharge", new BigDecimal("500.00"),
                "totalSubscriptionExpense", new BigDecimal("299.00"),
                "monthTransactionCount", 4L);
        when(jdbc.queryForList(anyString(), eq(9L), eq(9L), eq(9L), eq(9L),
                any(Timestamp.class), any(Timestamp.class)))
                .thenReturn(List.of(expected));

        assertEquals(expected, service.financeSummary(9L));
    }

    /**
     * 调减金额大于可用余额时必须在任何钱包更新、流水插入和审计写入前失败，保护钱包不产生负余额。
     */
    @Test
    void adjustBalanceRejectsNegativeResultBeforeWrites() {
        when(jdbc.queryForList(anyString(), eq(9L)))
                .thenReturn(List.of(Map.of("id", 9L, "name", "测试企业")))
                .thenReturn(List.of(Map.of("id", 3L, "balance", new BigDecimal("20.00"))));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.adjustBalance(9L, Map.of("amount", -20.01, "reason", "纠正错误入账"),
                        1L, "平台管理员"));

        assertEquals("调整后余额不能小于零", exception.getMsg());
        verify(jdbc, never()).update(anyString(), any(), any(), any());
        verifyNoInteractions(systemLog);
    }

    /**
     * 成功调增应先更新钱包再插入 IN/ADJUST 流水，并把新余额和流水编号交给监控审计日志保存。
     */
    @Test
    void adjustBalanceWritesWalletLedgerAndAudit() {
        when(jdbc.queryForList(anyString(), eq(9L)))
                .thenReturn(List.of(Map.of("id", 9L, "name", "测试企业")))
                .thenReturn(List.of(Map.of("id", 3L, "balance", new BigDecimal("20.00"))));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        Map<String, Object> result = service.adjustBalance(9L,
                Map.of("amount", 10, "reason", "线下到账补录"), 1L, "平台管理员");

        assertEquals(new BigDecimal("30.00"), result.get("balance"));
        verify(jdbc).update(contains("UPDATE saas_wallet SET balance_amount"),
                eq(new BigDecimal("30.00")), eq(1L), eq(3L));
        verify(jdbc).update(contains("INSERT INTO saas_wallet_transaction"),
                eq(9L), eq(3L), eq(1L), anyString(), eq("IN"), eq(new BigDecimal("10.00")),
                eq(new BigDecimal("20.00")), eq(new BigDecimal("30.00")), eq("平台人工调账：线下到账补录"));
        verify(systemLog).recordOperation(eq("BALANCE_ADJUST"), eq("人工调整企业余额"), eq("enterprise"),
                eq(1L), eq("平台管理员"), eq(9L), eq("测试企业"), eq("WALLET"), eq(3L),
                eq("测试企业钱包"), eq("线下到账补录"), anyString(), any(), any());
    }
}
