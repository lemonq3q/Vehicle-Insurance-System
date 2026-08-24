package com.example.insurancesystem.statistics;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 使用共享 Redis Hash 保存企业 API 和 OCR 自然日计数。键不采用应用级前缀，确保车险端与 SaaS 端
 * 的请求汇入同一统计桶；字段为企业 ID，HINCRBY 保证多实例并发递增不丢失。
 */
@Component
public class RedisUsageMetricRecorder implements UsageMetricRecorder {
    private static final Logger log = LoggerFactory.getLogger(RedisUsageMetricRecorder.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String API_KEY = "insurance-statistics:api:day:";
    private static final String OCR_KEY = "insurance-statistics:ocr:day:";
    private static final long ARCHIVE_RETENTION_DAYS = 7L;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 注入项目统一 RedisTemplate。该组件只使用整数 Hash 字段，不依赖应用缓存前缀，
     * 从而允许两个业务后端共同记录同一企业的访问量。
     */
    public RedisUsageMetricRecorder(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void recordApiRequest(long enterpriseId) {
        increment(API_KEY, enterpriseId);
    }

    @Override
    public void recordOcrInvocation(long enterpriseId) {
        increment(OCR_KEY, enterpriseId);
    }

    @Override
    public Map<Long, Long> readApiCounts(LocalDate date) {
        return read(API_KEY + date);
    }

    @Override
    public Map<Long, Long> readOcrCounts(LocalDate date) {
        return read(OCR_KEY + date);
    }

    @Override
    public void retainArchivedDay(LocalDate date) {
        redisTemplate.expire(API_KEY + date, ARCHIVE_RETENTION_DAYS, TimeUnit.DAYS);
        redisTemplate.expire(OCR_KEY + date, ARCHIVE_RETENTION_DAYS, TimeUnit.DAYS);
    }

    /**
     * 高频计数失败不能中断核心业务请求，但会记录企业和指标桶供监控告警；下一次请求仍可继续尝试。
     */
    private void increment(String prefix, long enterpriseId) {
        try {
            redisTemplate.opsForHash().increment(prefix + LocalDate.now(BUSINESS_ZONE), String.valueOf(enterpriseId), 1L);
        } catch (RuntimeException exception) {
            log.error("企业实时统计写入 Redis 失败, keyPrefix={}, enterpriseId={}", prefix, enterpriseId, exception);
        }
    }

    /**
     * 将 Redis 序列化后可能表现为数字或字符串的 Hash 转换为稳定的长整型映射；非法字段直接报错，
     * 防止日终任务把无法解释的源数据静默归档为零。
     */
    private Map<Long, Long> read(String key) {
        Map<Object, Object> source = redisTemplate.opsForHash().entries(key);
        Map<Long, Long> result = new LinkedHashMap<>();
        source.forEach((field, value) -> result.put(Long.valueOf(String.valueOf(field)), Long.valueOf(String.valueOf(value))));
        return result;
    }
}
