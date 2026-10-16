package com.example.insurancesystem.saas.service.impl;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.utils.email.EmailAccountService;
import com.example.insurancesystem.saas.mapper.EnterpriseMapper;
import com.example.insurancesystem.saas.mapper.PortalUserMapper;
import com.example.insurancesystem.security.SingleLoginSessionManager;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortalAuthServiceImplTest {

  private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
  private final PortalAuthServiceImpl service =
      new PortalAuthServiceImpl(
          authenticationManager,
          mock(SingleLoginSessionManager.class),
          mock(PortalUserMapper.class),
          mock(EnterpriseMapper.class),
          mock(PasswordEncoder.class),
          mock(EmailAccountService.class));

  @Test
  void mapsInvalidCredentialsToBusinessWarning() {
    when(authenticationManager.authenticate(any()))
        .thenThrow(new BadCredentialsException("Bad credentials"));

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> service.login(Map.of("email", "unknown@example.com", "password", "wrong")));

    assertEquals(400, exception.getCode());
    assertEquals("邮箱或密码错误", exception.getMsg());
  }

  @Test
  void mapsDisabledAccountSeparately() {
    when(authenticationManager.authenticate(any()))
        .thenThrow(new DisabledException("User is disabled"));

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> service.login(Map.of("email", "disabled@example.com", "password", "password")));

    assertEquals(403, exception.getCode());
    assertEquals("账号未启用", exception.getMsg());
  }
}
