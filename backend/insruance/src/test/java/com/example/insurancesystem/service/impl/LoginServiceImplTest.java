package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.handler.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginServiceImplTest {

    @Test
    void rejectsAuthenticatedUserWithoutEnterprise() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        LoginServiceImpl service = new LoginServiceImpl();
        ReflectionTestUtils.setField(service, "authenticationManager", authenticationManager);

        User user = new User();
        user.setId(10L);
        user.setUsername("unassigned-user");
        user.setPassword("password");
        user.setStatus(1);
        LoginUser principal = new LoginUser(user, List.of());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.login(user));

        assertEquals(403, exception.getCode());
        assertEquals("尚未加入企业，请先前往 SaaS 门户创建或加入企业", exception.getMsg());
    }
}
