package com.example.insurancesystem.statistics;

import com.example.insurancesystem.statistics.UsageMetricRecorder;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 编排企业自然日统计终算：读取 Redis 高频指标、计算业务指标并以绝对值覆盖每日宽表。
 * 整批写入处于同一事务，只有数据库提交成功后才缩短源 Redis 日桶生命周期。
 */
@Service
public class EnterpriseDailyStatisticsService {
    private static final String CUSTOMER_SQL =
            "SELECT m.enterprise_id,COUNT(DISTINCT m.id) customer_count FROM biz_merchant m " +
            "JOIN biz_merchant_category c ON c.id=m.category_id AND c.deleted=0 AND c.status=1 " +
            "WHERE m.deleted=0 AND c.direction='DOWNSTREAM' AND m.created_at>=? AND m.created_at<? GROUP BY m.enterprise_id";
    private static final String UPSERT_SQL =
            "INSERT INTO monitor_enterprise_daily_usage(stat_date,enterprise_id,processed_workorder_count," +
            "request_count,ocr_count,new_customer_count,upstream_income,downstream_cost,profit_amount," +
            "is_finalized,calculated_at,last_flushed_at,finalized_at) VALUES(?,?,?,?,?,?,?,?,?,1,NOW(),NOW(),NOW()) " +
            "ON DUPLICATE KEY UPDATE processed_workorder_count=VALUES(processed_workorder_count)," +
            "request_count=VALUES(request_count),ocr_count=VALUES(ocr_count)," +
            "new_customer_count=VALUES(new_customer_count),upstream_income=VALUES(upstream_income)," +
            "downstream_cost=VALUES(downstream_cost),profit_amount=VALUES(profit_amount)," +
            "is_finalized=1,calculated_at=NOW(),last_flushed_at=NOW(),finalized_at=NOW()";

    private final JdbcTemplate jdbcTemplate;
    private final UsageMetricRecorder metricRecorder;
    private final DailyProfitCalculator profitCalculator;

    /**
     * 注入统计数据源、实时计数器和可替换的盈利计算器，明确日终任务只负责流程编排。
     */
    public EnterpriseDailyStatisticsService(JdbcTemplate jdbcTemplate, UsageMetricRecorder metricRecorder,
            DailyProfitCalculator profitCalculator) {
        this.jdbcTemplate = jdbcTemplate;
        this.metricRecorder = metricRecorder;
        this.profitCalculator = profitCalculator;
    }

    /**
     * 终算指定已结束自然日。已有统计行也加入企业集合，使历史重算时被删除的业务数据能够覆盖回零；
     * Redis 读取失败会中断事务，避免把真实访问量误写为零。
     *
     * @param date 已结束的北京时间自然日
     */
    @Transactional
    public void finalizeDay(LocalDate date) {
        Map<Long, Long> apiCounts = metricRecorder.readApiCounts(date);
        Map<Long, Long> ocrCounts = metricRecorder.readOcrCounts(date);
        Map<Long, Long> customerCounts = queryCustomerCounts(date);
        Map<Long, ProfitSummary> profits = profitCalculator.calculate(date);

        Set<Long> enterpriseIds = new HashSet<>();
        enterpriseIds.addAll(apiCounts.keySet());
        enterpriseIds.addAll(ocrCounts.keySet());
        enterpriseIds.addAll(customerCounts.keySet());
        enterpriseIds.addAll(profits.keySet());
        /*
         * 每个已结束自然日都为所有有效企业保留一行，即使当天全部指标为零；
         * 这样区间聚合能区分“真实零值”和“维护任务尚未产出数据”。
         */
        enterpriseIds.addAll(jdbcTemplate.queryForList(
                "SELECT id FROM tenant_enterprise WHERE deleted=0", Long.class));
        enterpriseIds.addAll(jdbcTemplate.queryForList(
                "SELECT enterprise_id FROM monitor_enterprise_daily_usage WHERE stat_date=?", Long.class, Date.valueOf(date)));

        jdbcTemplate.batchUpdate(UPSERT_SQL, enterpriseIds, 200, (statement, enterpriseId) -> {
            ProfitSummary profit = profits.getOrDefault(enterpriseId,
                    new ProfitSummary(0L, BigDecimal.ZERO, BigDecimal.ZERO));
            statement.setDate(1, Date.valueOf(date));
            statement.setLong(2, enterpriseId);
            statement.setLong(3, profit.getProcessedWorkorderCount());
            statement.setLong(4, apiCounts.getOrDefault(enterpriseId, 0L));
            statement.setLong(5, ocrCounts.getOrDefault(enterpriseId, 0L));
            statement.setLong(6, customerCounts.getOrDefault(enterpriseId, 0L));
            statement.setBigDecimal(7, profit.getUpstreamIncome());
            statement.setBigDecimal(8, profit.getDownstreamCost());
            statement.setBigDecimal(9, profit.getProfitAmount());
        });

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                metricRecorder.retainArchivedDay(date);
            }
        });
    }

    /**
     * 统计目标日新建的有效下游商户。分类方向决定客户身份，避免把上游保险机构计入客户数量。
     */
    private Map<Long, Long> queryCustomerCounts(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        Map<Long, Long> result = new HashMap<>();
        RowCallbackHandler handler = rows -> result.put(
                rows.getLong("enterprise_id"), rows.getLong("customer_count"));
        jdbcTemplate.query(CUSTOMER_SQL, statement -> {
            statement.setTimestamp(1, Timestamp.valueOf(start));
            statement.setTimestamp(2, Timestamp.valueOf(end));
        }, handler);
        return result;
    }
}
