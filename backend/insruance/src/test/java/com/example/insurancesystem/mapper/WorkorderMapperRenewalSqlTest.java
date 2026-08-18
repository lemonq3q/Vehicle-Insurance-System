package com.example.insurancesystem.mapper;

import com.example.insurancesystem.domain.workorder.WorkorderSearchDTO;
import java.io.InputStream;
import java.util.Locale;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证续保查询 Mapper 的周期边界契约。
 *
 * <p>该测试直接解析生产环境使用的 WorkorderMapper.xml，并检查最终生成 SQL，主要防止续保周期再次
 * 被错误实现成“整整 365 天的提醒窗口”或“只在周期当天提醒”。测试不依赖数据库数据，适用于持续集成。
 */
class WorkorderMapperRenewalSqlTest {

    /**
     * 确认续保列表 SQL同时包含 365 天周期计算和周期结束前 30 天的窗口条件。
     *
     * <p>周期与提前天数必须是两个独立参数；生成结果通过 CEIL 定位当前周期截止点，再判断剩余天数
     * 是否位于 0—30 天，不得退化为创建日直接取模的单日提醒。
     */
    @Test
    void renewalQueryRequiresCompletedCycleAndExactCycleBoundary() {
        Configuration configuration = new Configuration();
        String resource = "mapper/WorkorderMapper.xml";
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            assertTrue(input != null, "应能读取生产 WorkorderMapper.xml");
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        } catch (Exception exception) {
            throw new AssertionError("WorkorderMapper.xml 解析失败", exception);
        }

        WorkorderSearchDTO params = new WorkorderSearchDTO();
        params.setRenewalCycleDays(365);
        params.setRenewalAdvanceDays(30);
        BoundSql boundSql = configuration
                .getMappedStatement("com.example.insurancesystem.mapper.WorkorderMapper.selectRenewByWorkorderSearchDTO")
                .getBoundSql(params);
        String normalizedSql = boundSql.getSql().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);

        assertTrue(normalizedSql.contains("DATEDIFF(CURDATE(), DATE(W.CREATED_AT)) >= 0"));
        assertTrue(normalizedSql.contains("CEIL(DATEDIFF(CURDATE(), DATE(W.CREATED_AT)) / ?)"));
        assertTrue(normalizedSql.contains(") BETWEEN 0 AND ?"));
        assertFalse(normalizedSql.contains("MOD(DATEDIFF"));
        assertFalse(normalizedSql.contains("BETWEEN DATE_SUB"));
    }
}
