package com.example.insurancesystem.saas.integration.email;

import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.utils.email.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证共享邮箱身份的并发冲突、既有绑定保护与密码原样编码，不写入数据库。
 */
class EmailAccountServiceTest {
    private final EmailAccountMapper mapper = mock(EmailAccountMapper.class);
    private final EmailVerificationService codes = mock(EmailVerificationService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final EmailAccountService service = new EmailAccountService(mapper, codes, encoder);

    /**
     * 已绑定账号不能通过绑定接口替换邮箱或密码。
     */
    @Test
    void existingBindingIsNotOverwritten() {
        User user = user(); user.setEmail("original@example.com");
        when(mapper.lockById(17L)).thenReturn(user);
        assertEquals(409, assertThrows(BusinessException.class, () -> service.bindEmail(17L, payload())).getCode());
        verifyNoInteractions(codes, encoder);
        verify(mapper, never()).bindEmail(anyLong(), anyString(), anyString());
    }

    /**
     * 并发申请同一邮箱时，即使预检通过仍将数据库唯一冲突转换为可理解的业务错误。
     */
    @Test
    void concurrentEmailConflictIsReported() {
        when(mapper.lockById(17L)).thenReturn(user());
        when(mapper.findByEmail("new@example.com")).thenReturn(List.of());
        when(encoder.encode("password123")).thenReturn("encoded");
        when(mapper.bindEmail(17L, "new@example.com", "encoded")).thenThrow(new DuplicateKeyException("unique"));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.bindEmail(17L, payload())).getCode());
        verify(codes).verifyAndConsume("new@example.com", "BIND_EMAIL", 17L, "123456");
    }

    /**
     * 绑定保留原用户身份，以当前会话用户 ID 设置规范邮箱，同时原样编码密码空白。
     */
    @Test
    void bindsOwnerWithoutTrimmingPassword() {
        when(mapper.lockById(17L)).thenReturn(user());
        when(mapper.findByEmail("new@example.com")).thenReturn(List.of());
        when(encoder.encode(" password123 ")).thenReturn("encoded");
        when(mapper.bindEmail(17L, "new@example.com", "encoded")).thenReturn(1);
        when(mapper.findProfile(17L)).thenReturn(Map.of("id", 17L, "email", "new@example.com", "real_name", "李明"));
        Map<String, Object> result = service.bindEmail(17L, Map.of("email", " NEW@EXAMPLE.COM ", "emailCode", "123456", "password", " password123 ", "id", 99L));
        assertEquals(17L, result.get("id")); assertEquals("李明", result.get("realName"));
        verify(encoder).encode(" password123 ");
    }

    /**
     * 历史重复邮箱不得批量重置或随机选择一个账号。
     */
    @Test
    void duplicateHistoricalEmailsCannotResetAccounts() {
        when(mapper.findByEmail("new@example.com")).thenReturn(List.of(user(), user()));
        assertEquals(409, assertThrows(BusinessException.class, () -> service.resetPassword(payload())).getCode());
        verifyNoInteractions(codes, encoder);
    }

    /**
     * 构造可绑定邮箱的有效历史账号，不包含企业身份。
     */
    private User user() { User user = new User(); user.setId(17L); user.setStatus(1); return user; }
    /**
     * 模拟合法的首次绑定请求，邮箱规范化由共享服务负责。
     */
    private Map<String, Object> payload() { return Map.of("email", "new@example.com", "emailCode", "123456", "password", "password123"); }
}
