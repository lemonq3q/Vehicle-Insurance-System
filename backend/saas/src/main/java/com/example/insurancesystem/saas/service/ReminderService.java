package com.example.insurancesystem.saas.service;

import java.util.List;
import java.util.Map;

/**
 * 门户提醒应用服务，同时承担每日风险扫描和当前企业近期列表查询。
 * 扫描结果以稳定合并键写入企业表，并通过内部接口同步到监控系统。
 */
public interface ReminderService {
    void generateDailyReminders();
    List<Map<String, Object>> recent();
}
