package com.example.insurancesystem.statistics;

import java.time.LocalDate;
import java.util.Map;

/**
 * 定义企业高频用量指标的实时记录和日桶读取能力。业务入口只提交可信企业 ID，
 * Redis 键格式、统计时区、原子递增及异常降级均由实现统一管理，避免计数细节侵入业务服务。
 */
public interface UsageMetricRecorder {

    /**
     * 将一次已认证企业业务 API 请求计入当前北京时间自然日。
     *
     * @param enterpriseId 当前认证主体所属企业 ID
     */
    void recordApiRequest(long enterpriseId);

    /**
     * 将一次实际发起的 OCR 供应商调用计入当前北京时间自然日。
     *
     * @param enterpriseId 发起 OCR 调用的企业 ID
     */
    void recordOcrInvocation(long enterpriseId);

    /**
     * 读取指定自然日所有企业的 API 绝对计数，用于日终幂等覆盖数据库。
     *
     * @param date 北京时间统计日期
     * @return 企业 ID 到累计次数的映射；Redis 不可用时抛出异常，阻止错误终算
     */
    Map<Long, Long> readApiCounts(LocalDate date);

    /**
     * 读取指定自然日所有企业的 OCR 绝对计数，用于日终幂等覆盖数据库。
     *
     * @param date 北京时间统计日期
     * @return 企业 ID 到累计次数的映射
     */
    Map<Long, Long> readOcrCounts(LocalDate date);

    /**
     * 日终数据库事务成功后为源 Redis 日桶设置保留期。保留期间可重复归档和人工对账，
     * 不立即删除可避免数据库提交结果不明确时无法恢复计数。
     *
     * @param date 已完成归档的自然日
     */
    void retainArchivedDay(LocalDate date);
}
