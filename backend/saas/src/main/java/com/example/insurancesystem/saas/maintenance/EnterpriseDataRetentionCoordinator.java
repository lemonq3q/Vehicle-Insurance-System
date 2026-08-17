package com.example.insurancesystem.saas.maintenance;

import com.example.insurancesystem.saas.config.EnterpriseDataRetentionProperties;
import com.example.insurancesystem.saas.integration.client.InsuranceEnterpriseDataClient;
import com.example.insurancesystem.saas.mapper.EnterpriseDataRetentionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 编排超过套餐结束保留期的企业车险资料清理。
 * 本组件只负责基于 SaaS 当前订阅状态选择企业，再逐个调用车险内部接口；实际车险表和 OSS 删除边界
 * 完全由车险后端控制。单个企业失败不会阻断其他企业清理，但本轮结束后会抛出汇总异常，令 C 将任务
 * 标记为失败并保留下一维护周期重新执行的机会。
 */
@Service
public class EnterpriseDataRetentionCoordinator {
    private static final Logger log = LoggerFactory.getLogger(EnterpriseDataRetentionCoordinator.class);

    private final EnterpriseDataRetentionMapper mapper;
    private final InsuranceEnterpriseDataClient client;
    private final EnterpriseDataRetentionProperties properties;

    /**
     * @param mapper 查询最近套餐结束时间超过阈值的企业
     * @param client 调用车险系统内部物理清理接口
     * @param properties 资料保留天数配置，默认九十天
     */
    public EnterpriseDataRetentionCoordinator(
            EnterpriseDataRetentionMapper mapper,
            InsuranceEnterpriseDataClient client,
            EnterpriseDataRetentionProperties properties) {
        this.mapper = mapper;
        this.client = client;
        this.properties = properties;
    }

    /**
     * 以任务真正执行时刻减去 retentionDays 作为截止时间，清理所有 end_at 不晚于该时刻的企业。
     * 重复维护会再次选中已经清空但仍未续订的企业，车险接口将返回零删除结果；这种无状态筛选避免为了
     * 记录清理标记而侵入 SaaS 订阅表。失败企业被收集，其他企业继续执行，最后通过异常暴露失败数量。
     */
    public void purgeExpiredEnterpriseData() {
        int retentionDays = Math.max(1, properties.getRetentionDays());
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<Long> enterpriseIds = mapper.findEnterpriseIdsPastRetention(cutoff);
        List<Long> failures = new ArrayList<>();
        for (Long enterpriseId : enterpriseIds) {
            try {
                client.purgeEnterprise(enterpriseId);
                log.info("Purged insurance business data for enterprise {} past {}-day retention",
                        enterpriseId, retentionDays);
            } catch (Exception exception) {
                failures.add(enterpriseId);
                log.error("Failed to purge insurance business data for enterprise {}", enterpriseId, exception);
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("企业车险资料清理失败，enterpriseIds=" + failures);
        }
    }
}
