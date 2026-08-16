package com.example.insurancesystem.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.example.insurancesystem.mapper.UserMapper;
import com.example.insurancesystem.security.SingleLoginSessionManager;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 验证企业和用户两个内部登出维度都准确删除目标 Redis 单登录会话。 */
class InsuranceSessionInvalidationServiceTest {
    private final UserMapper userMapper = mock(UserMapper.class);
    private final SingleLoginSessionManager sessions = mock(SingleLoginSessionManager.class);
    private final InsuranceSessionInvalidationService service =
            new InsuranceSessionInvalidationService(userMapper, sessions);

    @Test
    void logsOutEveryMemberOfEnterprise() {
        when(userMapper.selectUserIdsByEnterprise(20L)).thenReturn(List.of(1L, 2L, 3L));

        assertEquals(3, service.logoutEnterprise(20L));

        verify(sessions).removeAll(1L);
        verify(sessions).removeAll(2L);
        verify(sessions).removeAll(3L);
    }

    @Test
    void logsOutSingleUserWithoutDependingOnMembership() {
        assertEquals(1, service.logoutUser(9L));
        verify(sessions).removeAll(9L);
        verifyNoInteractions(userMapper);
    }
}
