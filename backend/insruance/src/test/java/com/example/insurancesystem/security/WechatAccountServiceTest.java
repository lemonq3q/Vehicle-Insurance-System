package com.example.insurancesystem.security;

import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.security.wechat.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 核对门户自动注册、昵称降级以及一对一绑定冲突；只使用 Mapper Mock，不写真实数据库。
 * 测试确保无头像、企业或权限字段被写入，车险未知身份不能触发账号创建。
 */
class WechatAccountServiceTest {
    /** 昵称不可获取时生成姓名，注册只写共享个人账号且不伪造邮箱或头像。 */
    @Test
    @SuppressWarnings("unchecked")
    void generatesNameForMissingNickname() {
        WechatAccountMapper mapper = mock(WechatAccountMapper.class);
        when(mapper.insertWechatUser(anyMap())).thenAnswer(call -> {
            Map<String, Object> values = call.getArgument(0);
            assertTrue(values.get("name").toString().matches("微信用户[0-9a-f]{8}"));
            assertFalse(values.containsKey("email")); assertFalse(values.containsKey("avatar"));
            assertFalse(values.containsKey("enterpriseId"));
            values.put("id", 23L); return 1;
        });
        assertEquals(23L, new WechatAccountService(mapper).resolve(new WechatClient.Identity("wx_test", "openid", "", ""), true));
    }

    /** 车险微信入口发现身份不存在时直接拒绝，没有数据库写入。 */
    @Test
    void unknownInsuranceIdentityDoesNotRegister() {
        WechatAccountMapper mapper = mock(WechatAccountMapper.class);
        assertThrows(BusinessException.class, () -> new WechatAccountService(mapper).resolve(new WechatClient.Identity("wx_test", "openid", "", "昵称"), false));
        verify(mapper, never()).insertWechatUser(anyMap());
    }

    /** 其他账号持有微信时拒绝绑定，不通过更新覆盖原账号归属。 */
    @Test
    void refusesOccupiedWechat() {
        WechatAccountMapper mapper = mock(WechatAccountMapper.class);
        User current = new User(); current.setId(23L); current.setStatus(1);
        User owner = new User(); owner.setId(24L);
        when(mapper.findUser(23L)).thenReturn(current);
        when(mapper.findIdentity("wx_test", "openid")).thenReturn(owner);
        assertThrows(BusinessException.class, () -> new WechatAccountService(mapper).bind(23L, new WechatClient.Identity("wx_test", "openid", "", "昵称")));
        verify(mapper, never()).bind(anyMap());
    }
}
