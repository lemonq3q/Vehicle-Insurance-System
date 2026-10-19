package com.example.insurancesystem.saas.service;

import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.security.SingleLoginSessionManager;
import com.example.insurancesystem.security.wechat.WechatAccountMapper;
import com.example.insurancesystem.saas.integration.client.InsuranceSessionClient;
import com.example.insurancesystem.utils.RedisCache;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 撤回授权只读用户身份并撤销两端会话，验证远端接收相同事件截止时间，禁止修改账号资料。
 */
class WechatRevocationServiceTest {
    /** 正常撤回持久化待办后撤销两端旧会话，完成才移除待办。 */
    @Test
    void revokesBothSystemsWithoutChangingAccount() {
        WechatAccountMapper users = mock(WechatAccountMapper.class);
        SingleLoginSessionManager sessions = mock(SingleLoginSessionManager.class);
        InsuranceSessionClient client = mock(InsuranceSessionClient.class);
        RedisCache cache = mock(RedisCache.class);
        User user = new User(); user.setId(19L);
        when(users.findIdentity("wx_test", "openid")).thenReturn(user);
        when(cache.putIfAbsent(anyString(), any(), anyLong())).thenReturn(true);
        long cutoff = Instant.now().getEpochSecond() - 10;
        new WechatRevocationService(users, sessions, client, cache).accept(Map.of("Event", "user_authorization_revoke", "AppID", "wx_test", "OpenID", "openid", "CreateTime", Long.toString(cutoff)));
        verify(sessions).removeBefore(19L, cutoff); verify(client).logoutUserBefore(19L, cutoff);
        verify(users).findIdentity("wx_test", "openid"); verifyNoMoreInteractions(users);
        verify(cache).delCacheMapValue("wechat:revocations", "19:" + cutoff);
    }
}
