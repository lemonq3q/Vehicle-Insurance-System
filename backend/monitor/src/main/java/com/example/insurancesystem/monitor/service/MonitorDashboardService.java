package com.example.insurancesystem.monitor.service;

import com.example.insurancesystem.handler.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 监控仪表盘只读统计服务，负责平台卡片、充值趋势、系统调用/OCR 趋势与企业排行。
 * 每个公开方法固定执行一条聚合 SQL：卡片通过三个单行聚合子查询合并为一次往返，
 * 趋势与排行直接利用日表以 stat_date 开头的索引，禁止按企业循环查询形成 N+1。
 */
@Service
public class MonitorDashboardService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final List<String> ALLOWED_METRICS = List.of("request", "ocr");
    private static final Map<String, RangeRule> RANGE_RULES = rangeRules();
    private static final String SUMMARY_SQL =
            "SELECT e.current_count,e.previous_count,u.current_workorders,u.previous_workorders," +
            "u.current_requests,u.previous_requests,u.current_ocr,u.previous_ocr," +
            "r.current_recharge,r.previous_recharge FROM " +
            "(SELECT COALESCE(SUM(CASE WHEN created_at<? THEN 1 ELSE 0 END),0) current_count," +
            "COALESCE(SUM(CASE WHEN created_at<? THEN 1 ELSE 0 END),0) previous_count " +
            "FROM tenant_enterprise WHERE deleted=0) e CROSS JOIN " +
            "(SELECT COALESCE(SUM(CASE WHEN stat_date>=? AND stat_date<? THEN processed_workorder_count ELSE 0 END),0) current_workorders," +
            "COALESCE(SUM(CASE WHEN stat_date>=? AND stat_date<? THEN processed_workorder_count ELSE 0 END),0) previous_workorders," +
            "COALESCE(SUM(CASE WHEN stat_date>=? AND stat_date<? THEN request_count ELSE 0 END),0) current_requests," +
            "COALESCE(SUM(CASE WHEN stat_date>=? AND stat_date<? THEN request_count ELSE 0 END),0) previous_requests," +
            "COALESCE(SUM(CASE WHEN stat_date>=? AND stat_date<? THEN ocr_count ELSE 0 END),0) current_ocr," +
            "COALESCE(SUM(CASE WHEN stat_date>=? AND stat_date<? THEN ocr_count ELSE 0 END),0) previous_ocr " +
            "FROM monitor_enterprise_daily_usage WHERE stat_date>=? AND stat_date<?) u CROSS JOIN " +
            "(SELECT COALESCE(SUM(CASE WHEN paid_at>=? AND paid_at<? THEN amount ELSE 0 END),0) current_recharge," +
            "COALESCE(SUM(CASE WHEN paid_at>=? AND paid_at<? THEN amount ELSE 0 END),0) previous_recharge " +
            "FROM saas_recharge_order WHERE deleted=0 AND status=2 AND paid_at>=? AND paid_at<?) r";

    private final JdbcTemplate jdbcTemplate;

    /** 注入项目现有 JDBC 查询能力，所有查询均为只读聚合且不维护额外缓存。 */
    public MonitorDashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 一次查询返回五张卡片的本月/上月值。当前企业总数以尚未删除且已创建的企业为准；
     * 上月值表示当前仍存在企业在上月月末的规模，因为企业表没有 deleted_at，无法还原已软删除企业。
     * 已处理工单、请求与 OCR 直接从企业日统计宽表汇总，充值只统计已支付订单。
     *
     * @return 五项指标及方向、绝对变化比例，上一期为零时方向为 NEW 且比例为空
     */
    public Map<String, Object> summary() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate currentStart = today.withDayOfMonth(1);
        LocalDate nextStart = currentStart.plusMonths(1);
        LocalDate previousStart = currentStart.minusMonths(1);
        Map<String, Object> row = jdbcTemplate.queryForMap(SUMMARY_SQL,
                Date.valueOf(today.plusDays(1)), Date.valueOf(currentStart),
                Date.valueOf(currentStart), Date.valueOf(nextStart), Date.valueOf(previousStart), Date.valueOf(currentStart),
                Date.valueOf(currentStart), Date.valueOf(nextStart), Date.valueOf(previousStart), Date.valueOf(currentStart),
                Date.valueOf(currentStart), Date.valueOf(nextStart), Date.valueOf(previousStart), Date.valueOf(currentStart),
                Date.valueOf(previousStart), Date.valueOf(nextStart),
                Date.valueOf(currentStart), Date.valueOf(nextStart), Date.valueOf(previousStart), Date.valueOf(currentStart),
                Date.valueOf(previousStart), Date.valueOf(nextStart));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enterprise", metric(row, "current_count", "previous_count"));
        result.put("workorder", metric(row, "current_workorders", "previous_workorders"));
        result.put("recharge", metric(row, "current_recharge", "previous_recharge"));
        result.put("request", metric(row, "current_requests", "previous_requests"));
        result.put("ocr", metric(row, "current_ocr", "previous_ocr"));
        result.put("updatedAt", java.time.LocalDateTime.now(BUSINESS_ZONE).toString());
        return result;
    }

    /**
     * 用一条按月份分组 SQL 返回包含本月在内的近十二个月已支付充值额，之后只在内存中补零。
     * WHERE 对 paid_at 使用闭开区间，不包裹 DATE_FORMAT，确保 idx_recharge_paid_rollup 可做范围扫描。
     *
     * @return 固定十二个 yyyy-MM 标签及对应金额
     */
    public Map<String, Object> rechargeTrend() {
        YearMonth endMonth = YearMonth.now(BUSINESS_ZONE);
        YearMonth startMonth = endMonth.minusMonths(11);
        LocalDate start = startMonth.atDay(1);
        LocalDate end = endMonth.plusMonths(1).atDay(1);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(paid_at,'%Y-%m') bucket,COALESCE(SUM(amount),0) value " +
                "FROM saas_recharge_order WHERE deleted=0 AND status=2 AND paid_at>=? AND paid_at<? " +
                "GROUP BY DATE_FORMAT(paid_at,'%Y-%m') ORDER BY bucket",
                Date.valueOf(start), Date.valueOf(end));
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        rows.forEach(row -> values.put(String.valueOf(row.get("bucket")), decimal(row.get("value"))));
        List<Map<String, Object>> points = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            String label = startMonth.plusMonths(index).format(MONTH_LABEL);
            points.add(point(label, values.getOrDefault(label, BigDecimal.ZERO)));
        }
        return Map.of("interval", "MONTH", "points", points);
    }

    /**
     * 按独立时间范围查询系统调用或 OCR 趋势。7/15/30 天按日、3 个月按周、6 个月和一年按月，
     * 分组表达式来自服务端白名单而非用户输入；一条 SQL 汇总全部企业，结果在内存中补齐无数据周期。
     *
     * @param metric request 或 ocr
     * @param range 7d、15d、30d、3m、6m、1y
     * @return 时间粒度和连续趋势点
     */
    public Map<String, Object> usageTrend(String metric, String range) {
        if (!ALLOWED_METRICS.contains(metric)) throw new BusinessException(400, "不支持的趋势指标");
        RangeRule rule = rangeRule(range);
        String column = "request".equals(metric) ? "request_count" : "ocr_count";
        String bucketExpression = bucketExpression(rule.interval);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + bucketExpression + " bucket,COALESCE(SUM(" + column + "),0) value " +
                "FROM monitor_enterprise_daily_usage WHERE stat_date>=? AND stat_date<? " +
                "GROUP BY " + bucketExpression + " ORDER BY bucket",
                Date.valueOf(rule.start), Date.valueOf(rule.end));
        return Map.of("range", rule.code, "interval", rule.interval, "points", fillUsagePoints(rule, rows));
    }

    /**
     * 一条 SQL 在指定范围内先按企业聚合请求量，再按总量倒序取 Top N，并关联企业名称。
     * LIMIT 只接受 5/10/20 白名单；聚合从 stat_date 范围索引进入，避免逐企业扫描和 N+1 名称查询。
     *
     * @param range 与趋势图一致的统计范围
     * @param top 前端选择的 5、10 或 20
     * @return 企业 ID、名称、系统调用量排行
     */
    public Map<String, Object> ranking(String range, int top) {
        if (top != 5 && top != 10 && top != 20) throw new BusinessException(400, "排行数量仅支持 5、10 或 20");
        RangeRule rule = rangeRule(range);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ranked.enterprise_id enterpriseId,e.name enterpriseName,ranked.requestCount FROM " +
                "(SELECT enterprise_id,SUM(request_count) requestCount FROM monitor_enterprise_daily_usage " +
                "WHERE stat_date>=? AND stat_date<? GROUP BY enterprise_id ORDER BY requestCount DESC LIMIT ?) ranked " +
                "JOIN tenant_enterprise e ON e.id=ranked.enterprise_id AND e.deleted=0 ORDER BY ranked.requestCount DESC",
                Date.valueOf(rule.start), Date.valueOf(rule.end), top);
        return Map.of("range", rule.code, "top", top, "items", rows);
    }

    /** 将当前值、上期值及环比信息组合成前端卡片的稳定结构。 */
    private Map<String, Object> metric(Map<String, Object> row, String currentKey, String previousKey) {
        BigDecimal current = decimal(row.get(currentKey));
        BigDecimal previous = decimal(row.get(previousKey));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current", current);
        result.put("previous", previous);
        result.put("comparison", comparison(current, previous));
        return result;
    }

    /** 计算带方向的绝对环比百分比；上期为零且本期非零时返回 NEW，避免无穷比例。 */
    private Map<String, Object> comparison(BigDecimal current, BigDecimal previous) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (previous.signum() == 0) {
            result.put("direction", current.signum() == 0 ? "FLAT" : "NEW");
            result.put("rate", current.signum() == 0 ? BigDecimal.ZERO : null);
            return result;
        }
        int change = current.compareTo(previous);
        result.put("direction", change > 0 ? "UP" : change < 0 ? "DOWN" : "FLAT");
        result.put("rate", current.subtract(previous).abs().multiply(BigDecimal.valueOf(100))
                .divide(previous.abs(), 1, RoundingMode.HALF_UP));
        return result;
    }

    /** 根据白名单代码返回时间范围，非法参数直接拒绝而不拼接进 SQL。 */
    private RangeRule rangeRule(String code) {
        RangeRule template = RANGE_RULES.get(code);
        if (template == null) throw new BusinessException(400, "时间范围仅支持 7d、15d、30d、3m、6m、1y");
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate start;
        if (template.months > 0) start = today.minusMonths(template.months).plusDays(1);
        else start = today.minusDays(template.days - 1L);
        if ("WEEK".equals(template.interval)) start = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if ("MONTH".equals(template.interval)) start = start.withDayOfMonth(1);
        return new RangeRule(code, template.days, template.months, template.interval, start, today.plusDays(1));
    }

    /** 返回与时间粒度对应的受控 MySQL 分组表达式。 */
    private String bucketExpression(String interval) {
        if ("WEEK".equals(interval)) return "DATE_FORMAT(DATE_SUB(stat_date,INTERVAL WEEKDAY(stat_date) DAY),'%Y-%m-%d')";
        if ("MONTH".equals(interval)) return "DATE_FORMAT(stat_date,'%Y-%m')";
        return "DATE_FORMAT(stat_date,'%Y-%m-%d')";
    }

    /** 将 SQL 返回值转为连续的日、周或月时间轴，保证图表不会因无数据周期错位。 */
    private List<Map<String, Object>> fillUsagePoints(RangeRule rule, List<Map<String, Object>> rows) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        rows.forEach(row -> values.put(String.valueOf(row.get("bucket")), decimal(row.get("value"))));
        List<Map<String, Object>> points = new ArrayList<>();
        LocalDate cursor = rule.start;
        while (cursor.isBefore(rule.end)) {
            String label = "MONTH".equals(rule.interval) ? cursor.format(MONTH_LABEL) : cursor.toString();
            points.add(point(label, values.getOrDefault(label, BigDecimal.ZERO)));
            cursor = "MONTH".equals(rule.interval) ? cursor.plusMonths(1) : "WEEK".equals(rule.interval) ? cursor.plusWeeks(1) : cursor.plusDays(1);
        }
        return points;
    }

    /** 创建保持插入顺序的图表点，便于序列化结果稳定。 */
    private Map<String, Object> point(String label, BigDecimal value) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("label", label);
        point.put("value", value);
        return point;
    }

    /** 把 JDBC 返回的整数或小数统一转换为 BigDecimal，空聚合值按零处理。 */
    private BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    /** 构建唯一允许的时间范围与自动粒度映射。 */
    private static Map<String, RangeRule> rangeRules() {
        Map<String, RangeRule> rules = new LinkedHashMap<>();
        rules.put("7d", new RangeRule("7d", 7, 0, "DAY", null, null));
        rules.put("15d", new RangeRule("15d", 15, 0, "DAY", null, null));
        rules.put("30d", new RangeRule("30d", 30, 0, "DAY", null, null));
        rules.put("3m", new RangeRule("3m", 0, 3, "WEEK", null, null));
        rules.put("6m", new RangeRule("6m", 0, 6, "MONTH", null, null));
        rules.put("1y", new RangeRule("1y", 0, 12, "MONTH", null, null));
        return rules;
    }

    /** 保存已校验时间范围、粒度和闭开日期边界的不可变内部值对象。 */
    private static final class RangeRule {
        private final String code;
        private final int days;
        private final int months;
        private final String interval;
        private final LocalDate start;
        private final LocalDate end;

        private RangeRule(String code, int days, int months, String interval, LocalDate start, LocalDate end) {
            this.code = code;
            this.days = days;
            this.months = months;
            this.interval = interval;
            this.start = start;
            this.end = end;
        }
    }
}
