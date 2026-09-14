package com.example.insurancesystem.monitor.service;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.monitor.domain.ReminderMergeRequest;
import com.example.insurancesystem.monitor.mapper.MonitorReminderMapper;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 执行监控提醒的插入或阶段升级。低阶段和重复阶段均不改写记录，因此协调任务可以安全重试；
 * 更高阶段更新将客服已处理状态恢复为待处理，防止早期风险的处理结论掩盖新的紧急状态。
 */
@Service
public class MonitorReminderService {
    private final MonitorReminderMapper mapper;
    private final MonitorSystemLogService systemLog;

    public MonitorReminderService(MonitorReminderMapper mapper, MonitorSystemLogService systemLog) {
        this.mapper = mapper;
        this.systemLog = systemLog;
    }

    /**
     * @param request SaaS 已完成模板渲染的提醒快照
     * @return CREATED、UPDATED 或 IGNORED，供调用方记录维护结果
     */
    @Transactional
    public String merge(ReminderMergeRequest request) {
        validate(request);
        if (mapper.countEnabledType(request.reminderType) == 0) {
            throw new BusinessException(400, "提醒类型尚未注册或已停用: " + request.reminderType);
        }
        Map<String, Object> existing = mapper.lock(request.enterpriseId, request.reminderKey);
        if (existing == null) {
            mapper.insert(request);
            return "CREATED";
        }
        int currentLevel = ((Number) existing.get("stageLevel")).intValue();
        if (request.stageLevel <= currentLevel) return "IGNORED";
        return mapper.upgrade(((Number) existing.get("id")).longValue(), request) == 1 ? "UPDATED" : "IGNORED";
    }

    /**
     * 幂等删除 SaaS 已确认风险恢复的自动续费套餐下架提醒。稳定键必须带该类型前缀，Mapper 再按
     * reminder_type 限制一次，形成双重白名单，避免内部凭证泄露时扩大删除范围。
     */
    @Transactional
    public String deleteAutoRenewPlanUnavailable(Long enterpriseId, String reminderKey) {
        if (enterpriseId == null || enterpriseId <= 0 || reminderKey == null
                || !reminderKey.startsWith("AUTO_RENEW_PLAN_UNAVAILABLE:subscription-") || reminderKey.length() > 200) {
            throw new BusinessException(400, "提醒清理参数不合法");
        }
        return mapper.deleteAutoRenewPlanUnavailable(enterpriseId, reminderKey) == 1 ? "DELETED" : "IGNORED";
    }

