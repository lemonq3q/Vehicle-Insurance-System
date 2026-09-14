package com.example.insurancesystem.monitor.service;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.monitor.mapper.MonitorAuthMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 将监控账号和角色转换为公共 JWT 认证链能够识别的 LoginUser。
 * 权限同时包含统一访问标识与 ROLE_ 角色标识，供接口级认证和管理员授权分别使用；
 * 该服务仅在 monitor 应用中装配，不会改变 SaaS 门户或车险系统的账号查询来源。
 */
@Service
public class MonitorUserDetailsService implements UserDetailsService {
    private final MonitorAuthMapper authMapper;

    /**
     * 注入监控认证 Mapper，账号状态和密码哈希均以数据库实时数据为准。
     *
     * @param authMapper 监控账号认证数据访问入口
     */
    public MonitorUserDetailsService(MonitorAuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    /**
     * 根据登录账号装配认证主体。没有任何有效角色的账号不允许进入监控后台，
     * 防止已撤销角色但尚未停用的历史账号获得基础访问权限。
     *
     * @param username 登录页提交的账号
     * @return 包含 BCrypt 密码摘要和角色权限的认证主体
     * @throws UsernameNotFoundException 账号不存在或没有有效角色时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = authMapper.findForLogin(username);
        if (user == null) throw new UsernameNotFoundException("账号不存在");
        List<String> roles = authMapper.findRoleCodes(user.getId());
        if (roles == null || roles.isEmpty()) throw new UsernameNotFoundException("账号未配置有效角色");
        List<String> permissions = roles.stream().map(role -> "ROLE_" + role).collect(Collectors.toList());
        permissions.add("monitor:access");
        return new LoginUser(user, permissions);
    }
}
