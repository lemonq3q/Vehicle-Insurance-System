package com.example.insurancesystem.utils;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 OSS 临时地址缓存优先和生命周期配置边界，不连接真实 Redis 或阿里云 OSS。
 */
class OSSUtilTemporaryUrlTest {

    /** Redis 中存在仍有效地址时直接返回缓存，不触发新的 OSS 签名申请。 */
    @Test
    void shouldReuseConfiguredRedisCache() {
        RedisCache redisCache = mock(RedisCache.class);
        OSSUtil ossUtil = configuredUtil(redisCache, 60, 55);
        when(redisCache.getCacheObject("oss:insurance/templates/upstream.xlsx"))
                .thenReturn("https://signed.example/template.xlsx");

        assertEquals("https://signed.example/template.xlsx",
                ossUtil.getTmpUrl("insurance/templates/upstream.xlsx"));
        verify(redisCache).getCacheObject("oss:insurance/templates/upstream.xlsx");
    }

    /** Redis 缓存期不得等于或超过 OSS 签名期，否则可能把已过期地址继续返回给浏览器。 */
    @Test
    void shouldRejectUnsafeExpirationConfiguration() {
        OSSUtil ossUtil = configuredUtil(mock(RedisCache.class), 60, 60);
        assertThrows(IllegalStateException.class,
                () -> ossUtil.getTmpUrl("insurance/templates/upstream.xlsx"));
    }

    /** 通过 Spring 测试工具注入与生产配置等价的字段，保持单元测试不启动应用上下文。 */
    private OSSUtil configuredUtil(RedisCache redisCache, long urlMinutes, int cacheMinutes) {
        OSSUtil ossUtil = new OSSUtil();
        ReflectionTestUtils.setField(ossUtil, "redisCache", redisCache);
        ReflectionTestUtils.setField(ossUtil, "temporaryUrlExpireMinutes", urlMinutes);
        ReflectionTestUtils.setField(ossUtil, "temporaryUrlCacheMinutes", cacheMinutes);
        return ossUtil;
    }
}
