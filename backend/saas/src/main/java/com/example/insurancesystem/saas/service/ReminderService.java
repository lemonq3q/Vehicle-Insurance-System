package com.example.insurancesystem.saas.service;

import java.util.List;
import java.util.Map;

/**
 * 门户提醒应用服务，同时承担每日风险扫描和当前企业近期列表查询。
 * 扫描结果以稳定合并键写入企业表，并通过内部接口同步到监控系统。
 */
public interface ReminderService {
    /**
     * 每日维护先分批失效，再新增、恢复或升降提醒阶段，最后补偿监控同步。
     * 同轮复用当前业务快照，不按年龄失效，不产生资金变更；降级保留阅读与处理历史。
     */
    void generateDailyReminders();
    List<Map<String, Object>> recent();
}
