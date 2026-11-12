package com.example.insurancesystem.saas.service;

import com.example.insurancesystem.saas.support.PortalContextService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import com.example.insurancesystem.utils.RenewalCalendar;
import org.springframework.jdbc.core.RowCallbackHandler;
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
            "SELECT commercial_insurance_start_time,compulsory_insurance_start_time," +
            "commercial_renewal_cycle,commercial_renewal_status,compulsory_renewal_cycle,compulsory_renewal_status " +
            "FROM biz_workorder WHERE enterprise_id=? AND deleted=0 AND renewal_reminder_disabled=0 " +
            "AND (commercial_insurance_start_time IS NOT NULL OR compulsory_insurance_start_time IS NOT NULL)";

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
        long[] renewalCounts = new long[2];
        /*
         * 门户按实际商业/交强起期计算，不再把工单创建时间当作保险日期。
         * 两险种任一待续保只计一张工单；完成当前周期的险种不重复提醒，非车险不参与。
         */
        jdbcTemplate.query(RENEWAL_SQL, (RowCallbackHandler) rows -> {
            Long commercial = rows.getObject("commercial_insurance_start_time", Long.class);
            Long compulsory = rows.getObject("compulsory_insurance_start_time", Long.class);
            int commercialCycle = rows.getInt("commercial_renewal_cycle");
            int compulsoryCycle = rows.getInt("compulsory_renewal_cycle");
            int commercialStatus = rows.getInt("commercial_renewal_status");
            int compulsoryStatus = rows.getInt("compulsory_renewal_status");
            if (needsRenewal(commercial, commercialCycle, commercialStatus, today, 30)
                    || needsRenewal(compulsory, compulsoryCycle, compulsoryStatus, today, 30)) renewalCounts[0]++;
            if (needsRenewal(commercial, commercialCycle, commercialStatus, today, 7)
                    || needsRenewal(compulsory, compulsoryCycle, compulsoryStatus, today, 7)) renewalCounts[1]++;
        }, enterpriseId);
        long renewalCount = renewalCounts[0];
        long renewalDueThisWeek = renewalCounts[1];

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
        result.put("renewalReminderCount", renewalCount);
        result.put("renewalDueThisWeek", renewalDueThisWeek);
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
     * 依据主表起期和独立跟进周期判断门户指定天数内的待续保事项，按配置自然月处理闰年。
     * @param seconds 该险种 Unix 秒起期，无值不回退到创建日。
     * @param handledCycle 最近处理周期。
     * @param status 0未处理，1已续保，2已流失；已处理本轮不重复计入。
     * @param today 中国业务日期。
     * @param days 门户30天或7天窗口，边界日包含。
     * @return 该险种是否需要处理；调用者将两个险种按工单去重。
     */
    static boolean needsRenewal(Long seconds, int handledCycle, int status, LocalDate today, int days) {
        LocalDate start = RenewalCalendar.startDate(seconds);
        if (start == null) return false;
        int cycle = RenewalCalendar.upcomingCycle(start, today);
        if (handledCycle >= cycle && status != 0) return false;
        long remaining = java.time.temporal.ChronoUnit.DAYS.between(today,
                start.plusMonths((long) RenewalCalendar.getCycleMonths() * cycle));
        return remaining >= 0 && remaining <= days;
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
