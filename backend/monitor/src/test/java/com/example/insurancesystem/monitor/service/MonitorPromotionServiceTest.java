package com.example.insurancesystem.monitor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 验证销售推广服务的关键业务边界。测试聚焦至少一种联系方式、官方模板结构、无条件全选忽略
 * 残留筛选以及 6000 条截断，不连接真实数据库或外部推广供应商。
 */
class MonitorPromotionServiceTest {
    private JdbcTemplate jdbc;
    private MonitorSystemLogService systemLog;
    private MonitorPromotionService service;

    /** 为每个用例创建隔离的 JDBC 与审计 mock，并固定推广上限为 6000。 */
    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        systemLog = mock(MonitorSystemLogService.class);
        service = new MonitorPromotionService(jdbc, new ObjectMapper(), systemLog, 6000);
    }

    /** 新增目标没有电话和邮箱时必须在访问数据库前拒绝。 */
    @Test
    void createRequiresAtLeastOneContact() {
        assertThrows(BusinessException.class, () -> service.create(
                Map.of("name", "无联系方式目标", "phone", "", "email", "", "status", 1), 1L, "管理员"));
    }

    /** 删除推广目标只执行软删除 SQL，不再向系统审计日志写入任何记录。 */
    @Test
    void deleteDoesNotWriteAuditLog() {
        when(jdbc.update(anyString(), eq(1L), eq(18L))).thenReturn(1);

        service.delete(18L, 1L);

        verify(jdbc).update(anyString(), eq(1L), eq(18L));
        verifyNoInteractions(systemLog);
    }

    /**
     * 下载模板必须仅包含页面开放的四个字段，并把整列默认格式设为文本。
     * 这个约束保证模板与导入解析器使用同一表头，同时避免电话与邮箱在 Excel 中被自动转换。
     */
    @Test
    void templateMatchesImporterContract() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.writeTemplate(response);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertEquals("推广信息", workbook.getSheetAt(0).getSheetName());
            assertEquals("名称*", workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("备注", workbook.getSheetAt(0).getRow(0).getCell(3).getStringCellValue());
            assertEquals("@", workbook.getSheetAt(0).getColumnStyle(1).getDataFormatString());
            assertEquals("@", workbook.getSheetAt(0).getColumnStyle(2).getDataFormatString());
            assertEquals(4, workbook.getSheetAt(0).getRow(0).getLastCellNum());
        }
    }

    /** 无条件全选应忽略页面残留关键词并在数据库计数超过限制时返回稳定截断信息。 */
    @Test
    void allSelectionIgnoresFiltersAndTruncatesAtLimit() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(6001L);
        Map<String, Object> result = service.preview(Map.of(
                "channel", "PHONE", "selectionMode", "ALL",
                "filters", Map.of("keyword", "不应生效", "sourceType", "EXCEL_IMPORT", "status", 0)));

        assertEquals(6000L, result.get("sendCount"));
        assertEquals(true, result.get("truncated"));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertTrue(!sql.getValue().contains("name LIKE") && !sql.getValue().contains("source_type"));
    }
}
