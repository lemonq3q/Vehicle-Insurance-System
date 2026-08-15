package com.example.insurancesystem.security;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.utils.RedisCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
/**
 * 使用 Redis 维护车险端单账号单会话状态。每个用户只保留一个带 sessionId 的 LoginUser，
 * 新登录覆盖旧记录，因此旧 JWT 即使尚未过期也会因 jti 不匹配而失效。
 */
public class SingleLoginSessionManager {

    private static final int SESSION_HOURS = 24;

    private final RedisCache redisCache;
    private final String loginKeyPrefix;

    /**
     * 注入 Redis 访问器并规范登录键前缀，允许不同应用在共享 Redis 中隔离会话命名空间。
     */
    public SingleLoginSessionManager(
            RedisCache redisCache,
            @Value("${security.session.key-prefix:login:}") String loginKeyPrefix) {
        this.redisCache = redisCache;
        this.loginKeyPrefix = normalizePrefix(loginKeyPrefix);
    }

    /**
     * 将 JWT 的唯一会话 ID 写入登录用户并缓存 24 小时；同一用户再次保存会覆盖此前会话。
     */
    public void save(Long userId, String sessionId, LoginUser loginUser) {
        loginUser.setSessionId(sessionId);
        redisCache.setCacheObject(key(userId), loginUser, SESSION_HOURS, TimeUnit.HOURS);
    }

    /**
     * 读取用户当前会话，只有请求 JWT 的 sessionId 与 Redis 中记录完全一致时才返回登录主体。
     */
    public LoginUser get(Long userId, String sessionId) {
        LoginUser loginUser = redisCache.getCacheObject(key(userId));
        if (loginUser == null || sessionId == null || !sessionId.equals(loginUser.getSessionId())) {
            return null;
        }
        return loginUser;
    }

    /**
     * 已认证请求通过后续期 Redis 会话，使持续活跃用户不会因固定缓存时间被强制下线。
     */
    public void refresh(Long userId, LoginUser loginUser) {
        redisCache.setCacheObject(key(userId), loginUser, SESSION_HOURS, TimeUnit.HOURS);
    }

    /**
     * 仅当待退出 sessionId 仍是用户当前会话时删除缓存，避免旧设备退出误删新设备登录状态。
     */
    public void remove(Long userId, String sessionId) {
        LoginUser loginUser = get(userId, sessionId);
        if (loginUser != null) {
            redisCache.deleteObject(key(userId));
        }
    }

    /**
     * 组合规范化前缀和用户 ID，形成每个用户唯一的 Redis 登录键。
     */
    private String key(Long userId) {
        return loginKeyPrefix + userId;
    }

    /**
     * 清理配置空白并确保前缀以冒号结尾；未配置时使用 login:，避免键名直接拼接产生歧义。
     */
    private String normalizePrefix(String prefix) {
        String normalized = prefix == null ? "" : prefix.trim();
        if (normalized.isEmpty()) return "login:";
        return normalized.endsWith(":") ? normalized : normalized + ":";
    }
}
