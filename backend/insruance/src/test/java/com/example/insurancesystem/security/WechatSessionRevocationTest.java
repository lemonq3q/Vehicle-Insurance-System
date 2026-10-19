package com.example.insurancesystem.security;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.utils.RedisCache;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证授权撤回对会话的时间边界，并确认旧请求只续期现存键、不复活已撤销会话。
 */
class WechatSessionRevocationTest {
    /** 延迟事件保留事件之后的新会话，早期会话通过比较删除而非无条件删除。 */
    @Test
    void preservesNewLoginAndRevokesOldSession() {
        RedisCache cache = mock(RedisCache.class);
        SingleLoginSessionManager manager = new SingleLoginSessionManager(cache, "login:");
        LoginUser recent = new LoginUser(new User(), List.of()); recent.setAuthenticatedAt(201000L); recent.setSessionId("recent-id");
        when(cache.getCacheObject("login:23")).thenReturn(recent);
        manager.removeBefore(23L, 200L);
        verify(cache, never()).deleteSessionIfId(anyString(), any());
        recent.setAuthenticatedAt(199000L);
        manager.removeBefore(23L, 200L);
        verify(cache).deleteSessionIfId("login:23", "recent-id");
        verify(cache, never()).deleteObject(anyString());
    }

    /** 活跃请求只能延长仍存在的同一份会话，不能覆盖或重新创建撤回后的缓存。 */
    @Test
    void refreshDoesNotRecreateRevokedSession() {
        RedisCache cache = mock(RedisCache.class);
        LoginUser principal = new LoginUser(new User(), List.of()); principal.setSessionId("session-id");
        new SingleLoginSessionManager(cache, "login:").refresh(23L, principal);
        verify(cache).expireSessionIfId("login:23", "session-id", 86400L);
        verify(cache, never()).setCacheObject(anyString(), any(), anyInt(), any());
    }
}
