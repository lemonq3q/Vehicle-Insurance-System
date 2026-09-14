package com.example.insurancesystem.monitor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.insurancesystem.handler.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** 验证统一日志查询的白名单、日期边界和空结果分页行为。 */
class MonitorSystemLogQueryServiceTest {
    @Mock private NamedParameterJdbcTemplate jdbc;
    private MonitorSystemLogQueryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new MonitorSystemLogQueryService(jdbc);
    }

    /** 不允许任意类别字符串进入查询，避免前后端枚举语义失配。 */
    @Test
    void rejectsUnknownCategory() {
        assertThrows(BusinessException.class, () -> service.page("UNKNOWN", null, null, null, 1, 10));
    }

    /** 开始日晚于结束日时应在访问数据库前拒绝请求。 */
    @Test
    void rejectsReversedDateRange() {
        assertThrows(BusinessException.class,
                () -> service.page("OPERATION", "INFO", "2026-08-27", "2026-08-26", 1, 10));
    }

    /** 无结果时只执行计数 SQL，不再发起没有意义的分页列表查询。 */
    @Test
    void skipsListQueryWhenCountIsZero() {
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        Map<String, Object> result = service.page("OPERATION", "INFO", "2026-08-01", "2026-08-26", 1, 10);
        assertEquals(0L, result.get("total"));
        assertEquals(List.of(), result.get("list"));
        verify(jdbc).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    }
}
