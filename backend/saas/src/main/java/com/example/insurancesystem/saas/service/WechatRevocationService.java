package com.example.insurancesystem.saas.service;

import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.security.SingleLoginSessionManager;
import com.example.insurancesystem.security.wechat.WechatAccountMapper;
import com.example.insurancesystem.saas.integration.client.InsuranceSessionClient;
import com.example.insurancesystem.utils.RedisCache;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 处理已经验签解密的微信撤回通知，仅撤销两端旧会话，不修改任何账号或用户资料。
 * 远端待办保存在 Redis Hash，重启可继续重试；事件时间边界保护重新授权的新会话。
 */
@Service
public class WechatRevocationService {
    private static final Logger log = LoggerFactory.getLogger(WechatRevocationService.class);
    private static final String QUEUE = "wechat:revocations";
    private final WechatAccountMapper users;
    private final SingleLoginSessionManager sessions;
    private final InsuranceSessionClient insurance;
    private final RedisCache cache;

    /** 复用共享用户身份与既有内部服务客户端，两个系统不需要共享 Redis 命名空间。 */
    public WechatRevocationService(WechatAccountMapper users, SingleLoginSessionManager sessions, InsuranceSessionClient insurance, RedisCache cache) {
        this.users = users; this.sessions = sessions; this.insurance = insurance; this.cache = cache;
    }

    /**
     * 已认证事件按 AppID/OpenID 定位账号，未知或其他类型事件安全确认，不删除用户。
     * @param event 来自安全模式消息解码器的事件字段，不接收前端直接调用
     */
    public void accept(Map<String, String> event) {
        if (!"user_authorization_revoke".equals(event.get("Event"))) return;
        long cutoff;
        try { cutoff = Long.parseLong(event.getOrDefault("CreateTime", "")); }
        catch (Exception exception) { throw new BusinessException(400, "微信撤回时间不正确"); }
        if (cutoff <= 0 || cutoff > Instant.now().getEpochSecond() + 300) throw new BusinessException(400, "微信撤回时间不正确");
        String openId = event.getOrDefault("OpenID", "");
        if (openId.isBlank() || openId.length() > 128) throw new BusinessException(400, "微信身份不正确");
        User user = users.findIdentity(event.get("AppID"), openId);
        if (user == null) return;
        String eventId = user.getId() + ":" + cutoff;
        if (cache.getCacheObject("wechat:revoked:" + eventId) != null) return;
        Map<String, Object> work = new java.util.LinkedHashMap<>();
        work.put("userId", user.getId()); work.put("cutoff", cutoff);
        /* 先持久化待办再撤销会话，进程中断或远端不可用时均可安全恢复。 */
        cache.setCacheMapValue(QUEUE, eventId, work);
        process(eventId, work);
    }

    /** 每轮最多处理五十项；失败保留 Redis 待办，不依赖浏览器在线或无限次微信回调。 */
    @Scheduled(fixedDelayString = "${insurance.wechat.revocation-retry-ms:30000}")
    public void retry() {
        Map<String, Map<String, Object>> pending = cache.getCacheMap(QUEUE);
        pending.entrySet().stream().limit(50).forEach(entry -> {
            try { process(entry.getKey(), entry.getValue()); }
            catch (Exception exception) { log.warn("微信撤回会话同步暂未完成，事件 {}", entry.getKey()); }
        });
    }

    /** 两端只清理事件发生时的旧会话；成功标记后移除待办，重复请求不会误踢新登录。 */
    private void process(String eventId, Map<String, Object> work) {
        if (!cache.putIfAbsent("wechat:revoke-lock:" + eventId, "1", 25)) return;
        try {
            Long userId = ((Number) work.get("userId")).longValue();
            long cutoff = ((Number) work.get("cutoff")).longValue();
            sessions.removeBefore(userId, cutoff);
            try { insurance.logoutUserBefore(userId, cutoff); }
            catch (Exception exception) { throw new BusinessException(502, "车险会话同步暂未完成，将自动重试"); }
            cache.setCacheObject("wechat:revoked:" + eventId, "1", 7, TimeUnit.DAYS);
            cache.delCacheMapValue(QUEUE, eventId);
        } finally { cache.deleteObject("wechat:revoke-lock:" + eventId); }
    }
}
