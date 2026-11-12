package com.example.insurancesystem.statistics;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

/**
 * 基于已完成承保记录计算企业每日盈利。每张工单分别合并 UPSTREAM 和 DOWNSTREAM 四类已落库费用，
 * 再按企业汇总；只认 finish_time 已落入目标日且主工单有效，费用从合并后的金额表读取的数据。
 */
@Component
public class SettledWorkorderProfitCalculator implements DailyProfitCalculator {
    private static final String PROFIT_SQL =
            "SELECT w.enterprise_id,COUNT(DISTINCT w.id) processed_count," +
            "COALESCE(SUM(q.upstream_total),0) upstream_income,COALESCE(SUM(q.downstream_total),0) downstream_cost " +
            "FROM biz_workorder w LEFT JOIN (SELECT enterprise_id,workorder_id,SUM(COALESCE(upstream_commercial_amount,0)+COALESCE(upstream_compulsory_amount,0)+COALESCE(upstream_vehicle_and_vessel_tax_amount,0)+COALESCE(upstream_non_motor_amount,0)) upstream_total," +
            "SUM(COALESCE(downstream_commercial_amount,0)+COALESCE(downstream_compulsory_amount,0)+COALESCE(downstream_vehicle_and_vessel_tax_amount,0)+COALESCE(downstream_non_motor_amount,0)) downstream_total FROM biz_workorder_quote WHERE deleted=0 GROUP BY enterprise_id,workorder_id) q " +
            "ON q.workorder_id=w.id AND q.enterprise_id=w.enterprise_id " +
            "WHERE w.deleted=0 AND w.finish_time>=? AND w.finish_time<? GROUP BY w.enterprise_id";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 注入 JDBC 聚合能力；计算只读业务表，不在该组件内写统计状态。
     */
    public SettledWorkorderProfitCalculator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<Long, ProfitSummary> calculate(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        Map<Long, ProfitSummary> result = new LinkedHashMap<>();
        RowCallbackHandler handler = rows -> result.put(rows.getLong("enterprise_id"), new ProfitSummary(
                rows.getLong("processed_count"),
                rows.getBigDecimal("upstream_income"),
                rows.getBigDecimal("downstream_cost")));
        jdbcTemplate.query(PROFIT_SQL, statement -> {
            statement.setTimestamp(1, Timestamp.valueOf(start));
            statement.setTimestamp(2, Timestamp.valueOf(end));
        }, handler);
        return result;
    }
}
