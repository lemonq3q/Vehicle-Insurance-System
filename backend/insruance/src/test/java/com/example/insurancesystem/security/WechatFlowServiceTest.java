package com.example.insurancesystem.security;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.security.wechat.*;
import com.example.insurancesystem.utils.RedisCache;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 无网络、数据库和 Redis 的授权边界测试，验证原浏览器证明、用途及发起会话的强关联。
 * 确保车险不注册，绑定不能换用户或用其他浏览器兑换；失败不得调用微信或修改账号。
 */
class WechatFlowServiceTest {
    private RedisCache cache;
    private WechatClient client;
    private WechatAccountService accounts;
    private WechatFlowService flow;
    private final String state = "a".repeat(32);
    private final String verifier = "b".repeat(64);

    /** 使用本地固定配置及 Mock，隔离外部服务。 */
    @BeforeEach
    void setup() {
        cache = mock(RedisCache.class); client = mock(WechatClient.class); accounts = mock(WechatAccountService.class);
        WechatProperties props = new WechatProperties(); props.setEnabled(true); props.setAppId("wx_test");
        props.setAppSecret("test-secret"); props.setCallbackUri("https://example.com/wechat/callback");
        flow = new WechatFlowService(props, cache, client, accounts);
    }

    /** 认证上下文不可跨测试保留。 */
    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    /** 内部配置改名不改变前端契约，二维码仍通过 redirectUri 取得当前应用回调地址。 */
    @Test
    void startKeepsPublicRedirectUriContract() {
        when(cache.putIfAbsent(anyString(), eq("1"), eq(2L))).thenReturn(true);
        Map<String, Object> response = flow.start(Map.of("challenge", "b".repeat(64)), false);
        assertEquals("https://example.com/wechat/callback", response.get("redirectUri"));
        assertFalse(response.containsKey("callbackUri"));
        verifyNoInteractions(client, accounts);
    }

    /** 有效车险登录只按已有身份查账号，始终禁止自动注册。 */
    @Test
    void insuranceNeverRegisters() throws Exception {
        Map<String, Object> pending = pending(false);
        when(cache.getCacheObject("wechat:state:" + state)).thenReturn(pending);
        when(cache.deleteIfEquals(anyString(), eq(pending))).thenReturn(true);
        WechatClient.Identity identity = new WechatClient.Identity("wx_test", "openid", "", "昵称");
        when(client.exchange("code")).thenReturn(identity);
        when(accounts.resolve(identity, false)).thenReturn(17L);
        assertEquals(17L, flow.complete(body(), false, false));
        verify(accounts).resolve(identity, false);
    }

    /** 他人浏览器或用途切换无法消费原事务，原 state 不因无效兑换而被删除。 */
    @Test
    void rejectsForeignBrowserAndPurpose() throws Exception {
        when(cache.getCacheObject("wechat:state:" + state)).thenReturn(pending(false));
        Map<String, Object> wrong = new HashMap<>(body()); wrong.put("verifier", "c".repeat(64));
        assertThrows(BusinessException.class, () -> flow.complete(wrong, false, true));
        assertThrows(BusinessException.class, () -> flow.complete(body(), true, false));
        verify(cache, never()).deleteIfEquals(anyString(), any()); verifyNoInteractions(client, accounts);
    }

    /** 绑定发起后若切换用户，即使持有原 verifier 也不得绑定到新账号。 */
    @Test
    void bindingRejectsChangedSession() throws Exception {
        Map<String, Object> pending = new HashMap<>(pending(true)); pending.put("userId", 17L); pending.put("sessionId", "original");
        when(cache.getCacheObject("wechat:state:" + state)).thenReturn(pending);
        User user = new User(); user.setId(18L);
        LoginUser principal = new LoginUser(user, List.of()); principal.setSessionId("other");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        assertThrows(BusinessException.class, () -> flow.complete(body(), true, false));
        verifyNoInteractions(client, accounts);
    }

    /** Redis 原子消费失败意味着并发重放，禁止再次请求微信 code 兑换接口。 */
    @Test
    void rejectsReplay() throws Exception {
        when(cache.getCacheObject("wechat:state:" + state)).thenReturn(pending(false));
        when(cache.deleteIfEquals(anyString(), any())).thenReturn(false);
        assertThrows(BusinessException.class, () -> flow.complete(body(), false, true));
        verifyNoInteractions(client, accounts);
    }

    /** 构造来自前端本标签页的临时兑换参数。 */
    private Map<String, Object> body() { return Map.of("code", "code", "state", state, "verifier", verifier); }

    /** 构造后端只保存摘要的待授权状态，不包含原 verifier 或应用密钥。 */
    private Map<String, Object> pending(boolean binding) throws Exception {
        String challenge = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8)));
        return Map.of("binding", binding, "challenge", challenge);
    }
}
