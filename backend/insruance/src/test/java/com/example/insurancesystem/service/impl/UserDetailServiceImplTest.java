package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.mapper.MenuMapper;
import com.example.insurancesystem.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserDetailServiceImplTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final MenuMapper menuMapper = mock(MenuMapper.class);
    private final UserDetailServiceImpl service = new UserDetailServiceImpl();

    UserDetailServiceImplTest() {
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "menuMapper", menuMapper);
    }

    @Test
    void returnsDisabledPrincipalForInactiveEnterpriseMember() {
        User user = loginUser(1, 0);
        when(userMapper.selectLoginUser("issuer")).thenReturn(user);
        when(menuMapper.selectPermsByUserId(10L, 23L)).thenReturn(List.of());

        LoginUser result = (LoginUser) service.loadUserByUsername("issuer");

        assertFalse(result.isEnabled());
        verify(menuMapper).selectPermsByUserId(10L, 23L);
    }

    @Test
    void carriesEnterpriseIntoAuthenticatedPrincipal() {
        User user = loginUser(1, 1);
        when(userMapper.selectLoginUser("issuer")).thenReturn(user);
        when(menuMapper.selectPermsByUserId(10L, 23L)).thenReturn(List.of("workorder:list"));

        LoginUser result = (LoginUser) service.loadUserByUsername("issuer");

        assertEquals(23L, result.getEnterpriseId());
        assertTrue(result.isEnabled());
        verify(menuMapper).selectPermsByUserId(10L, 23L);
    }

    @Test
    void marksDisabledAccountAsNotEnabled() {
        LoginUser loginUser = new LoginUser(loginUser(0, 1), List.of());

        assertFalse(loginUser.isEnabled());
    }

    @Test
    void returnsEnterpriseRequiredPrincipalWithoutLoadingPermissions() {
        User user = loginUser(1, 1);
        user.setEnterpriseId(null);
        user.setMemberStatus(null);
        when(userMapper.selectLoginUser("issuer")).thenReturn(user);

        LoginUser result = (LoginUser) service.loadUserByUsername("issuer");

        assertTrue(result.isEnabled());
        assertTrue(result.isCredentialsNonExpired());
        assertTrue(result.getPermissions().isEmpty());
        verifyNoInteractions(menuMapper);
    }

    private User loginUser(int accountStatus, int memberStatus) {
        User user = new User();
        user.setId(10L);
        user.setUsername("issuer");
        user.setPassword("encoded-password");
        user.setStatus(accountStatus);
        user.setMemberStatus(memberStatus);
        user.setEnterpriseId(23L);
        return user;
    }
}
