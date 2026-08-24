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
 * 再按企业汇总；只认 finish_time 已落入目标日且主工单、承保记录均有效的数据。
 */
@Component
public class SettledWorkorderProfitCalculator implements DailyProfitCalculator {
    private static final String PROFIT_SQL =
            "SELECT w.enterprise_id,COUNT(DISTINCT w.id) processed_count," +
            "COALESCE(SUM(COALESCE(upc.total_amount,0)),0) upstream_income," +
            "COALESCE(SUM(COALESCE(downc.total_amount,0)),0) downstream_cost " +
            "FROM biz_workorder w JOIN biz_workorder_underwriting uw " +
            "ON uw.workorder_id=w.id AND uw.enterprise_id=w.enterprise_id AND uw.deleted=0 " +
            "LEFT JOIN (SELECT enterprise_id,workorder_id,SUM(COALESCE(commercial_amount,0)+" +
            "COALESCE(compulsory_amount,0)+COALESCE(vehicle_tax_amount,0)+COALESCE(non_motor_amount,0)) total_amount " +
            "FROM biz_workorder_commission WHERE deleted=0 AND side='UPSTREAM' GROUP BY enterprise_id,workorder_id) upc " +
            "ON upc.enterprise_id=w.enterprise_id AND upc.workorder_id=w.id " +
            "LEFT JOIN (SELECT enterprise_id,workorder_id,SUM(COALESCE(commercial_amount,0)+" +
            "COALESCE(compulsory_amount,0)+COALESCE(vehicle_tax_amount,0)+COALESCE(non_motor_amount,0)) total_amount " +
            "FROM biz_workorder_commission WHERE deleted=0 AND side='DOWNSTREAM' GROUP BY enterprise_id,workorder_id) downc " +
            "ON downc.enterprise_id=w.enterprise_id AND downc.workorder_id=w.id " +
            "WHERE w.deleted=0 AND uw.finish_time>=? AND uw.finish_time<? GROUP BY w.enterprise_id";

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
