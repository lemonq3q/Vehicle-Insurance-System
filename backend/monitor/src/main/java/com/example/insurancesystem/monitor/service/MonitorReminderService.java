package com.example.insurancesystem.monitor.service;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.monitor.domain.ReminderMergeRequest;
import com.example.insurancesystem.monitor.mapper.MonitorReminderMapper;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 执行监控提醒的插入或阶段升级。低阶段和重复阶段均不改写记录，因此协调任务可以安全重试；
 * 更高阶段更新将客服已处理状态恢复为待处理，防止早期风险的处理结论掩盖新的紧急状态。
 */
@Service
public class MonitorReminderService {
    private final MonitorReminderMapper mapper;

    public MonitorReminderService(MonitorReminderMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @param request SaaS 已完成模板渲染的提醒快照
     * @return CREATED、UPDATED 或 IGNORED，供调用方记录维护结果
     */
    @Transactional
    public String merge(ReminderMergeRequest request) {
        validate(request);
        Map<String, Object> existing = mapper.lock(request.enterpriseId, request.reminderKey);
        if (existing == null) {
            mapper.insert(request);
            return "CREATED";
        }
        int currentLevel = ((Number) existing.get("stageLevel")).intValue();
        if (request.stageLevel <= currentLevel) return "IGNORED";
        return mapper.upgrade(((Number) existing.get("id")).longValue(), request) == 1 ? "UPDATED" : "IGNORED";
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
