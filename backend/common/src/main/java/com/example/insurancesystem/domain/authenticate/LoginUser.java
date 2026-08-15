package com.example.insurancesystem.domain.authenticate;

import com.example.insurancesystem.domain.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * Spring Security 认证主体，组合系统用户、权限集合、企业租户 ID 和 Redis 单登录 sessionId。
 * JWT 过滤器从 Redis 恢复该对象并据此建立当前请求的身份、权限与企业上下文。
 */
public class LoginUser implements UserDetails {

    private User user;

    private List<String> permissions;

    private String sessionId;

    private Long enterpriseId;

    @JsonIgnore
    private List<GrantedAuthority> authorities;

    /**
     * 从数据库用户和权限代码创建认证主体，同时复制用户企业 ID；空权限集合转换为空列表，避免鉴权时空指针。
     */
    public LoginUser(User user, List<String> permissions) {
        this.user = user;
        this.enterpriseId = user.getEnterpriseId();
        this.permissions = Objects.requireNonNullElseGet(permissions, ArrayList::new);
    }

    @Override
    /**
     * 将权限字符串懒转换为 Spring GrantedAuthority 并缓存，避免同一请求多次鉴权重复构建对象。
     */
    public Collection<? extends GrantedAuthority> getAuthorities() {

        if (authorities == null) {
            authorities = permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        }
        return authorities;
    }

    @Override
    /**
     * 向 Spring Security 提供数据库中已编码的密码哈希，仅用于认证比对。
     */
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    /**
     * 返回系统登录账号作为 Spring Security 主体名称。
     */
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    /**
     * 当前数据模型未维护账号到期日，因此账号始终视为未过期；启停由 isEnabled 处理。
     */
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    /**
     * 当前数据模型未设置独立锁定标记，因此始终视为未锁定。
     */
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    /**
     * 当前系统未维护密码凭证到期策略，因此凭证始终视为未过期。
     */
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    /**
     * 账号状态必须启用；若用户已关联企业，其租户成员状态也必须启用。任一停用都会阻止认证，
     * 从而支持 SaaS 管理端暂停成员使用权而不删除账号或取消套餐。
     */
    public boolean isEnabled() {
        boolean accountEnabled = Integer.valueOf(1).equals(user.getStatus());
        boolean memberEnabled = user.getEnterpriseId() == null
                || Integer.valueOf(1).equals(user.getMemberStatus());
        return accountEnabled && memberEnabled;
    }
}
