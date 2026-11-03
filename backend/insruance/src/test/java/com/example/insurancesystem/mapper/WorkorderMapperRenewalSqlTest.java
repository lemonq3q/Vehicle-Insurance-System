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
 * 解析实际Mapper，验证双起期日历周期和去重提醒条件能生成合法SQL，不再依赖创建日固定天数。
 */
class WorkorderMapperRenewalSqlTest {
    /** 列表和计数共用周期数据源，处理范围必须包含商业与交强两种持久化状态。 */
    @Test
    void renewalQueryUsesInsuranceStartDatesAndIndependentCycles() {
        Configuration configuration = new Configuration();
        String resource = "mapper/WorkorderMapper.xml";
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            assertTrue(input != null);
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        } catch (Exception exception) { throw new AssertionError(exception); }
        WorkorderSearchDTO params = new WorkorderSearchDTO();
        params.setRemindStatus(0);
        String sql = configuration.getMappedStatement("com.example.insurancesystem.mapper.WorkorderMapper.selectRenewByWorkorderSearchDTO")
                .getBoundSql(params).getSql().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        assertTrue(sql.contains("COMMERCIAL_INSURANCE_START_TIME"));
        assertTrue(sql.contains("COMPULSORY_INSURANCE_START_TIME"));
        assertTrue(sql.contains("TIMESTAMPADD(MONTH"));
        assertTrue(sql.contains("COMMERCIAL_RENEWAL_CYCLE"));
        assertTrue(sql.contains("COMPULSORY_RENEWAL_CYCLE"));
        assertFalse(sql.contains("DATEDIFF"));
        assertFalse(sql.contains("CEIL("));
        String count = configuration.getMappedStatement("com.example.insurancesystem.mapper.WorkorderMapper.selectRenewCount")
                .getBoundSql(java.util.Collections.singletonMap("createBy", null)).getSql().toUpperCase(Locale.ROOT);
        assertTrue(count.contains("COUNT(DISTINCT W.ID)"));
        assertTrue(count.contains("COMMERCIAL_RENEWAL_CYCLE"));
        assertTrue(count.contains("COMPULSORY_RENEWAL_CYCLE"));
        /* 租户拦截器通过同一SQL解析器补充企业条件，验证长周期表达式可被其解析。 */
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(count));
    }
}
