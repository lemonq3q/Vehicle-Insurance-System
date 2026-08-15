package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.mapper.MenuMapper;
import com.example.insurancesystem.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
/**
 * 将项目用户、所属企业及菜单权限转换为 Spring Security 可识别的登录主体。
 * 该服务是账号密码认证链路读取用户身份和授权信息的入口。
 */
public class UserDetailServiceImpl implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MenuMapper menuMapper;

    @Override
    /**
     * 按登录名读取用户；用户存在所属企业时加载其在该企业下的菜单权限。
     * 未绑定企业的账号仅建立身份主体，不授予企业菜单权限；账号不存在时终止认证。
     *
     * @param username 登录账号
     * @return 包含用户资料和权限标识的安全上下文主体
     * @throws UsernameNotFoundException 登录名不存在时抛出
     */
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectLoginUser(username);
        if (Objects.isNull(user)) {
            throw new UsernameNotFoundException("username is not exist");
        }
        List<String> list = user.getEnterpriseId() == null
                ? Collections.emptyList()
                : menuMapper.selectPermsByUserId(user.getId(), user.getEnterpriseId());

        return new LoginUser(user, list);
    }
}
