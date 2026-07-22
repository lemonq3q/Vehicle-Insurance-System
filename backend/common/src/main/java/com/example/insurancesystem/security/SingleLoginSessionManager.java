package com.example.insurancesystem.security;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.utils.RedisCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SingleLoginSessionManager {

    private static final int SESSION_HOURS = 24;

    private final RedisCache redisCache;
    private final String loginKeyPrefix;

    public SingleLoginSessionManager(
            RedisCache redisCache,
            @Value("${security.session.key-prefix:login:}") String loginKeyPrefix) {
        this.redisCache = redisCache;
        this.loginKeyPrefix = normalizePrefix(loginKeyPrefix);
    }

    public void save(Long userId, String sessionId, LoginUser loginUser) {
        loginUser.setSessionId(sessionId);
        redisCache.setCacheObject(key(userId), loginUser, SESSION_HOURS, TimeUnit.HOURS);
    }

    public LoginUser get(Long userId, String sessionId) {
        LoginUser loginUser = redisCache.getCacheObject(key(userId));
        if (loginUser == null || sessionId == null || !sessionId.equals(loginUser.getSessionId())) {
            return null;
        }
        return loginUser;
    }

    public void refresh(Long userId, LoginUser loginUser) {
        redisCache.setCacheObject(key(userId), loginUser, SESSION_HOURS, TimeUnit.HOURS);
    }

    public void remove(Long userId, String sessionId) {
        LoginUser loginUser = get(userId, sessionId);
        if (loginUser != null) {
            redisCache.deleteObject(key(userId));
        }
    }

    private String key(Long userId) {
        return loginKeyPrefix + userId;
    }

    private String normalizePrefix(String prefix) {
        String normalized = prefix == null ? "" : prefix.trim();
        if (normalized.isEmpty()) return "login:";
        return normalized.endsWith(":") ? normalized : normalized + ":";
    }
}
