package com.example.insurancesystem.mapper;

import com.example.insurancesystem.domain.workorder.*;
import com.example.insurancesystem.statistics.SettledWorkorderProfitCalculator;
import com.example.insurancesystem.service.EnterpriseBusinessDataPurgeService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.ResultSet;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 在已执行增量迁移的独立 MySQL 测试库运行真实 Mapper、续保查询、利润及归档。
 * 只接受 localhost 和 insurance_saas_mergecheck_ 命名的测试库；所有本用例写入最终回滚。
 * 默认不连接任何数据库，由独立实例验证脚本显式传入 workorder.test.jdbc 后启用。
 */
@EnabledIfSystemProperty(named="workorder.test.jdbc", matches=".*insurance_saas_mergecheck_.*")
class WorkorderMergedSchemaIntegrationTest {
    /**
     * 校验实际新字段写读、报价不覆盖保费、取消险种清空、完成时间、统计和归档列一致。
     * 执行依赖迁移后的表结构，故能发现仅静态编译无法发现的旧表依赖与字段映射错误。
     */
    @Test
    void persistsReadsCalculatesAndArchivesMergedWorkorders() throws Exception {
        String url = System.getProperty("workorder.test.jdbc");
        assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:"));
        assertTrue(url.contains("/insurance_saas_mergecheck_"));
        UnpooledDataSource source = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, "root", "");
        Configuration configuration = new Configuration(new Environment("merged-schema-test", new JdbcTransactionFactory(), source));
        configuration.setMapUnderscoreToCamelCase(true);
        for (String resource : new String[]{"mapper/WorkorderAggregateMapper.xml", "mapper/WorkorderMapper.xml"}) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(input);
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession(false)) {
            var connection = session.getConnection();
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            long tenant = 989898L;
            long id;
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO biz_workorder(enterprise_id,code,status,deleted,created_at) VALUES(989898,'MERGED-INTEGRATION',8,0,NOW())", Statement.RETURN_GENERATED_KEYS);
                try (ResultSet keys = statement.getGeneratedKeys()) { assertTrue(keys.next()); id=keys.getLong(1); }
            }
            WorkorderAggregateMapper aggregate = session.getMapper(WorkorderAggregateMapper.class);
            assertEquals(8, aggregate.lockWorkorder(id, tenant).getStatus());
            Workorder update = new Workorder();
            update.setId(id); update.setEnterpriseId(tenant); update.setUpdateBy(1L);
            update.setCommercialInsurerName("商业公司"); update.setCommercialPolicyNo("C-TEST");
            update.setCommercialInsuranceStartTime(1793462400L); update.setCommercialInsuranceEndTime(1824998400L);
            update.setCompulsoryInsurerName("交强公司"); update.setCompulsoryPolicyNo("J-TEST");
            update.setCompulsoryInsuranceStartTime(1793462400L); update.setCompulsoryInsuranceEndTime(1824998400L);
            update.setNonMotorInsurerName("非车公司"); update.setNonMotorPolicyNo("N-TEST");
            update.setNonMotorInsuranceStartTime(1793462400L); update.setNonMotorInsuranceEndTime(1824998400L);
            update.setCommercialAmount(new BigDecimal("3000")); update.setCompulsoryAmount(new BigDecimal("950"));
            update.setNonMotorAmount(new BigDecimal("200")); update.setVehicleAndTaxAmount(new BigDecimal("360"));
            update.setUpstreamCommercialAmount(new BigDecimal("12.34")); update.setDownstreamCommercialAmount(new BigDecimal("5.67"));
            update.setUpstreamComputeType(0); update.setDownstreamComputeType(1);
            update.setPayName("收款姓名"); update.setPayIdNum("13800000000");
            update.setFinishTime(1793462400L); update.setStatus(10);
            aggregate.replacePolicies(update); aggregate.insertQuote(update);
            aggregate.replaceFinancials(update); aggregate.updateMainStage(update);
            assertEquals(1793462400L, aggregate.lockWorkorder(id,tenant).getFinishTime());

            Workorder quote = new Workorder(); quote.setId(id); quote.setEnterpriseId(tenant); quote.setQuotationRemark("报价备注");
            aggregate.updateQuote(quote);
            assertEquals(1, aggregate.countQuote(quote));
            WorkorderSearchDTO search = new WorkorderSearchDTO(); search.setId(id);
            WorkorderMapper orders = session.getMapper(WorkorderMapper.class);
            WorkorderDTO detail = orders.selectByWorkorderSearchDTO(search).get(0);
            detail.computePolicyTotals();
            assertEquals("商业公司", detail.getCommercialInsurerName());
            assertEquals("N-TEST", detail.getNonMotorPolicyNo());
            assertEquals("收款姓名", detail.getPayName());
            assertEquals(new BigDecimal("4150.00"), detail.getTotalPremiumAmount());
            assertEquals(new BigDecimal("4510.00"), detail.getPayableAmount());
            assertEquals(1793462400L, detail.getNonMotorInsuranceStartTime());
            assertDoesNotThrow(() -> orders.selectRenewCount(null));
            var profit = new SettledWorkorderProfitCalculator(jdbc).calculate(LocalDate.of(2026,11,1)).get(tenant);
            assertNotNull(profit); assertEquals(1, profit.getProcessedWorkorderCount());
            assertEquals(new BigDecimal("6.67"), profit.getProfitAmount());

            update.setNonMotorPolicyNo(null); update.setNonMotorInsurerName(null); update.setNonMotorAmount(null);
            update.setNonMotorInsuranceStartTime(null); update.setNonMotorInsuranceEndTime(null);
            aggregate.replacePolicies(update); aggregate.replaceFinancials(update);
            session.clearCache();
            detail=orders.selectByWorkorderSearchDTO(search).get(0);
            assertNull(detail.getNonMotorPolicyNo()); assertNull(detail.getNonMotorAmount());
            assertEquals("C-TEST", detail.getCommercialPolicyNo());

            /*
             * 直接验证实际企业归档方法，只迁移本测试企业；不调用 OSS 删除或真正的保留期任务。
             * 新保单列、收款和金额应全部进入对应归档表，随后由外层会话回滚测试数据。
             */
            EnterpriseBusinessDataPurgeService purge = new EnterpriseBusinessDataPurgeService(jdbc, null);
            assertEquals(1, (Integer) ReflectionTestUtils.invokeMethod(purge, "archiveEnterpriseRows", "biz_workorder_quote", tenant));
            assertEquals(1, (Integer) ReflectionTestUtils.invokeMethod(purge, "archiveEnterpriseRows", "biz_workorder", tenant));
            assertEquals("商业公司", jdbc.queryForObject("SELECT commercial_insurer_name FROM biz_workorder_archive WHERE id=?", String.class, id));
            assertEquals("收款姓名", jdbc.queryForObject("SELECT payee_name FROM biz_workorder_archive WHERE id=?", String.class, id));
            assertEquals(new BigDecimal("12.34"), jdbc.queryForObject("SELECT upstream_commercial_amount FROM biz_workorder_quote_archive WHERE workorder_id=?", BigDecimal.class, id));
            session.rollback();
        }
    }
}

