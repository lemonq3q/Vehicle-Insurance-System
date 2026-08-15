package com.example.insurancesystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 统一管理 Redis 键的应用级命名空间，防止多个部署实例共用 Redis 时出现键冲突。
 * 前缀来自 {@code spring.redis.key-prefix}，构造时会去除多余分隔符；业务代码通过
 * {@link #key(String)} 生成最终键名，因此空前缀环境仍可保持原有键格式。
 */
@Component
public class RedisKeyPrefixManager {
    private final String prefix;

    /**
     * 读取应用级 Redis 前缀并在构造阶段规范化，后续业务生成键时无需重复处理分隔符。
     */
    public RedisKeyPrefixManager(@Value("${spring.redis.key-prefix:}") String prefix) {
        this.prefix = normalize(prefix);
    }

    /**
     * 为逻辑键添加应用命名空间；空逻辑键返回纯前缀，便于调用方构造命名空间级操作。
     */
    public String key(String logicalKey) {
        if (logicalKey == null || logicalKey.isEmpty()) {
            return prefix;
        }
        return prefix + logicalKey;
    }

    /**
     * 为扫描或批量删除使用的逻辑模式添加相同命名空间，确保模式不会匹配其他应用的键。
     */
    public String pattern(String logicalPattern) {
        return key(logicalPattern);
    }

    /**
     * 去除配置两端空白，并保证非空前缀只带一个语义上的尾部分隔冒号。
     */
    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String normalized = value.trim();
        return normalized.endsWith(":") ? normalized : normalized + ":";
    }
}
