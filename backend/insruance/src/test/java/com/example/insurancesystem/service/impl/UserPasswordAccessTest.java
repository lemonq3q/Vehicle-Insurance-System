package com.example.insurancesystem.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.mapper.UserMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 验证个人中心密码维护不能通过篡改用户 ID 更新其他账号，保留本人维护能力。
 * 使用隔离的登录上下文和 Mapper，不访问真实数据库或修改共享账号密码。
 */
class UserPasswordAccessTest {
  /**
   * 每个用例结束清理线程登录态，避免影响其他账号认证测试。
   */
  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  /**
   * 构造无用户管理权限的当前用户，伪造另一个账号 ID 必须在数据库操作前拒绝。
   */
  @Test
  void ordinaryUserCannotResetAnotherAccount() {
    UserMapper mapper = mock(UserMapper.class);
    UserServiceImpl service = service(mapper);
    User target = new User(); target.setId(99L); target.setPassword("password123");
    assertEquals(403, service.updatePassword(target).getCode());
    verifyNoInteractions(mapper);
  }

  /**
   * 本人密码修改使用已有编码器并按主键更新，保持个人中心原有操作可用。
   */
  @Test
  void ownAccountCanChangePassword() {
    UserMapper mapper = mock(UserMapper.class);
    UserServiceImpl service = service(mapper);
    User target = new User(); target.setId(7L); target.setPassword("password123");
    when(mapper.update(isNull(), any())).thenReturn(1);
    assertEquals(200, service.updatePassword(target).getCode());
    verify(mapper).update(isNull(), any());
  }

  /**
   * 初始化当前用户及必要依赖，权限为空使测试覆盖普通用户边界。
   */
  private UserServiceImpl service(UserMapper mapper) {
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "password-access-test"), User.class);
    User current = new User(); current.setId(7L);
    LoginUser principal = new LoginUser(current, List.of());
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    UserServiceImpl service = new UserServiceImpl();
    ReflectionTestUtils.setField(service, "userMapper", mapper);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(encoder.encode("password123")).thenReturn("encoded");
    ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
    return service;
  }
}
