package com.example.insurancesystem.security.wechat;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.utils.RedisCache;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 编排两端独立 Redis 命名空间下的微信授权事务，有效期五分钟且成功校验后仅可消费一次。
 * challenge 绑定原浏览器，绑定事务额外关联发起用户和 sessionId；不缓存微信令牌或头像。
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "insurance.wechat.account-enabled", havingValue = "true")
public class WechatFlowService {
    private final WechatProperties config;
    private final RedisCache cache;
    private final WechatClient client;
    private final WechatAccountService accounts;

    /** 注入授权配置、既有缓存与账号业务服务，网络校验不置于数据库事务中。 */
    public WechatFlowService(WechatProperties config, RedisCache cache, WechatClient client, WechatAccountService accounts) {
        this.config = config; this.cache = cache; this.client = client; this.accounts = accounts;
    }

    /**
     * 生成扫码参数；绑定只读取当前安全上下文，申请次数按浏览器挑战限制，阻止重复请求堆积。
     * @param body challenge 为随机 verifier 的 SHA-256 小写十六进制
     * @param binding 是否为登录态下首次绑定，不能由浏览器更改服务端接口用途
     * @return 公开 AppID、固定前端回调、随机 state 和有效期，无应用密钥
     */
    public Map<String, Object> start(Map<String, Object> body, boolean binding) {
        requireConfigured();
        /* 限制实际连接来源的每分钟申请数；不信任外部随意提供的转发头。 */
        org.springframework.web.context.request.RequestAttributes attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes) {
            String address = ((org.springframework.web.context.request.ServletRequestAttributes) attributes).getRequest().getRemoteAddr();
            if (cache.incrementCounter("wechat:rate:" + digest(address), 60) > 30) throw new BusinessException(429, "二维码申请过于频繁，请稍后重试");
        }
        String challenge = text(body, "challenge", 64);
        if (!challenge.matches("[0-9a-f]{64}")) throw new BusinessException(400, "浏览器授权凭证不正确");
        if (!cache.putIfAbsent("wechat:cooldown:" + challenge, "1", 2)) throw new BusinessException(429, "请稍后刷新二维码");
        String state = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("challenge", challenge); pending.put("binding", binding);
        if (binding) {
            LoginUser user = principal();
            pending.put("userId", user.getUser().getId()); pending.put("sessionId", user.getSessionId());
        }
        cache.setCacheObject("wechat:state:" + state, pending, 300, TimeUnit.SECONDS);
        return Map.of("appId", config.getAppId(), "redirectUri", config.getCallbackUri(), "state", state, "expiresInSeconds", 300);
    }

    /**
     * 校验原浏览器、用途及绑定会话后消费 state，再交换微信身份；网络失败需重新扫码。
     * @param body code/state/verifier，前端不提交 openid 或用户 ID
     * @param binding 服务端路由决定的用途
     * @param allowRegistration 仅门户登录允许创建新个人账号
     * @return 完成身份验证的共享用户 ID，由调用端按原权限流程签发 JWT
     */
    public Long complete(Map<String, Object> body, boolean binding, boolean allowRegistration) {
        requireConfigured();
        String state = text(body, "state", 32), verifier = text(body, "verifier", 64), code = text(body, "code", 512);
        if (!state.matches("[0-9a-f]{32}") || !verifier.matches("[0-9a-f]{64}")) throw new BusinessException(400, "微信授权参数不正确");
        String key = "wechat:state:" + state;
        Map<String, Object> pending = cache.getCacheObject(key);
        if (pending == null) throw new BusinessException(400, "微信授权已过期或已使用，请重新扫码");
        if (!Boolean.valueOf(binding).equals(pending.get("binding")) || !digest(verifier).equals(pending.get("challenge")))
            throw new BusinessException(400, "微信授权与当前浏览器或操作不匹配");
        Long userId = null;
        if (binding) {
            LoginUser user = principal();
            userId = ((Number) pending.get("userId")).longValue();
            if (!userId.equals(user.getUser().getId()) || !user.getSessionId().equals(pending.get("sessionId")))
                throw new BusinessException(403, "绑定会话已变化，请重新扫码");
        }
        if (!cache.deleteIfEquals(key, pending)) throw new BusinessException(400, "微信授权已过期或已使用，请重新扫码");
        WechatClient.Identity identity = client.exchange(code);
        if (binding) { accounts.bind(userId, identity); return userId; }
        return accounts.resolve(identity, allowRegistration);
    }

    /** 只接受登录主体，避免匿名调用绑定流程或信任浏览器提交的 userId。 */
    private LoginUser principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) throw new BusinessException(401, "请先登录");
        return (LoginUser) authentication.getPrincipal();
    }

    /** 检查开关、密钥和固定 HTTPS 回调，localhost 仅供本地调试使用。 */
    private void requireConfigured() {
        if (!config.isEnabled() || config.getAppId().isBlank() || config.getAppSecret().isBlank()
                || !(config.getCallbackUri().startsWith("https://") || config.getCallbackUri().startsWith("http://localhost:")))
            throw new BusinessException(503, "微信登录尚未配置，请使用邮箱登录");
    }

    /** 按接口约定检查短文本，防止空值和超长授权参数进入外部请求或缓存键。 */
    private String text(Map<String, Object> body, String field, int limit) {
        Object value = body == null ? null : body.get(field);
        if (!(value instanceof String) || ((String) value).isBlank() || ((String) value).length() > limit)
            throw new BusinessException(400, "微信授权参数不正确");
        return (String) value;
    }

    /** 计算随机 verifier 摘要，与发起时缓存的 challenge 比较，不持久化原值。 */
    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("无法生成授权摘要"); }
    }
}
