package com.example.insurancesystem.expression;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
/**
 * 暴露给 Spring 方法安全表达式的自定义权限判断组件，使控制器可通过 Bean 表达式检查 LoginUser 权限集合。
 */
public class MyExpressionRoot {

    /**
     * 从当前认证主体读取权限代码并判断是否包含目标权限。该方法用于已经要求认证的安全表达式，
     * 因此主体按 LoginUser 处理；匿名请求会先被安全过滤链拦截。
     */
    public boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        List<String> permissions = loginUser.getPermissions();
        return permissions.contains(authority);
    }
}
