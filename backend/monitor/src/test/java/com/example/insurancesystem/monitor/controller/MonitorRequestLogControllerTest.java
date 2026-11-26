package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.monitor.integration.SaasLogDirectoryClient;
import com.example.insurancesystem.handler.exception.BusinessException;
import java.util.*;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** 验证筛选区间、秒级边界和数据归属，监控仅访问自身日志表。 */
class MonitorRequestLogControllerTest {
    /** 结束秒转为下一秒开区间，分类/URI/耗时只作用于日志表。 */
    @Test void filtersSecondsClassesLiteralUriAndDuration() {
        NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),any(SqlParameterSource.class),eq(Long.class))).thenReturn(0L);
        when(jdbc.queryForList(anyString(),any(SqlParameterSource.class))).thenReturn(List.of());
        MonitorRequestLogController controller=new MonitorRequestLogController(jdbc,mock(SaasLogDirectoryClient.class));
        controller.page(Map.of("startTime","2026-11-20 12:00:00","endTime","2026-11-20 12:00:01","httpStatusClass","400","businessCodeClass","500","uri","a_%","minDurationMs","0","maxDurationMs","150"));
        ArgumentCaptor<String> sql=ArgumentCaptor.forClass(String.class);ArgumentCaptor<SqlParameterSource> args=ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForList(sql.capture(),args.capture());
        assertFalse(sql.getValue().contains("tenant_"));assertTrue(sql.getValue().contains("duration_ms<=:durationMax"));
        assertEquals(Timestamp.valueOf("2026-11-20 12:00:02"),args.getValue().getValue("end"));
        assertEquals(400,args.getValue().getValue("httpMin"));assertEquals("^5[0-9][0-9]$",args.getValue().getValue("businessPattern"));assertEquals("%a!_!%%",args.getValue().getValue("uri"));
    }
    /** 错误范围、无企业用户和非法分类在查库前返回参数错误。 */
    @Test void rejectsInvalidRangesAndUserWithoutEnterprise() {
        NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
        MonitorRequestLogController controller=new MonitorRequestLogController(jdbc,mock(SaasLogDirectoryClient.class));
        for(Map<String,String> q:List.of(Map.of("minDurationMs","-1"),Map.of("minDurationMs","100","maxDurationMs","99"),Map.of("userId","2"),Map.of("httpStatusClass","404"),Map.of("startTime","2026-11-20 13:00:00","endTime","2026-11-20 12:00:00")))assertThrows(BusinessException.class,()->controller.page(q));
        verifyNoInteractions(jdbc);
    }
    /** 企业与用户候选必须委托SaaS客户端，不通过监控JDBC查询。 */
    @Test void delegatesDirectoryToSaas() {
        NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);SaasLogDirectoryClient client=mock(SaasLogDirectoryClient.class);
        MonitorRequestLogController controller=new MonitorRequestLogController(jdbc,client);
        controller.enterprises("企业");controller.users(1L,"mail");
        verify(client).options("enterprises",null,"企业");verify(client).options("users",1L,"mail");verifyNoInteractions(jdbc);
    }
    /** 清除日期时不添加时间条件，避免后端隐式回退当天。 */
    @Test void clearedTimeDoesNotRestrictDates() {
        NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),any(SqlParameterSource.class),eq(Long.class))).thenReturn(0L);
        when(jdbc.queryForList(anyString(),any(SqlParameterSource.class))).thenReturn(List.of());
        new MonitorRequestLogController(jdbc,mock(SaasLogDirectoryClient.class)).page(Map.of("startTime","","endTime",""));
        ArgumentCaptor<String> sql=ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(),any(SqlParameterSource.class));
        assertFalse(sql.getValue().contains("started_at>=:start"));assertFalse(sql.getValue().contains("started_at<:end"));
    }
    /** 精确HTTP值与分类取交集，租户用户不得匹配监控账号。 */
    @Test void exactHttpAndTenantIdentityAreApplied() {
        NamedParameterJdbcTemplate jdbc=mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),any(SqlParameterSource.class),eq(Long.class))).thenReturn(0L);
        when(jdbc.queryForList(anyString(),any(SqlParameterSource.class))).thenReturn(List.of());
        new MonitorRequestLogController(jdbc,mock(SaasLogDirectoryClient.class)).page(Map.of("httpStatus","404","httpStatusClass","400","enterpriseId","1","userId","1"));
        ArgumentCaptor<String> sql=ArgumentCaptor.forClass(String.class);ArgumentCaptor<SqlParameterSource> args=ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForList(sql.capture(),args.capture());assertTrue(sql.getValue().contains("http_status=:httpExact"));
        assertTrue(sql.getValue().contains("identity_type='TENANT_USER'"));assertEquals(404,args.getValue().getValue("httpExact"));
    }
}
