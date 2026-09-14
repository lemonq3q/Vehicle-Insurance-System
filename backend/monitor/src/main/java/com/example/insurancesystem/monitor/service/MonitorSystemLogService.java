package com.example.insurancesystem.monitor.service;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import javax.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 监控平台统一系统事件日志写入服务。当前只对已落地的人工操作提供同事务写入，
 * 从当前 HTTP 请求中收集 requestId、IP 和 User-Agent，业务服务只需提供结构化事件信息。
 * 后续维护任务接入时应另增系统事件方法，不应伪造人工操作人。
 */
@Service
public class MonitorSystemLogService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public MonitorSystemLogService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * 在调用方当前事务内写入一条成功的监控用户操作日志。
     * before/after 只应包含追溯所需的业务字段，严禁传入密码、Token、证件号或银行卡等敏感值。
     * JSON 序列化或日志写入失败会中断业务事务，避免出现操作成功但无审计记录的状态。
     */
    public void recordOperation(String eventCode, String eventName, String sourceModule,
            Long operatorId, String operatorName, Long enterpriseId, String enterpriseName,
            String targetType, Object targetId, String targetName, String reason, String summary,
            Object before, Object after) {
        RequestMetadata request = requestMetadata();
        jdbc.update("INSERT INTO monitor_system_log(log_category,severity,event_code,event_name,source_system,source_module," +
                        "result_status,operator_type,operator_id,operator_name_snapshot,enterprise_id,enterprise_name_snapshot," +
                        "target_type,target_id,target_name_snapshot,operation_reason,summary,before_json,after_json,request_id," +
                        "ip_address,user_agent,occurred_at,created_at) VALUES('OPERATION','INFO',?,?,'MONITOR',?,'SUCCESS'," +
                        "'MONITOR_USER',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW())",
                eventCode, eventName, sourceModule, operatorId, normalize(operatorName), enterpriseId,
                normalize(enterpriseName), targetType, targetId == null ? null : String.valueOf(targetId),
                normalize(targetName), normalize(reason), summary, json(before), json(after), request.requestId,
                request.ipAddress, request.userAgent, Timestamp.valueOf(LocalDateTime.now()));
    }

    /** 将非空快照序列化为 JSON；序列化失败视为审计失败并回滚人工操作。 */
    private String json(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new BusinessException(500, "系统日志快照生成失败"); }
    }

    /** 从当前同步 HTTP 请求读取追踪信息，非 HTTP 调用保持空值以便未来任务事件复用。 */
    private RequestMetadata requestMetadata() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes))
            return new RequestMetadata(null, null, null);
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        return new RequestMetadata(limit(request.getHeader("X-Request-Id"), 100), limit(ip, 64),
                limit(request.getHeader("User-Agent"), 500));
    }

    /** 空白快照转为 null，避免日志查询区分空字符串。 */
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    /** 限制来自请求头的不可信文本长度，避免超过数据库列宽。 */
    private String limit(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    /** 保存当前请求中已清洗的审计元数据。 */
    private static final class RequestMetadata {
        private final String requestId; private final String ipAddress; private final String userAgent;
        private RequestMetadata(String requestId, String ipAddress, String userAgent) {
            this.requestId = requestId; this.ipAddress = ipAddress; this.userAgent = userAgent;
        }
    }
}
