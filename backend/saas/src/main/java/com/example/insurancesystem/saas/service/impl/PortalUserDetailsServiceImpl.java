package com.example.insurancesystem.saas.service.impl;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.saas.mapper.PortalUserMapper;
import java.util.Collections;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PortalUserDetailsServiceImpl implements UserDetailsService {
  private final PortalUserMapper userMapper;

  public PortalUserDetailsServiceImpl(PortalUserMapper userMapper) {
    this.userMapper = userMapper;
  }

  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user = userMapper.findForLogin(username);
    if (user == null || user.getStatus() == null || user.getStatus() != 1) {
      throw new UsernameNotFoundException("账号不存在或已停用");
    }
    return new LoginUser(user, Collections.singletonList("portal:access"));
  }
}
