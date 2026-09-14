package com.example.insurancesystem.monitor.service;

import com.example.insurancesystem.handler.exception.BusinessException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 统一系统日志只读查询服务。它对枚举、日期和分页进行严格校验，
 * 每次请求最多执行一条计数 SQL 和一条分页 SQL，按 occurred_at 与 id 倒序保证新日志优先且排序稳定。
 * JSON 快照原样返回，页面仅在用户展开某行时格式化展示，避免后端为列表重复解析。
 */
@Service
public class MonitorSystemLogQueryService {
    private static final Set<String> CATEGORIES = Set.of("OPERATION", "MAINTENANCE", "SYSTEM", "SECURITY", "BUSINESS");
    private static final Set<String> SEVERITIES = Set.of("DEBUG", "INFO", "WARN", "ERROR", "CRITICAL");
    private final NamedParameterJdbcTemplate jdbc;

    public MonitorSystemLogQueryService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * 构建只包含非空筛选的参数化 WHERE 条件。日期区间使用 [startDate,endDate+1)，
     * 不对 occurred_at 列套用 DATE 函数，保留时间索引范围扫描能力。
     */
    public Map<String, Object> page(String category, String severity, String startDate, String endDate,
            int pageNo, int pageSize) {
        pageNo = Math.max(1, pageNo);
        pageSize = pageSize < 1 ? 10 : Math.min(pageSize, 100);
        String normalizedCategory = normalizeEnum(category, CATEGORIES, "审计类别不合法");
        String normalizedSeverity = normalizeEnum(severity, SEVERITIES, "严重等级不合法");
        LocalDate start = parseDate(startDate, "开始日期");
        LocalDate end = parseDate(endDate, "结束日期");
        if (start != null && end != null && start.isAfter(end)) throw new BusinessException(400, "开始日期不能晚于结束日期");

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (normalizedCategory != null) { where.append(" AND log_category=:category"); parameters.addValue("category", normalizedCategory); }
        if (normalizedSeverity != null) { where.append(" AND severity=:severity"); parameters.addValue("severity", normalizedSeverity); }
        if (start != null) { where.append(" AND occurred_at>=:startTime"); parameters.addValue("startTime", Timestamp.valueOf(start.atStartOfDay())); }
        if (end != null) { where.append(" AND occurred_at<:endTime"); parameters.addValue("endTime", Timestamp.valueOf(end.plusDays(1).atStartOfDay())); }

        long total = jdbc.queryForObject("SELECT COUNT(1) FROM monitor_system_log" + where, parameters, Long.class);
        parameters.addValue("offset", (pageNo - 1) * pageSize).addValue("pageSize", pageSize);
        List<Map<String, Object>> list = total == 0 ? List.of() : jdbc.queryForList(
                "SELECT id,log_category logCategory,severity,event_code eventCode,event_name eventName," +
                "source_system sourceSystem,source_module sourceModule,result_status resultStatus," +
                "operator_type operatorType,operator_id operatorId,operator_name_snapshot operatorNameSnapshot," +
                "enterprise_id enterpriseId,enterprise_name_snapshot enterpriseNameSnapshot,target_type targetType," +
                "target_id targetId,target_name_snapshot targetNameSnapshot,operation_reason operationReason,summary," +
                "detail_json detailJson,before_json beforeJson,after_json afterJson,error_code errorCode," +
                "error_message errorMessage,exception_digest exceptionDigest,request_id requestId,trace_id traceId," +
                "job_execution_id jobExecutionId,ip_address ipAddress,user_agent userAgent,occurred_at occurredAt " +
                "FROM monitor_system_log" + where + " ORDER BY occurred_at DESC,id DESC LIMIT :offset,:pageSize", parameters);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list); result.put("pageNo", pageNo); result.put("pageSize", pageSize); result.put("total", total);
        return result;
    }

    /** 空字符串表示不限制；其他值转大写后必须命中服务端白名单。 */
    private String normalizeEnum(String value, Set<String> allowed, String message) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase();
        if (!allowed.contains(normalized)) throw new BusinessException(400, message);
        return normalized;
    }

    /** 将可选 yyyy-MM-dd 参数转为业务日，格式错误时返回明确的参数提示。 */
    private LocalDate parseDate(String value, String fieldName) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value.trim()); }
        catch (Exception exception) { throw new BusinessException(400, fieldName + "格式应为 yyyy-MM-dd"); }
    }
}
