package com.example.insurancesystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Adds the application namespace to every Redis key. */
@Component
public class RedisKeyPrefixManager {
    private final String prefix;

    public RedisKeyPrefixManager(@Value("${spring.redis.key-prefix:}") String prefix) {
        this.prefix = normalize(prefix);
    }

    public String key(String logicalKey) {
        if (logicalKey == null || logicalKey.isEmpty()) {
            return prefix;
        }
        return prefix + logicalKey;
    }

    public String pattern(String logicalPattern) {
        return key(logicalPattern);
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String normalized = value.trim();
        return normalized.endsWith(":") ? normalized : normalized + ":";
    }
}