    /**
     * 校验分页和枚举参数后读取提醒列表。总数与数据各执行一条针对性 SQL，不产生逐行查询；
     * 返回固定分页外壳供前端筛选、翻页和处理后原位刷新。
     */
    public Map<String, Object> page(String severity, String categoryCode, String typeCodes, Long enterpriseId,
            Integer processStatus, int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) throw new BusinessException(400, "分页参数不合法");
        if (severity != null && !severity.isBlank() && !List.of("NOTICE", "WARNING", "CRITICAL").contains(severity))
            throw new BusinessException(400, "严重等级不合法");
        if (processStatus != null && processStatus != 0 && processStatus != 1)
            throw new BusinessException(400, "处理状态不合法");
        if (typeCodes != null && typeCodes.length() > 2000) throw new BusinessException(400, "提醒类型筛选参数过长");
        long total = mapper.countPage(severity, categoryCode, typeCodes, enterpriseId, processStatus);
        List<Map<String, Object>> list = total == 0 ? List.of() : mapper.findPage(severity, categoryCode, typeCodes,
                enterpriseId, processStatus, (pageNo - 1) * pageSize, pageSize);
        list.forEach(this::normalizeReminderRow);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list); result.put("pageNo", pageNo); result.put("pageSize", pageSize); result.put("total", total);
        return result;
    }

    /** 将单次字典查询按类别组装；企业不在首屏预加载，由用户关键词按需搜索。 */
    public Map<String, Object> filterOptions() {
        Map<String, Map<String, Object>> categoryMap = new LinkedHashMap<>();
        for (Map<String, Object> row : mapper.findFilterOptions()) {
            String optionKind = text(row, "optionKind", "option_kind");
            String code = text(row, "categoryCode", "category_code");
            String categoryName = text(row, "categoryName", "category_name");
            String typeCode = text(row, "typeCode", "type_code");
            String typeName = text(row, "typeName", "type_name");
            if (!"TYPE".equals(optionKind) || code == null || categoryName == null || typeCode == null || typeName == null) {
                throw new IllegalStateException("提醒筛选字典查询返回了不完整的数据");
            }
            Map<String, Object> category = categoryMap.computeIfAbsent(code, ignored -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("code", code); value.put("name", categoryName); value.put("types", new ArrayList<>());
                return value;
            });
            ((List<Map<String, Object>>) category.get("types")).add(Map.of("code", typeCode, "name", typeName));
        }
        return Map.of("categories", new ArrayList<>(categoryMap.values()));
    }

    /**
     * 用户输入至少一个非空关键词后才查询企业，名称和编码均支持模糊匹配；结果由 SQL 固定为 20 条，
     * 页面必须选择明确企业 ID，输入但未选择的文本不会参与提醒筛选。
     */
    public List<Map<String, Object>> searchEnterprises(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isEmpty()) return List.of();
        if (normalized.length() > 100) throw new BusinessException(400, "企业搜索关键词过长");
        return mapper.searchEnterprises(normalized);
    }

    /**
     * MyBatis 对普通列可按全局配置转换驼峰，但 UNION 结果的计算列在不同驱动版本中可能保留原始别名。
     * 读取时同时兼容两种键名，避免筛选选项因 JDBC 元数据差异再次出现空值。
     */
    private Object value(Map<String, Object> row, String camelKey, String underscoreKey) {
        Object value = row.get(camelKey);
        return value != null ? value : row.get(underscoreKey);
    }

    /** 将查询选项值转换为非空字符串；SQL NULL 保持 null，供调用方执行完整性判断。 */
    private String text(Map<String, Object> row, String camelKey, String underscoreKey) {
        Object value = value(row, camelKey, underscoreKey);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 在事务锁内将待处理提醒标记为已处理。调用方必须提交页面所见 revision；若提醒已处理或已升级，
     * 拒绝覆盖并要求刷新，防止人工结论作用于过期风险阶段。
     */
    @Transactional
    public void markProcessed(Long id, Integer revision, Long userId, String operatorName, String remark) {
        if (id == null || id <= 0 || revision == null || revision <= 0) throw new BusinessException(400, "提醒处理参数不完整");
        Map<String, Object> current = mapper.lockForProcessing(id);
        if (current == null) throw new BusinessException(404, "提醒不存在");
        if (number(current, "processStatus", "process_status").intValue() == 1) throw new BusinessException(409, "该提醒已被处理，请刷新列表");
        if (number(current, "revision", "revision").intValue() != revision) throw new BusinessException(409, "提醒状态已更新，请刷新后重新确认");
        if (mapper.markProcessed(id, revision, userId, remark == null ? null : remark.trim()) != 1)
            throw new BusinessException(409, "提醒状态已变化，请刷新列表");
        String normalizedRemark = remark == null || remark.isBlank() ? null : remark.trim();
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("processStatus", 0); before.put("revision", revision); before.put("processRemark", value(current, "processRemark", "process_remark"));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("processStatus", 1); after.put("revision", revision + 1); after.put("processRemark", normalizedRemark);
        Long enterpriseId = number(current, "enterpriseId", "enterprise_id").longValue();
        systemLog.recordOperation("REMINDER_PROCESSED", "人工处理系统提醒", "reminder",
                userId, operatorName, enterpriseId, String.valueOf(value(current, "enterpriseNameSnapshot", "enterprise_name_snapshot")),
                "REMINDER", id, String.valueOf(value(current, "title", "title")), normalizedRemark,
                "将提醒“" + value(current, "title", "title") + "”标记为已处理", before, after);
    }

    /**
     * 将已处理提醒人工恢复为待处理。调用方提交页面所见版本，事务内锁定并校验当前确为已处理状态；
     * 恢复时保留最近一次处理快照、清空当前处理信息并记录审计日志，供误操作纠正和后续继续跟进。
     */
    @Transactional
    public void restoreUnprocessed(Long id, Integer revision, Long userId, String operatorName) {
        if (id == null || id <= 0 || revision == null || revision <= 0) throw new BusinessException(400, "提醒恢复参数不完整");
        Map<String, Object> current = mapper.lockForProcessing(id);
        if (current == null) throw new BusinessException(404, "提醒不存在");
        if (number(current, "processStatus", "process_status").intValue() == 0) throw new BusinessException(409, "该提醒已是待处理状态，请刷新列表");
        if (number(current, "revision", "revision").intValue() != revision) throw new BusinessException(409, "提醒状态已更新，请刷新后重新确认");
        if (mapper.restoreUnprocessed(id, revision) != 1) throw new BusinessException(409, "提醒状态已变化，请刷新列表");
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("processStatus", 1); before.put("revision", revision);
        before.put("processRemark", value(current, "processRemark", "process_remark"));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("processStatus", 0); after.put("revision", revision + 1); after.put("processRemark", null);
        Long enterpriseId = number(current, "enterpriseId", "enterprise_id").longValue();
        String title = String.valueOf(value(current, "title", "title"));
        systemLog.recordOperation("REMINDER_REOPENED", "恢复系统提醒为待处理", "reminder",
                userId, operatorName, enterpriseId, String.valueOf(value(current, "enterpriseNameSnapshot", "enterprise_name_snapshot")),
                "REMINDER", id, title, null, "将提醒“" + title + "”恢复为待处理", before, after);
    }

    /**
     * 将提醒查询中的下划线列名统一为前端契约使用的驼峰字段。
     * 部分 JDBC/MyBatis 组合不会对 Map 结果执行驼峰转换；集中兼容可以同时保证企业跳转、处理按钮、
     * 时间展示和处理提交版本号正确，不要求页面感知数据库驱动差异。
     */
    private void normalizeReminderRow(Map<String, Object> row) {
        String[][] fields = {
                {"enterpriseId", "enterprise_id"}, {"enterpriseNameSnapshot", "enterprise_name_snapshot"},
                {"enterpriseCode", "enterprise_code"}, {"enterpriseContactName", "enterprise_contact_name"},
                {"enterpriseContactPhone", "enterprise_contact_phone"},
                {"reminderType", "reminder_type"}, {"typeName", "type_name"},
                {"categoryCode", "category_code"}, {"categoryName", "category_name"},
                {"reminderStage", "reminder_stage"}, {"stageLevel", "stage_level"},
                {"triggerCount", "trigger_count"}, {"firstTriggeredAt", "first_triggered_at"},
                {"lastTriggeredAt", "last_triggered_at"}, {"processStatus", "process_status"},
                {"processedAt", "processed_at"}, {"processedBy", "processed_by"},
                {"processRemark", "process_remark"}
        };
        for (String[] field : fields) {
            Object fieldValue = value(row, field[0], field[1]);
            if (fieldValue != null) row.put(field[0], fieldValue);
            row.remove(field[1]);
        }
    }

    /** 读取必须存在的数值列，并在 Mapper 契约异常时给出明确服务端错误。 */
    private Number number(Map<String, Object> row, String camelKey, String underscoreKey) {
        Object fieldValue = value(row, camelKey, underscoreKey);
        if (!(fieldValue instanceof Number)) throw new IllegalStateException("提醒查询缺少数值字段: " + camelKey);
        return (Number) fieldValue;
    }

    /** 对跨服务输入执行最小但完整的边界校验，避免缺失合并键或展示文案的数据进入客服待办。 */
    private void validate(ReminderMergeRequest request) {
        if (request == null || request.enterpriseId == null || request.enterpriseId <= 0
                || blank(request.enterpriseName) || blank(request.reminderType) || blank(request.reminderKey)
                || blank(request.reminderStage) || request.stageLevel == null || request.stageLevel < 0
                || blank(request.severity) || blank(request.title) || blank(request.content)
                || request.triggeredAt == null) {
            throw new BusinessException(400, "提醒合并参数不完整");
        }
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
