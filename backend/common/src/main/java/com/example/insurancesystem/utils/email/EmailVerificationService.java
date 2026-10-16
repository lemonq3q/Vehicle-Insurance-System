package com.example.insurancesystem.utils.email;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.utils.RedisCache;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 为注册、密码重置和首次邮箱绑定发送真实验证码，复用 QQ/Gmail 邮件渠道。
 * 验证码在当前应用 Redis 命名空间保留五分钟，冷却按邮箱隔离，绑定码额外关联用户 ID。
 * 不记录或返回验证码；错误尝试计数和成功消费均原子执行，支持多实例部署。
 */
@Service
@ConditionalOnProperty(name = "insurance.email.account-enabled", havingValue = "true")
public class EmailVerificationService {
    private final EmailUtil sender;
    private final RedisCache cache;
    private final SecureRandom random = new SecureRandom();

    /**
     * 注入实际邮件发送器及具有应用命名空间的 Redis 缓存。
     */
    public EmailVerificationService(EmailUtil sender, RedisCache cache) {
        this.sender = sender;
        this.cache = cache;
    }

    /**
     * 规范邮箱身份，拒绝超过数据库长度、空地址和包含空白的非邮箱输入。
     */
    public static String normalizeEmail(String input) {
        String email = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 100 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new BusinessException(400, "邮箱格式错误");
        return email;
    }

    /**
     * 为已经过账号资格检查的邮箱发送验证码；发送失败释放本请求冷却，旧验证码不被失败请求覆盖。
     * @param userId 绑定场景的当前用户 ID，匿名注册和找回场景为 null
     * @return 有效期和重发等待秒数，不包含验证码
     */
    public Map<String, Object> send(String input, String scene, Long userId) {
        String email = normalizeEmail(input);
        String key = codeKey(email, scene, userId);
        String cooldown = "email-code:cooldown:" + email;
        String reservation = UUID.randomUUID().toString();
        if (!cache.putIfAbsent(cooldown, reservation, 60))
            throw new BusinessException(429, "请一分钟后再尝试发送");
        try {
            String code = String.format(Locale.ROOT, "%06d", random.nextInt(1000000));
            String purpose = "REGISTER".equals(scene) ? "创建账号" : "BIND_EMAIL".equals(scene) ? "绑定邮箱" : "修改密码";
            if (!sender.sendEmail(email, "iDatag 邮箱验证码", "您正在" + purpose + "，验证码为：" + code + "。验证码五分钟内有效，请勿泄露。若非本人操作，请忽略此邮件。"))
                throw new BusinessException(502, "邮件发送失败，请稍后重试");
            cache.setCacheObject(key, code, 300, TimeUnit.SECONDS);
            cache.deleteObject(key + ":attempts");
            return Map.of("expiresInSeconds", 300, "retryAfterSeconds", 60);
        } catch (RuntimeException exception) {
            cache.deleteIfEquals(cooldown, reservation);
            throw exception;
        }
    }

    /**
     * 检查指定场景、邮箱和绑定用户对应的六位验证码，最多允许五次错误尝试。
     * 成功时原子比较并删除缓存值，重放、并发消费和被新验证码替换的旧请求均失败。
     */
    public void verifyAndConsume(String input, String scene, Long userId, String code) {
        String key = codeKey(normalizeEmail(input), scene, userId);
        long attempt = cache.incrementCounter(key + ":attempts", 300);
        if (attempt > 5) {
            cache.deleteObject(key);
            throw new BusinessException(429, "验证码尝试次数过多，请重新获取");
        }
        String expected = cache.getCacheObject(key);
        if (code == null || !code.matches("^\\d{6}$") || expected == null || !expected.equals(code)
                || !cache.deleteIfEquals(key, expected))
            throw new BusinessException(400, "验证码错误或已失效");
    }

    /**
     * 固定三种用途；绑定必须关联已认证用户，匿名用途不允许伪造绑定身份。
     */
    private String codeKey(String email, String scene, Long userId) {
        if (scene == null || !Set.of("REGISTER", "RESET_PASSWORD", "BIND_EMAIL").contains(scene)
                || ("BIND_EMAIL".equals(scene) && userId == null))
            throw new BusinessException(400, "邮箱验证码场景不正确");
        return "email-code:" + scene + ":" + (userId == null ? "anonymous" : userId) + ":" + email;
    }
}
