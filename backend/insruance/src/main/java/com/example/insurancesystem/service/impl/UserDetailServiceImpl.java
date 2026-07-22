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
public class UserDetailServiceImpl implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MenuMapper menuMapper;

    @Override
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
