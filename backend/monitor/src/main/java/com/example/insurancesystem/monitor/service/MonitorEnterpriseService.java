package com.example.insurancesystem.monitor.service;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.utils.SystemCommonUtil;
import com.example.insurancesystem.utils.UniqueCodeRetryUtil;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 监控平台企业查询与套餐管理服务。列表采用一次计数和一次分页聚合，
 * 详情和趋势各执行一条范围 SQL，统计数据只读取企业日统计宽表。
 * 人工设置与取消套餐使用行锁保护每企业唯一订阅状态，并与审计写入共享事务边界。
 */
@Service
public class MonitorEnterpriseService {
    private static final String WALLET_TRANSACTION_NO_CONSTRAINT = "uk_saas_wallet_tx_enterprise_no";
    private static final BigDecimal MAX_WALLET_AMOUNT = new BigDecimal("9999999999.99");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TRANSACTION_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Map<String, RangeTemplate> RANGE_TEMPLATES = rangeTemplates();
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final MonitorSystemLogService systemLog;

    public MonitorEnterpriseService(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc,
            MonitorSystemLogService systemLog) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.systemLog = systemLog;
    }

    /**
     * 组合列表筛选并安全限制分页大小。计数 SQL 不引入日统计表，分页 SQL 一次联合成员、钱包、当前订阅与本月用量，
     * 避免对每个企业发起追加查询。
     */
    public Map<String, Object> page(String keyword, Integer status, Long planId, Integer expireDays,
            int pageNo, int pageSize) {
        pageNo = Math.max(1, pageNo);
        pageSize = pageSize < 1 ? 10 : Math.min(pageSize, 100);
        StringBuilder where = new StringBuilder(" WHERE e.deleted=0");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (e.name LIKE :keyword OR e.code LIKE :keyword)");
            parameters.addValue("keyword", "%" + keyword.trim() + "%");
        }
        if (status != null) { where.append(" AND e.status=:status"); parameters.addValue("status", status); }
        if (planId != null) { where.append(" AND s.plan_id=:planId AND s.status IN (1,3) AND s.end_at>NOW()"); parameters.addValue("planId", planId); }
        if (expireDays != null) {
            if (expireDays != 7 && expireDays != 30) throw new BusinessException(400, "到期范围仅支持 7 天或 30 天");
            where.append(" AND s.status IN (1,3) AND s.end_at>=NOW() AND s.end_at<DATE_ADD(NOW(),INTERVAL :expireDays DAY)");
            parameters.addValue("expireDays", expireDays);
        }
        String joins = " FROM tenant_enterprise e LEFT JOIN saas_subscription s ON s.enterprise_id=e.id";
        long total = namedJdbc.queryForObject("SELECT COUNT(1)" + joins + where, parameters, Long.class);
        parameters.addValue("monthStart", Date.valueOf(YearMonth.now(BUSINESS_ZONE).atDay(1)));
        parameters.addValue("nextMonth", Date.valueOf(YearMonth.now(BUSINESS_ZONE).plusMonths(1).atDay(1)));
        parameters.addValue("offset", (pageNo - 1) * pageSize).addValue("pageSize", pageSize);
        String sql = "SELECT e.id,e.code,e.name,e.status,COALESCE(m.memberCount,0) memberCount," +
                "COALESCE(w.balance_amount,0) balance," +
                "CASE WHEN s.status IN (1,3) AND s.end_at>NOW() THEN s.plan_id END planId," +
                "CASE WHEN s.status IN (1,3) AND s.end_at>NOW() THEN p.name END planName," +
                "CASE WHEN s.status IN (1,3) AND s.end_at>NOW() THEN DATE(s.end_at) END subscriptionEndDate," +
                "COALESCE(u.workorderCount,0) monthWorkorderCount,COALESCE(u.requestCount,0) monthRequestCount," +
                "COALESCE(u.ocrCount,0) monthOcrCount" + joins +
                " LEFT JOIN saas_plan p ON p.id=s.plan_id AND p.deleted=0" +
                " LEFT JOIN saas_wallet w ON w.enterprise_id=e.id AND w.deleted=0" +
                " LEFT JOIN (SELECT enterprise_id,COUNT(1) memberCount FROM tenant_member WHERE deleted=0 AND status=1 GROUP BY enterprise_id) m ON m.enterprise_id=e.id" +
                " LEFT JOIN (SELECT enterprise_id,SUM(processed_workorder_count) workorderCount,SUM(request_count) requestCount,SUM(ocr_count) ocrCount " +
                "FROM monitor_enterprise_daily_usage WHERE stat_date>=:monthStart AND stat_date<:nextMonth GROUP BY enterprise_id) u ON u.enterprise_id=e.id" +
                where + " ORDER BY e.created_at DESC,e.id DESC LIMIT :offset,:pageSize";
        List<Map<String, Object>> list = namedJdbc.queryForList(sql, parameters);
        list.forEach(this::attachMonthUsage);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list); result.put("pageNo", pageNo); result.put("pageSize", pageSize); result.put("total", total);
        return result;
    }

    /**
     * 使用一条 SQL 返回企业资料、有效成员数、最近活跃时间、钱包、当前订阅和本月三项用量。
     * 企业表当前没有备注字段，接口稳定返回空备注；来源依照 1 用户自建、2 后台创建转为展示文案。
     */
    public Map<String, Object> detail(Long id) {
        LocalDate monthStart = YearMonth.now(BUSINESS_ZONE).atDay(1);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT e.id,e.code,e.name,e.status,e.contact_name contactName,e.contact_phone contactPhone,e.created_at createdAt," +
                "CASE e.source WHEN 1 THEN '用户自建' WHEN 2 THEN '后台创建' ELSE '未知' END source," +
                "COALESCE(m.memberCount,0) memberCount,m.lastActiveAt,COALESCE(w.balance_amount,0) balance," +
                "CASE WHEN s.status IN (1,3) AND s.end_at>NOW() THEN s.plan_id END planId," +
                "CASE WHEN s.status IN (1,3) AND s.end_at>NOW() THEN p.name END planName," +
                "CASE WHEN s.status IN (1,3) AND s.end_at>NOW() THEN s.user_limit ELSE 0 END memberLimit," +
                "CASE WHEN s.status IN (1,3) AND s.end_at>NOW() THEN DATE(s.end_at) END subscriptionEndDate," +
                "COALESCE(u.workorderCount,0) monthWorkorderCount,COALESCE(u.requestCount,0) monthRequestCount,COALESCE(u.ocrCount,0) monthOcrCount " +
                "FROM tenant_enterprise e LEFT JOIN saas_subscription s ON s.enterprise_id=e.id " +
                "LEFT JOIN saas_plan p ON p.id=s.plan_id AND p.deleted=0 LEFT JOIN saas_wallet w ON w.enterprise_id=e.id AND w.deleted=0 " +
                "LEFT JOIN (SELECT tm.enterprise_id,COUNT(1) memberCount,MAX(tu.last_login_time) lastActiveAt FROM tenant_member tm " +
                "JOIN tenant_user tu ON tu.id=tm.user_id AND tu.deleted=0 WHERE tm.deleted=0 AND tm.status=1 GROUP BY tm.enterprise_id) m ON m.enterprise_id=e.id " +
                "LEFT JOIN (SELECT enterprise_id,SUM(processed_workorder_count) workorderCount,SUM(request_count) requestCount,SUM(ocr_count) ocrCount " +
                "FROM monitor_enterprise_daily_usage WHERE stat_date>=? AND stat_date<? AND enterprise_id=? GROUP BY enterprise_id) u ON u.enterprise_id=e.id " +
                "WHERE e.id=? AND e.deleted=0",
                Date.valueOf(monthStart), Date.valueOf(monthStart.plusMonths(1)), id, id);
        if (rows.isEmpty()) throw new BusinessException(404, "企业不存在");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        result.put("remark", null);
        attachMonthUsage(result);
        return result;
    }

    /**
     * 按与仪表盘一致的范围规则聚合单企业用量：7/15/30 天按日，3 个月按周，6 个月和 1 年按月。
     * 分组表达式只由服务端白名单生成，企业 ID 和日期边界使用索引列的闭开范围。
     */
    public Map<String, Object> usage(Long id, String range) {
        ensureEnterprise(id);
        RangeRule rule = rangeRule(range);
        String bucket = bucketExpression(rule.interval);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + bucket + " label,SUM(processed_workorder_count) workorderCount,SUM(request_count) requestCount,SUM(ocr_count) ocrCount " +
                "FROM monitor_enterprise_daily_usage WHERE enterprise_id=? AND stat_date>=? AND stat_date<? GROUP BY " + bucket + " ORDER BY label",
                id, Date.valueOf(rule.start), Date.valueOf(rule.end));
        Map<String, Map<String, Object>> values = new HashMap<>();
        rows.forEach(row -> values.put(String.valueOf(row.get("label")), row));
        List<Map<String, Object>> points = new ArrayList<>();
        LocalDate cursor = rule.start;
        while (cursor.isBefore(rule.end)) {
            String label = "MONTH".equals(rule.interval) ? cursor.format(MONTH_LABEL) : cursor.toString();
            Map<String, Object> source = values.get(label);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("label", label);
            point.put("workorderCount", source == null ? 0 : source.get("workorderCount"));
            point.put("requestCount", source == null ? 0 : source.get("requestCount"));
            point.put("ocrCount", source == null ? 0 : source.get("ocrCount"));
            points.add(point);
            cursor = "MONTH".equals(rule.interval) ? cursor.plusMonths(1) : "WEEK".equals(rule.interval) ? cursor.plusWeeks(1) : cursor.plusDays(1);
        }
        return Map.of("range", rule.code, "interval", rule.interval, "points", points);
    }

    /**
     * 分页读取指定企业的成员身份和账号快照。成员表决定企业角色与启停状态，用户表提供姓名、账号、手机号和最后登录时间；
     * 手机号在 SQL 返回阶段统一脱敏，监控平台不获得完整号码。关键字支持姓名、账号和原始手机号包含匹配，
     * 角色文案先映射到固定角色代码，所有筛选值均通过命名参数绑定。
     *
     * @param enterpriseId 当前查看的企业主键
     * @param keyword 姓名、登录账号或手机号关键词
     * @param roleName 拥有者、管理员或出单员
     * @param status 成员状态，0 停用、1 启用
     * @param pageNo 页码，从 1 开始
     * @param pageSize 每页条数，限制为 1 至 100
     * @return 企业成员分页结果
     */
    public Map<String, Object> members(Long enterpriseId, String keyword, String roleName,
            Integer status, int pageNo, int pageSize) {
        ensureEnterprise(enterpriseId);
        pageNo = Math.max(1, pageNo);
        pageSize = pageSize < 1 ? 10 : Math.min(pageSize, 100);
        if (status != null && status != 0 && status != 1)
            throw new BusinessException(400, "成员状态仅支持启用或停用");

        StringBuilder where = new StringBuilder(
                " WHERE m.enterprise_id=:enterpriseId AND m.deleted=0 AND u.deleted=0");
        MapSqlParameterSource parameters = new MapSqlParameterSource("enterpriseId", enterpriseId);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            if (value.length() > 100) throw new BusinessException(400, "成员关键词不能超过 100 个字符");
            where.append(" AND (u.real_name LIKE :keyword OR u.username LIKE :keyword OR u.phone LIKE :keyword)");
            parameters.addValue("keyword", "%" + value + "%");
        }
        if (roleName != null && !roleName.isBlank()) {
            where.append(" AND m.role_code=:roleCode");
            parameters.addValue("roleCode", memberRoleCode(roleName.trim()));
        }
        if (status != null) {
            where.append(" AND m.status=:status");
            parameters.addValue("status", status);
        }

        String from = " FROM tenant_member m JOIN tenant_user u ON u.id=m.user_id";
        long total = namedJdbc.queryForObject("SELECT COUNT(1)" + from + where, parameters, Long.class);
        parameters.addValue("offset", (pageNo - 1) * pageSize).addValue("pageSize", pageSize);
        String sql = "SELECT m.id,m.enterprise_id enterpriseId,u.real_name realName,u.username," +
                "CASE WHEN u.phone IS NULL OR u.phone='' THEN '-' WHEN CHAR_LENGTH(u.phone)>=7 " +
                "THEN CONCAT(LEFT(u.phone,3),'****',RIGHT(u.phone,4)) ELSE u.phone END phone," +
                "CASE m.role_code WHEN 'OWNER' THEN '拥有者' WHEN 'ADMIN' THEN '管理员' WHEN 'ISSUER' THEN '出单员' ELSE m.role_code END roleName," +
                "m.status,m.joined_at joinedAt,u.last_login_time lastLoginAt" + from + where +
                " ORDER BY FIELD(m.role_code,'OWNER','ADMIN','ISSUER'),m.joined_at,m.id LIMIT :offset,:pageSize";
        List<Map<String, Object>> list = namedJdbc.queryForList(sql, parameters);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list); result.put("pageNo", pageNo); result.put("pageSize", pageSize); result.put("total", total);
        return result;
    }

    /** 将页面角色文案映射为租户成员表的固定代码，拒绝未知角色进入查询。 */
    private String memberRoleCode(String roleName) {
        if ("拥有者".equals(roleName) || "OWNER".equals(roleName)) return "OWNER";
        if ("管理员".equals(roleName) || "ADMIN".equals(roleName)) return "ADMIN";
        if ("出单员".equals(roleName) || "ISSUER".equals(roleName)) return "ISSUER";
        throw new BusinessException(400, "企业角色仅支持拥有者、管理员或出单员");
    }

    /**
     * 按名称或企业编码模糊搜索可参与对比的企业。空关键词返回空集合，避免页面打开时加载企业全集；
     * 搜索结果限制为二十条并稳定按名称、ID 排序，供前端联想列表直接展示。
     */
    public List<Map<String, Object>> options(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        String value = "%" + keyword.trim() + "%";
        return jdbc.queryForList("SELECT id,code,name FROM tenant_enterprise " +
                "WHERE deleted=0 AND (name LIKE ? OR code LIKE ?) ORDER BY name,id LIMIT 20", value, value);
    }

    /**
     * 在同一统计窗口内按出单量、系统调用量或 OCR 使用量汇总企业用量并返回 Top N。
     * 指标列由服务端白名单映射，Top 数量限制为 1 至 20，避免动态 SQL 注入和无界查询。
     */
    public List<Map<String, Object>> usageTop(String metric, String range, int top) {
        RangeRule rule = rangeRule(range);
        String column = metricColumn(metric);
        if (top < 1 || top > 20) throw new BusinessException(400, "Top 数量仅支持 1 至 20");
        return jdbc.queryForList("SELECT e.id,e.code,e.name,COALESCE(SUM(u." + column + "),0) metricValue " +
                "FROM tenant_enterprise e LEFT JOIN monitor_enterprise_daily_usage u ON u.enterprise_id=e.id " +
                "AND u.stat_date>=? AND u.stat_date<? WHERE e.deleted=0 GROUP BY e.id,e.code,e.name " +
                "ORDER BY metricValue DESC,e.id ASC LIMIT ?", Date.valueOf(rule.start), Date.valueOf(rule.end), top);
    }

    /**
     * 解析并校验逗号分隔企业 ID，在统一粒度下聚合三项用量，再为每家企业补齐缺失周期的零值点。
     * 返回顺序严格遵循用户选择顺序，使图例、占比和排名在刷新前后保持稳定。
     */
    public Map<String, Object> usageComparison(String enterpriseIds, String range) {
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        try {
            Arrays.stream(enterpriseIds.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                    .map(Long::valueOf).forEach(uniqueIds::add);
        } catch (Exception exception) {
            throw new BusinessException(400, "企业 ID 格式不正确");
        }
        if (uniqueIds.size() < 1 || uniqueIds.size() > 20)
            throw new BusinessException(400, "请选择 1 至 20 家企业进行对比");
        if (uniqueIds.stream().anyMatch(id -> id <= 0)) throw new BusinessException(400, "企业 ID 格式不正确");

        RangeRule rule = rangeRule(range);
        String bucket = bucketExpression(rule.interval);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("ids", uniqueIds).addValue("start", Date.valueOf(rule.start)).addValue("end", Date.valueOf(rule.end));
        List<Map<String, Object>> enterpriseRows = namedJdbc.queryForList(
                "SELECT id,name FROM tenant_enterprise WHERE deleted=0 AND id IN (:ids)", parameters);
        if (enterpriseRows.size() != uniqueIds.size()) throw new BusinessException(404, "部分企业不存在或已删除");
        List<Map<String, Object>> usageRows = namedJdbc.queryForList(
                "SELECT enterprise_id enterpriseId," + bucket + " label," +
                "SUM(processed_workorder_count) workorderCount,SUM(request_count) requestCount,SUM(ocr_count) ocrCount " +
                "FROM monitor_enterprise_daily_usage WHERE enterprise_id IN (:ids) AND stat_date>=:start AND stat_date<:end " +
                "GROUP BY enterprise_id," + bucket + " ORDER BY label", parameters);

        List<String> labels = rangeLabels(rule);
        Map<Long, String> names = new HashMap<>();
        enterpriseRows.forEach(row -> names.put(((Number) row.get("id")).longValue(), String.valueOf(row.get("name"))));
        Map<String, Map<String, Object>> values = new HashMap<>();
        usageRows.forEach(row -> values.put(row.get("enterpriseId") + "@" + row.get("label"), row));
        List<Map<String, Object>> enterprises = new ArrayList<>();
        uniqueIds.forEach(id -> {
            List<Map<String, Object>> points = new ArrayList<>();
            labels.forEach(label -> {
                Map<String, Object> source = values.get(id + "@" + label);
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("label", label);
                point.put("workorderCount", source == null ? 0 : source.get("workorderCount"));
                point.put("requestCount", source == null ? 0 : source.get("requestCount"));
                point.put("ocrCount", source == null ? 0 : source.get("ocrCount"));
                points.add(point);
            });
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("enterpriseId", id); item.put("enterpriseName", names.get(id)); item.put("points", points);
            enterprises.add(item);
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("range", rule.code); result.put("interval", rule.interval);
        result.put("labels", labels); result.put("enterprises", enterprises);
        return result;
    }

    /** 一次读取未删除套餐的精简字段，状态字段供列表筛选和设置弹窗区分上下架。 */
    public List<Map<String, Object>> subscriptionPlans() {
        return jdbc.queryForList("SELECT id,code,name,status,user_limit memberLimit,workorder_limit workorderLimit,duration_days durationDays " +
                "FROM saas_plan WHERE deleted=0 ORDER BY sort_no,id");
    }

    /**
     * 从真实 SaaS 财务表汇总企业资金概况。余额读取唯一有效钱包；累计充值只统计已支付充值订单，
     * 套餐支出只统计已支付订阅订单的实际支付金额；本月流水使用上海业务时区的自然月闭开边界。
     *
     * @param enterpriseId 当前查看的企业主键
     * @return balance、totalRecharge、totalSubscriptionExpense 和 monthTransactionCount
     */
    public Map<String, Object> financeSummary(Long enterpriseId) {
        ensureEnterprise(enterpriseId);
        LocalDate monthStart = YearMonth.now(BUSINESS_ZONE).atDay(1);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT COALESCE((SELECT w.balance_amount FROM saas_wallet w WHERE w.enterprise_id=? AND w.deleted=0 LIMIT 1),0) balance," +
                "COALESCE((SELECT SUM(r.amount) FROM saas_recharge_order r WHERE r.enterprise_id=? AND r.deleted=0 AND r.status=2),0) totalRecharge," +
                "COALESCE((SELECT SUM(COALESCE(o.paid_amount,o.payable_amount,o.amount,0)) FROM saas_order o WHERE o.enterprise_id=? AND o.deleted=0 AND o.status=2),0) totalSubscriptionExpense," +
                "(SELECT COUNT(1) FROM saas_wallet_transaction t WHERE t.enterprise_id=? AND t.created_at>=? AND t.created_at<?) monthTransactionCount",
                enterpriseId, enterpriseId, enterpriseId, enterpriseId,
                Timestamp.valueOf(monthStart.atStartOfDay()), Timestamp.valueOf(monthStart.plusMonths(1).atStartOfDay()));
        return new LinkedHashMap<>(rows.get(0));
    }

    /**
     * 分页查询企业充值订单、订阅订单或钱包流水。资源类型仅接受控制器传入的三个白名单值，
     * 业务编号根据资源映射到真实编号列并做包含匹配；日期按企业业务时区解释为自然日闭区间，
     * SQL 使用结束日次日作为排他上界，保证包含结束日期全天且可以命中企业与创建时间联合索引。
     *
     * @param enterpriseId 监控页面当前查看的企业
     * @param resource 充值订单、订阅订单或钱包流水资源代码
     * @param businessNo 对应资源业务编号的可选关键词
     * @param startDate 可选开始日期，格式 yyyy-MM-dd
     * @param endDate 可选结束日期，格式 yyyy-MM-dd
     * @param pageNo 页码，从 1 开始
     * @param pageSize 每页条数，限制为 1 至 100
     * @return 与监控前端分页契约一致的 list、pageNo、pageSize 和 total
     */
    public Map<String, Object> financeRecords(Long enterpriseId, String resource, String businessNo,
            String startDate, String endDate, int pageNo, int pageSize) {
        ensureEnterprise(enterpriseId);
        pageNo = Math.max(1, pageNo);
        pageSize = pageSize < 1 ? 10 : Math.min(pageSize, 100);

        FinanceResource definition = financeResource(resource);
        LocalDate start = optionalDate(startDate, "开始日期");
        LocalDate end = optionalDate(endDate, "结束日期");
        if (start != null && end != null && start.isAfter(end))
            throw new BusinessException(400, "开始日期不能晚于结束日期");

        StringBuilder where = new StringBuilder(" WHERE r.enterprise_id=:enterpriseId");
        if (definition.hasDeleted) where.append(" AND r.deleted=0");
        MapSqlParameterSource parameters = new MapSqlParameterSource("enterpriseId", enterpriseId);
        if (businessNo != null && !businessNo.isBlank()) {
            String keyword = businessNo.trim();
            if (keyword.length() > 64) throw new BusinessException(400, "业务编号不能超过 64 个字符");
            where.append(" AND r.").append(definition.numberColumn).append(" LIKE :businessNo");
            parameters.addValue("businessNo", "%" + keyword + "%");
        }
        if (start != null) {
            where.append(" AND r.created_at>=:startAt");
            parameters.addValue("startAt", Timestamp.valueOf(start.atStartOfDay()));
        }
        if (end != null) {
            where.append(" AND r.created_at<:endAt");
            parameters.addValue("endAt", Timestamp.valueOf(end.plusDays(1).atStartOfDay()));
        }

        long total = namedJdbc.queryForObject("SELECT COUNT(1) " + definition.fromSql + where, parameters, Long.class);
        parameters.addValue("offset", (pageNo - 1) * pageSize).addValue("pageSize", pageSize);
        List<Map<String, Object>> list = namedJdbc.queryForList(
                definition.selectSql + " " + definition.fromSql + where
                        + " ORDER BY r.created_at DESC,r.id DESC LIMIT :offset,:pageSize",
                parameters);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list); result.put("pageNo", pageNo); result.put("pageSize", pageSize); result.put("total", total);
        return result;
    }

    /**
     * 执行监控平台人工余额调账。方法先校验金额精度和原因，再锁定企业及其唯一钱包，拒绝产生负余额或超出
     * DECIMAL(12,2) 上限的结果；钱包更新、ADJUST 流水和监控审计共享事务，任一步失败都会整体回滚。
     * 正金额生成 IN 流水，负金额生成 OUT 流水，流水 amount 始终保存绝对值以沿用 SaaS 记账规则。
     *
     * @param enterpriseId 被调整余额的企业主键
     * @param body amount 为非零两位小数，reason 为最多 500 字的审计原因
     * @param operatorId 当前监控账号主键，由认证上下文提供
     * @param operatorName 当前监控账号姓名快照
     * @return 调整完成后的实时余额
     */
    @Transactional
    public Map<String, Object> adjustBalance(Long enterpriseId, Map<String, Object> body,
            Long operatorId, String operatorName) {
        BigDecimal delta = requiredAdjustmentAmount(body);
        String reason = requiredText(body, "reason", "请填写操作原因");
        Map<String, Object> enterprise = lockEnterprise(enterpriseId);
        List<Map<String, Object>> wallets = jdbc.queryForList(
                "SELECT id,balance_amount balance FROM saas_wallet WHERE enterprise_id=? AND deleted=0 FOR UPDATE",
                enterpriseId);
        if (wallets.isEmpty()) throw new BusinessException(409, "企业钱包尚未初始化");

        Map<String, Object> wallet = wallets.get(0);
        long walletId = ((Number) wallet.get("id")).longValue();
        BigDecimal balanceBefore = new BigDecimal(String.valueOf(wallet.get("balance"))).setScale(2);
        BigDecimal balanceAfter = balanceBefore.add(delta);
        if (balanceAfter.signum() < 0) throw new BusinessException(409, "调整后余额不能小于零");
        if (balanceAfter.compareTo(MAX_WALLET_AMOUNT) > 0) throw new BusinessException(400, "调整后余额超出钱包金额上限");

        jdbc.update("UPDATE saas_wallet SET balance_amount=?,updated_by=?,updated_at=NOW() WHERE id=? AND deleted=0",
                balanceAfter, operatorId, walletId);
        Map<String, Object> transaction = new HashMap<>();
        UniqueCodeRetryUtil.insertWithGeneratedCode(
                WALLET_TRANSACTION_NO_CONSTRAINT,
                () -> "TX" + LocalDate.now(BUSINESS_ZONE).format(TRANSACTION_DATE)
                        + SystemCommonUtil.buildCode().substring(0, 5),
                code -> transaction.put("transactionNo", code),
                () -> jdbc.update(
                        "INSERT INTO saas_wallet_transaction(enterprise_id,wallet_id,user_id,transaction_no,direction," +
                        "transaction_type,amount,balance_before,balance_after,remark,created_at) " +
                        "VALUES(?,?,?,?,?,'ADJUST',?,?,?,?,NOW())",
                        enterpriseId, walletId, operatorId, transaction.get("transactionNo"),
                        delta.signum() > 0 ? "IN" : "OUT", delta.abs(), balanceBefore, balanceAfter,
                        "平台人工调账：" + reason));

        Map<String, Object> before = Map.of("balance", balanceBefore);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("balance", balanceAfter); after.put("adjustmentAmount", delta);
        after.put("transactionNo", transaction.get("transactionNo"));
        String directionText = delta.signum() > 0 ? "调增" : "调减";
        systemLog.recordOperation("BALANCE_ADJUST", "人工调整企业余额", "enterprise",
                operatorId, operatorName, enterpriseId, String.valueOf(enterprise.get("name")),
                "WALLET", walletId, String.valueOf(enterprise.get("name")) + "钱包", reason,
                directionText + "余额 ¥" + delta.abs().setScale(2) + "，调整后余额 ¥" + balanceAfter,
                before, after);
        return Map.of("balance", balanceAfter);
    }

    /**
     * 将请求金额转换为钱包精度。金额必须非零、最多两位有效小数且绝对值不超过钱包字段上限，
     * 在取得数据库行锁前完成廉价校验，避免无效请求占用企业财务锁。
     */
    private BigDecimal requiredAdjustmentAmount(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("amount");
        if (raw == null || raw.toString().isBlank()) throw new BusinessException(400, "请输入非零调整金额");
        try {
            BigDecimal value = new BigDecimal(raw.toString().trim());
            if (value.signum() == 0) throw new BusinessException(400, "调整金额不能为零");
            if (Math.max(0, value.stripTrailingZeros().scale()) > 2)
                throw new BusinessException(400, "调整金额最多保留两位小数");
            if (value.abs().compareTo(MAX_WALLET_AMOUNT) > 0)
                throw new BusinessException(400, "调整金额超出钱包金额上限");
            return value.setScale(2);
        } catch (BusinessException exception) { throw exception; }
        catch (Exception exception) { throw new BusinessException(400, "调整金额格式不正确"); }
    }

    /** 将可选日期参数解析为业务日，非法格式统一返回明确的参数错误。 */
    private LocalDate optionalDate(String value, String label) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value.trim()); }
        catch (Exception exception) { throw new BusinessException(400, label + "格式必须为 yyyy-MM-dd"); }
    }

    /**
     * 将固定资源代码映射为受控表名、编号列和返回字段，避免任何客户端字符串参与 SQL 结构拼接。
     * 订阅套餐名称优先读取订单快照，钱包流水通过关联主键还原对应充值单号或订阅单号。
     */
    private FinanceResource financeResource(String resource) {
        if ("recharge-orders".equals(resource)) return new FinanceResource(
                "SELECT r.id,r.enterprise_id enterpriseId,r.recharge_no orderNo,r.amount,r.pay_channel channel,r.status,r.paid_at paidAt,r.created_at createdAt",
                "FROM saas_recharge_order r", "recharge_no", true);
        if ("subscription-orders".equals(resource)) return new FinanceResource(
                "SELECT r.id,r.enterprise_id enterpriseId,r.order_no orderNo,COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.plan_snapshot_json,'$.name')),p.name) planName,COALESCE(r.paid_amount,r.payable_amount,r.amount,0) amount,r.status,DATE(r.paid_at) startedAt,DATE(DATE_ADD(r.paid_at,INTERVAL COALESCE(r.buy_duration_days,0) DAY)) endedAt,r.created_at createdAt",
                "FROM saas_order r LEFT JOIN saas_plan p ON p.id=r.plan_id", "order_no", true);
        if ("wallet-transactions".equals(resource)) return new FinanceResource(
                "SELECT r.id,r.enterprise_id enterpriseId,r.transaction_no transactionNo,r.transaction_type type,CASE WHEN r.direction='OUT' THEN -ABS(r.amount) ELSE r.amount END amount,r.balance_after balanceAfter,COALESCE(o.order_no,ro.recharge_no) referenceNo,r.remark,r.created_at createdAt",
                "FROM saas_wallet_transaction r LEFT JOIN saas_order o ON o.id=r.related_order_id LEFT JOIN saas_recharge_order ro ON ro.id=r.related_recharge_order_id",
                "transaction_no", false);
        throw new BusinessException(400, "不支持的财务资源类型");
    }

    /** 保存受控财务资源的查询片段；实例只在服务端静态分支中创建。 */
    private static final class FinanceResource {
        private final String selectSql; private final String fromSql; private final String numberColumn; private final boolean hasDeleted;
        private FinanceResource(String selectSql, String fromSql, String numberColumn, boolean hasDeleted) {
            this.selectSql = selectSql; this.fromSql = fromSql; this.numberColumn = numberColumn; this.hasDeleted = hasDeleted;
        }
    }

    /**
     * 锁定企业和唯一订阅行，校验上架套餐与未来到期日后更新当前权益。
     * 管理员人工设置不生成 SaaS 购买订单或扣款；该边界由审计原因和操作人身份约束。
     */
    @Transactional
    public Map<String, Object> setSubscription(Long enterpriseId, Map<String, Object> body,
            Long operatorId, String operatorName) {
        long planId = requiredLong(body, "planId", "请选择套餐");
        String reason = requiredText(body, "reason", "请填写操作原因");
        LocalDate endDate;
        try { endDate = LocalDate.parse(requiredText(body, "endDate", "请选择到期日期")); }
        catch (BusinessException exception) { throw exception; }
        catch (Exception exception) { throw new BusinessException(400, "到期日期格式应为 yyyy-MM-dd"); }
        if (!endDate.isAfter(LocalDate.now(BUSINESS_ZONE))) throw new BusinessException(400, "到期日期必须晚于今天");
        Map<String, Object> enterprise = lockEnterprise(enterpriseId);
        List<Map<String, Object>> plans = jdbc.queryForList(
                "SELECT id,name,user_limit memberLimit,workorder_limit workorderLimit FROM saas_plan WHERE id=? AND deleted=0 AND status=1", planId);
        if (plans.isEmpty()) throw new BusinessException(404, "套餐不存在或已下架");
        Map<String, Object> plan = plans.get(0);
        List<Map<String, Object>> subscriptions = jdbc.queryForList(
                "SELECT id,plan_id planId,status,user_limit userLimit,workorder_limit workorderLimit,start_at startAt,end_at endAt " +
                "FROM saas_subscription WHERE enterprise_id=? FOR UPDATE", enterpriseId);
        Map<String, Object> before = subscriptions.isEmpty() ? null : new LinkedHashMap<>(subscriptions.get(0));
        LocalDateTime expiration = endDate.atTime(23, 59, 59);
        long subscriptionId;
        if (subscriptions.isEmpty()) {
            jdbc.update("INSERT INTO saas_subscription(enterprise_id,plan_id,status,user_limit,workorder_limit,ocr_quota,request_quota,start_at,end_at,auto_renew_enabled,created_at,updated_at) " +
                    "VALUES(?,?,1,?,?,0,0,NOW(),?,0,NOW(),NOW())", enterpriseId, planId, plan.get("memberLimit"), plan.get("workorderLimit"), Timestamp.valueOf(expiration));
            subscriptionId = jdbc.queryForObject("SELECT id FROM saas_subscription WHERE enterprise_id=?", Long.class, enterpriseId);
        } else {
            subscriptionId = ((Number) subscriptions.get(0).get("id")).longValue();
            jdbc.update("UPDATE saas_subscription SET plan_id=?,status=1,suspend_reason=NULL,suspended_at=NULL,resumed_at=NOW()," +
                    "user_limit=?,workorder_limit=?,ocr_quota=0,request_quota=0,start_at=NOW(),end_at=?,auto_renew_enabled=0," +
                    "auto_renew_plan_id=NULL,next_renew_at=NULL,cancel_auto_renew_at=NOW(),updated_at=NOW() WHERE id=?",
                    planId, plan.get("memberLimit"), plan.get("workorderLimit"), Timestamp.valueOf(expiration), subscriptionId);
        }
        String description = "开通了" + plan.get("name") + "套餐，到期时间为" + endDate;
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("subscriptionId", subscriptionId); after.put("planId", planId); after.put("planName", plan.get("name"));
        after.put("status", 1); after.put("userLimit", plan.get("memberLimit"));
        after.put("workorderLimit", plan.get("workorderLimit")); after.put("endAt", expiration);
        systemLog.recordOperation("SUBSCRIPTION_SET", "人工设置企业套餐", "enterprise",
                operatorId, operatorName, enterpriseId, String.valueOf(enterprise.get("name")),
                "SUBSCRIPTION", subscriptionId, String.valueOf(plan.get("name")), reason, description, before, after);
        return detail(enterpriseId);
    }

    /**
     * 锁定企业当前订阅，仅允许取消已关联套餐的状态。取消后保留 plan_id 作为历史语义，
     * 但状态改为 4 并清零权益、有效期和自动续费信息；前端详情只将生效状态解析为当前套餐。
     */
    @Transactional
    public void cancelSubscription(Long enterpriseId, Map<String, Object> body,
            Long operatorId, String operatorName) {
        String reason = requiredText(body, "reason", "请填写取消原因");
        Map<String, Object> enterprise = lockEnterprise(enterpriseId);
        List<Map<String, Object>> subscriptions = jdbc.queryForList(
                "SELECT s.id,s.plan_id planId,p.name planName FROM saas_subscription s LEFT JOIN saas_plan p ON p.id=s.plan_id " +
                "WHERE s.enterprise_id=? AND s.status IN (1,3) AND s.plan_id IS NOT NULL FOR UPDATE", enterpriseId);
        if (subscriptions.isEmpty()) throw new BusinessException(409, "企业当前没有可取消的套餐");
        Map<String, Object> subscription = subscriptions.get(0);
        long subscriptionId = ((Number) subscription.get("id")).longValue();
        Map<String, Object> before = new LinkedHashMap<>(subscription);
        jdbc.update("UPDATE saas_subscription SET status=4,suspend_reason=NULL,user_limit=0,workorder_limit=0,ocr_quota=0,request_quota=0," +
                "start_at=NULL,end_at=NULL,auto_renew_enabled=0,auto_renew_plan_id=NULL,next_renew_at=NULL,cancel_auto_renew_at=NOW(),updated_at=NOW() WHERE id=?", subscriptionId);
        Map<String, Object> after = Map.of("subscriptionId", subscriptionId, "status", 4,
                "userLimit", 0, "workorderLimit", 0);
        systemLog.recordOperation("SUBSCRIPTION_CANCEL", "人工取消企业套餐", "enterprise",
                operatorId, operatorName, enterpriseId, String.valueOf(enterprise.get("name")),
                "SUBSCRIPTION", subscriptionId, String.valueOf(subscription.get("planName")), reason,
                "取消套餐", before, after);
    }

    /** 把 SQL 中的三个月度聚合列收敛为稳定 monthUsage 对象，避免页面依赖散落字段。 */
    private void attachMonthUsage(Map<String, Object> row) {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("workorderCount", value(row.remove("monthWorkorderCount")));
        usage.put("requestCount", value(row.remove("monthRequestCount")));
        usage.put("ocrCount", value(row.remove("monthOcrCount")));
        row.put("monthUsage", usage);
    }

    /** 锁定目标企业作为套餐变更的并发入口，同时获取审计所需名称快照。 */
    private Map<String, Object> lockEnterprise(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,name FROM tenant_enterprise WHERE id=? AND deleted=0 FOR UPDATE", id);
        if (rows.isEmpty()) throw new BusinessException(404, "企业不存在");
        return rows.get(0);
    }

    /** 趋势查询前做廉价存在性校验，保证空趋势和不存在企业的语义可区分。 */
    private void ensureEnterprise(Long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM tenant_enterprise WHERE id=? AND deleted=0", Integer.class, id);
        if (count == null || count == 0) throw new BusinessException(404, "企业不存在");
    }

    /** 解析并限制文本长度，避免空原因或超长内容进入审计表。 */
    private String requiredText(Map<String, Object> body, String key, String message) {
        Object raw = body == null ? null : body.get(key);
        if (raw == null || raw.toString().isBlank()) throw new BusinessException(400, message);
        String value = raw.toString().trim();
        if (value.length() > 500) throw new BusinessException(400, "操作原因不能超过 500 个字符");
        return value;
    }

    /** 将请求中的必填标识转换为正长整数，格式错误统一返回业务参数异常。 */
    private long requiredLong(Map<String, Object> body, String key, String message) {
        try {
            long value = Long.parseLong(requiredText(body, key, message));
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (BusinessException exception) { throw exception; }
        catch (Exception exception) { throw new BusinessException(400, message); }
    }

    /** 统一把空聚合值转为数字零。 */
    private Object value(Object value) { return value == null ? BigDecimal.ZERO : value; }

    /** 根据白名单代码构造当前业务日的闭开日期边界。 */
    private RangeRule rangeRule(String code) {
        RangeTemplate template = RANGE_TEMPLATES.get(code);
        if (template == null) throw new BusinessException(400, "时间范围仅支持 7d、15d、30d、3m、6m、1y");
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate start = template.months > 0 ? today.minusMonths(template.months).plusDays(1) : today.minusDays(template.days - 1L);
        if ("WEEK".equals(template.interval)) start = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if ("MONTH".equals(template.interval)) start = start.withDayOfMonth(1);
        return new RangeRule(code, template.interval, start, today.plusDays(1));
    }

    /** 仅返回三种受控 MySQL 分组表达式，不允许客户端字符串参与 SQL 拼接。 */
    private String bucketExpression(String interval) {
        if ("WEEK".equals(interval)) return "DATE_FORMAT(DATE_SUB(stat_date,INTERVAL WEEKDAY(stat_date) DAY),'%Y-%m-%d')";
        if ("MONTH".equals(interval)) return "DATE_FORMAT(stat_date,'%Y-%m')";
        return "DATE_FORMAT(stat_date,'%Y-%m-%d')";
    }

    /** 将外部指标代码映射为日统计宽表列名，映射之外的值拒绝参与排序 SQL。 */
    private String metricColumn(String metric) {
        if ("workorder".equals(metric)) return "processed_workorder_count";
        if ("request".equals(metric)) return "request_count";
        if ("ocr".equals(metric)) return "ocr_count";
        throw new BusinessException(400, "统计指标仅支持 workorder、request、ocr");
    }

    /** 根据已归一化范围生成公共图表标签，周和月范围分别按周一和月初推进。 */
    private List<String> rangeLabels(RangeRule rule) {
        List<String> labels = new ArrayList<>();
        LocalDate cursor = rule.start;
        while (cursor.isBefore(rule.end)) {
            labels.add("MONTH".equals(rule.interval) ? cursor.format(MONTH_LABEL) : cursor.toString());
            cursor = "MONTH".equals(rule.interval) ? cursor.plusMonths(1)
                    : "WEEK".equals(rule.interval) ? cursor.plusWeeks(1) : cursor.plusDays(1);
        }
        return labels;
    }

    /** 建立与监控仪表盘相同的范围和粒度白名单。 */
    private static Map<String, RangeTemplate> rangeTemplates() {
        Map<String, RangeTemplate> ranges = new LinkedHashMap<>();
        ranges.put("7d", new RangeTemplate(7, 0, "DAY"));
        ranges.put("15d", new RangeTemplate(15, 0, "DAY"));
        ranges.put("30d", new RangeTemplate(30, 0, "DAY"));
        ranges.put("3m", new RangeTemplate(0, 3, "WEEK"));
        ranges.put("6m", new RangeTemplate(0, 6, "MONTH"));
        ranges.put("1y", new RangeTemplate(0, 12, "MONTH"));
        return ranges;
    }

    /** 保存静态范围配置的不可变内部值对象。 */
    private static final class RangeTemplate {
        private final int days; private final int months; private final String interval;
        private RangeTemplate(int days, int months, String interval) { this.days = days; this.months = months; this.interval = interval; }
    }

    /** 保存已解析的实际日期边界和图表粒度。 */
    private static final class RangeRule {
        private final String code; private final String interval; private final LocalDate start; private final LocalDate end;
        private RangeRule(String code, String interval, LocalDate start, LocalDate end) { this.code = code; this.interval = interval; this.start = start; this.end = end; }
    }
}
