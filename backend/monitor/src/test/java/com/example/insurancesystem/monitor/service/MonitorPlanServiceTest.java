package com.example.insurancesystem.monitor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.insurancesystem.handler.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 套餐配置服务的单元测试。重点覆盖列表单次查询和新增套餐的关键额度边界，确保工单额度允许为零、
 * 不允许负数，同时在无效请求阶段不会提前产生数据库写入或审计日志。
 */
class MonitorPlanServiceTest {
    private JdbcTemplate jdbc;
    private MonitorPlanService service;

    /** 每个用例使用独立依赖，避免查询和校验测试互相污染调用记录。 */
    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new MonitorPlanService(jdbc, mock(MonitorSystemLogService.class));
    }

    /** 列表接口应通过一条按排序值排列的 SQL 返回完整套餐集合。 */
    @Test
    void listUsesOneOrderedQuery() {
        List<Map<String, Object>> expected = List.of(Map.of("id", 1L, "workorderLimit", 1000));
        String sql = "SELECT id,code,name,description,billing_period billingCycle,duration_days durationDays," +
                "user_limit memberLimit,workorder_limit workorderLimit,price,original_price listPrice,status," +
                "sort_no sortOrder,updated_at updatedAt FROM saas_plan WHERE deleted=0 ORDER BY sort_no,id";
        when(jdbc.queryForList(sql)).thenReturn(expected);

        assertEquals(expected, service.list());
        verify(jdbc).queryForList(sql);
    }

    /** 免费工单存储额度不能为负数，服务应在任何数据库读取或写入前拒绝该配置。 */
    @Test
    void createRejectsNegativeWorkorderLimit() {
        Map<String, Object> body = validBody();
        body.put("workorderLimit", -1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(body, 1L, "管理员"));

        assertEquals("workorderLimit参数无效", exception.getMsg());
    }

    /** 生成一份普通正式套餐请求，测试数据不包含体验版或试用期语义。 */
    private Map<String, Object> validBody() {
        return new java.util.HashMap<>(Map.ofEntries(
                Map.entry("code", "STANDARD_YEAR"), Map.entry("name", "标准版"),
                Map.entry("description", "正式套餐"), Map.entry("billingCycle", "YEAR"),
                Map.entry("durationDays", 365), Map.entry("memberLimit", 10),
                Map.entry("workorderLimit", 1000), Map.entry("price", 3600),
                Map.entry("listPrice", 4200), Map.entry("status", 1),
                Map.entry("sortOrder", 10), Map.entry("reason", "新增正式套餐")));
    }
}
