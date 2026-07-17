package com.example.insurancesystem.security;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.utils.RedisCache;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SingleLoginSessionManager {

    private static final int SESSION_HOURS = 24;
    private static final String LOGIN_KEY_PREFIX = "login:";

    private final RedisCache redisCache;

    public SingleLoginSessionManager(RedisCache redisCache) {
        this.redisCache = redisCache;
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
        return LOGIN_KEY_PREFIX + userId;
    }
}
