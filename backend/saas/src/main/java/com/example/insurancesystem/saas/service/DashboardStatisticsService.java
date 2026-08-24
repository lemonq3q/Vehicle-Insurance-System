package com.example.insurancesystem.saas.service;

import com.example.insurancesystem.saas.support.PortalContextService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 为企业门户仪表盘聚合本月、上月经营指标及一周内续保提醒。月度指标只读取企业每日统计宽表，
 * 单企业两个月最多约六十二行，因此当前直接使用索引查询，不引入额外 Redis 缓存和一致性成本。
 */
@Service
public class DashboardStatisticsService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String MONTHLY_SQL =
            "SELECT " +
            "COALESCE(SUM(CASE WHEN stat_date>=? AND stat_date<? THEN processed_workorder_count ELSE 0 END),0) current_workorders," +
            "COALESCE(SUM(CASE WHEN stat_date>=? AND stat_date<? THEN processed_workorder_count ELSE 0 END),0) previous_workorders," +
            "COALESCE(SUM(CASE WHEN stat_date>=? AND stat_date<? THEN new_customer_count ELSE 0 END),0) current_customers," +
            "COALESCE(SUM(CASE WHEN stat_date>=? AND stat_date<? THEN new_customer_count ELSE 0 END),0) previous_customers," +
            "COALESCE(SUM(CASE WHEN stat_date>=? AND stat_date<? THEN profit_amount ELSE 0 END),0) current_profit," +
            "COALESCE(SUM(CASE WHEN stat_date>=? AND stat_date<? THEN profit_amount ELSE 0 END),0) previous_profit " +
            "FROM monitor_enterprise_daily_usage WHERE enterprise_id=? AND stat_date>=? AND stat_date<? AND is_finalized=1";
    private static final String RENEWAL_SQL =
            "SELECT COUNT(DISTINCT w.id) FROM biz_workorder w WHERE w.enterprise_id=? AND w.deleted=0 " +
            "AND w.renewal_reminder_disabled=0 AND DATEDIFF(CURDATE(),DATE(w.created_at))>=0 " +
            "AND (GREATEST(CEIL(DATEDIFF(CURDATE(),DATE(w.created_at))/365),1)*365-" +
            "DATEDIFF(CURDATE(),DATE(w.created_at))) BETWEEN 0 AND ?";

    private final JdbcTemplate jdbcTemplate;
    private final PortalContextService context;

    /**
     * 注入当前企业上下文和 JDBC 查询能力，所有条件固定包含 enterprise_id 以维持租户隔离。
     */
    public DashboardStatisticsService(JdbcTemplate jdbcTemplate, PortalContextService context) {
        this.jdbcTemplate = jdbcTemplate;
        this.context = context;
    }

    /**
     * 返回当前企业经营概览。本月数据累计到最近已结束自然日；上月使用完整自然月，
     * 环比在上月为零时返回 null 并标记 NEW，避免用无穷百分比误导用户。
     */
    public Map<String, Object> currentEnterpriseDashboard() {
        Long enterpriseId = context.enterpriseId();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate currentStart = today.withDayOfMonth(1);
        LocalDate nextStart = currentStart.plusMonths(1);
        LocalDate previousStart = currentStart.minusMonths(1);

        Map<String, Object> monthly = jdbcTemplate.queryForMap(MONTHLY_SQL,
                Date.valueOf(currentStart), Date.valueOf(nextStart),
                Date.valueOf(previousStart), Date.valueOf(currentStart),
                Date.valueOf(currentStart), Date.valueOf(nextStart),
                Date.valueOf(previousStart), Date.valueOf(currentStart),
                Date.valueOf(currentStart), Date.valueOf(nextStart),
                Date.valueOf(previousStart), Date.valueOf(currentStart),
                enterpriseId, Date.valueOf(previousStart), Date.valueOf(nextStart));
        Long renewalCount = jdbcTemplate.queryForObject(RENEWAL_SQL, Long.class, enterpriseId, 30);
        Long renewalDueThisWeek = jdbcTemplate.queryForObject(RENEWAL_SQL, Long.class, enterpriseId, 7);

        long currentWorkorders = number(monthly.get("current_workorders"));
        long previousWorkorders = number(monthly.get("previous_workorders"));
        long currentCustomers = number(monthly.get("current_customers"));
        long previousCustomers = number(monthly.get("previous_customers"));
        BigDecimal currentProfit = money(monthly.get("current_profit"));
        BigDecimal previousProfit = money(monthly.get("previous_profit"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentMonthProcessedWorkorders", currentWorkorders);
        result.put("previousMonthProcessedWorkorders", previousWorkorders);
        result.put("workorderComparison", comparison(BigDecimal.valueOf(currentWorkorders), BigDecimal.valueOf(previousWorkorders)));
        result.put("renewalReminderCount", renewalCount == null ? 0L : renewalCount);
        result.put("renewalDueThisWeek", renewalDueThisWeek == null ? 0L : renewalDueThisWeek);
        result.put("currentMonthNewCustomers", currentCustomers);
        result.put("previousMonthNewCustomers", previousCustomers);
        result.put("customerComparison", comparison(BigDecimal.valueOf(currentCustomers), BigDecimal.valueOf(previousCustomers)));
        result.put("currentMonthProfit", currentProfit);
        result.put("previousMonthProfit", previousProfit);
        result.put("profitComparison", comparison(currentProfit, previousProfit));
        result.put("updatedThrough", today.minusDays(1).toString());
        return result;
    }

    /**
     * 生成带方向和绝对百分比的环比对象。下降使用 DOWN、增长使用 UP、相同使用 FLAT；
     * 上期为零且本期非零时使用 NEW 并省略不可定义的百分比。
     */
    private Map<String, Object> comparison(BigDecimal current, BigDecimal previous) {
        Map<String, Object> result = new LinkedHashMap<>();
        int change = current.compareTo(previous);
        if (previous.signum() == 0) {
            result.put("direction", current.signum() == 0 ? "FLAT" : "NEW");
            result.put("rate", current.signum() == 0 ? BigDecimal.ZERO : null);
            return result;
        }
        result.put("direction", change > 0 ? "UP" : change < 0 ? "DOWN" : "FLAT");
        result.put("rate", current.subtract(previous).abs().multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 1, RoundingMode.HALF_UP));
        return result;
    }

    private long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private BigDecimal money(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }
}
