package com.example.insurancesystem.saas.service;

import com.example.insurancesystem.handler.exception.BusinessException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 官网游客提交的来源验证与频率保护组件。它只信任服务端配置的站点白名单，并以来源站点和客户端地址
 * 形成不可逆限流键；Redis 提供多实例一致性，本地时间窗在 Redis 故障时继续阻止单实例上的连续提交。
 */
@Component
public class VisitorLeadSubmissionGuard {
    private final StringRedisTemplate redisTemplate;
    private final Set<String> allowedOrigins;
    private final long rateLimitSeconds;
    private final boolean trustProxyHeaders;
    private final ConcurrentHashMap<String, Long> localDeadlines = new ConcurrentHashMap<>();

    public VisitorLeadSubmissionGuard(StringRedisTemplate redisTemplate,
            @Value("${saas.visitor-lead.allowed-origins}") String allowedOrigins,
            @Value("${saas.visitor-lead.rate-limit-seconds:60}") long rateLimitSeconds,
            @Value("${saas.visitor-lead.trust-proxy-headers:false}") boolean trustProxyHeaders) {
        this.redisTemplate = redisTemplate;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .filter(value -> !value.isBlank()).map(this::normalizeOrigin).collect(Collectors.toUnmodifiableSet());
        this.rateLimitSeconds = Math.max(1, rateLimitSeconds);
        this.trustProxyHeaders = trustProxyHeaders;
    }

    /**
     * 校验 Origin，缺失时从 Referer 提取站点来源；校验通过后按“站点+客户端地址”占用限流窗口。
     * 来源缺失、格式非法或不在白名单中均拒绝；重复调用返回 429，提示用户等待后再提交。
     */
    public void verifyAndAcquire(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) origin = originFromReferer(request.getHeader("Referer"));
        origin = normalizeOrigin(origin);
        if (!allowedOrigins.contains(origin)) throw new BusinessException(403, "请求来源不受信任");
        String sourceKey = sha256(origin + '|' + clientAddress(request));
        String redisKey = "portal:visitor-lead:rate:" + sourceKey;
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", Duration.ofSeconds(rateLimitSeconds));
            if (!Boolean.TRUE.equals(acquired)) throw new BusinessException(429, "提交过于频繁，请稍后再试");
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException redisUnavailable) {
            acquireLocal(sourceKey);
        }
    }

    /** Redis 不可用时以原子 Map 更新实现同样时间窗，并顺带清除已经过期的来源键。 */
    private void acquireLocal(String key) {
        long now = System.currentTimeMillis();
        long deadline = now + rateLimitSeconds * 1000L;
        Long existing = localDeadlines.compute(key, (ignored, oldDeadline) -> oldDeadline == null || oldDeadline <= now ? deadline : oldDeadline);
        if (existing != null && existing > now && existing != deadline) throw new BusinessException(429, "提交过于频繁，请稍后再试");
        if (localDeadlines.size() > 10000) localDeadlines.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    /** 仅在部署显式信任反向代理时读取代理头，避免直连客户端伪造地址绕过限流。 */
    private String clientAddress(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",", 2)[0].trim();
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) return realIp.trim();
        }
        return String.valueOf(request.getRemoteAddr());
    }

    /** 从完整 Referer 中仅提取 scheme、host 和有效端口，避免路径和查询串影响来源判断。 */
    private String originFromReferer(String referer) {
        if (referer == null || referer.isBlank()) return "";
        try {
            URI uri = URI.create(referer.trim());
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        } catch (RuntimeException invalid) {
            return "";
        }
    }

    /** 将来源统一为小写且去除末尾斜杠，保证配置与请求使用相同比较口径。 */
    private String normalizeOrigin(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    /** 对限流来源生成 SHA-256 摘要，Redis 键和进程内缓存均不保存可读 IP。 */
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) hex.append(String.format("%02x", item));
            return hex.toString();
        } catch (Exception impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
