package com.example.insurancesystem.saas.integration.email;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.utils.RedisCache;
import com.example.insurancesystem.utils.email.EmailUtil;
import com.example.insurancesystem.utils.email.EmailVerificationService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证真实邮件失败、用途隔离、重放和尝试额度等认证边界，不发送外部邮件。
 */
class EmailVerificationServiceTest {
    private final EmailUtil sender = mock(EmailUtil.class);
    private final RedisCache cache = mock(RedisCache.class);
    private final EmailVerificationService service = new EmailVerificationService(sender, cache);

    /**
     * 邮件未被实际服务接受时，不能保存可用于注册的验证码。
     */
    @Test
    void failedDeliveryDoesNotIssueUsableCode() {
        when(cache.putIfAbsent(anyString(), any(), eq(60L))).thenReturn(true);
        when(sender.sendEmail(anyString(), anyString(), anyString())).thenReturn(false);
        BusinessException error = assertThrows(BusinessException.class, () -> service.send(" User@Example.com ", "REGISTER", null));
        assertEquals(502, error.getCode());
        verify(sender).sendEmail(eq("user@example.com"), anyString(), anyString());
        verify(cache, never()).setCacheObject(anyString(), any(), anyInt(), any());
        verify(cache).deleteIfEquals(eq("email-code:cooldown:user@example.com"), any());
    }

    /**
     * 重发限制在发送前原子占用，重复请求不会再次调用邮件服务。
     */
    @Test
    void cooldownStopsDuplicateDelivery() {
        when(cache.putIfAbsent(anyString(), any(), anyLong())).thenReturn(false);
        assertEquals(429, assertThrows(BusinessException.class, () -> service.send("user@example.com", "REGISTER", null)).getCode());
        verifyNoInteractions(sender);
    }

    /**
     * 绑定码按当前用户隔离；成功消费后相同验证码重放失败。
     */
    @Test
    void consumesBindingCodeOnceForItsOwner() {
        String key = "email-code:BIND_EMAIL:17:user@example.com";
        when(cache.incrementCounter(anyString(), eq(300L))).thenReturn(1L);
        when(cache.getCacheObject(key)).thenReturn("123456");
        when(cache.deleteIfEquals(key, "123456")).thenReturn(true, false);
        service.verifyAndConsume("USER@example.com", "BIND_EMAIL", 17L, "123456");
        assertEquals(400, assertThrows(BusinessException.class, () -> service.verifyAndConsume("user@example.com", "BIND_EMAIL", 17L, "123456")).getCode());
        assertEquals(400, assertThrows(BusinessException.class, () -> service.verifyAndConsume("user@example.com", "BIND_EMAIL", 18L, "123456")).getCode());
        assertEquals(400, assertThrows(BusinessException.class, () -> service.verifyAndConsume("user@example.com", "REGISTER", null, "123456")).getCode());
    }

    /**
     * 超过五次猜测后关闭该验证码，正确值也不能绕过额度。
     */
    @Test
    void rejectsExceededAttemptBudget() {
        String key = "email-code:RESET_PASSWORD:anonymous:user@example.com";
        when(cache.incrementCounter(key + ":attempts", 300)).thenReturn(6L);
        assertEquals(429, assertThrows(BusinessException.class, () -> service.verifyAndConsume("user@example.com", "RESET_PASSWORD", null, "123456")).getCode());
        verify(cache).deleteObject(key);
        verify(cache, never()).deleteIfEquals(anyString(), any());
    }
}
