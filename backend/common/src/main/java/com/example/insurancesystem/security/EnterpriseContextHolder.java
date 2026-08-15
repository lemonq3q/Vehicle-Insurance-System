package com.example.insurancesystem.security;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.handler.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 Spring Security 当前线程上下文中提供车险端登录用户所属企业。
 * 业务查询和写入以这里的企业 ID 作为租户边界，避免由请求参数直接指定企业而产生越权访问。
 */
public final class EnterpriseContextHolder {

    /**
     * 工具类只提供静态上下文访问能力，禁止创建无状态实例。
     */
    private EnterpriseContextHolder() {
    }

    /**
     * 尝试读取当前认证主体绑定的企业 ID。未认证或主体不是车险端 LoginUser 时返回空值，
     * 便于允许匿名访问的基础设施代码自行决定是否要求企业上下文。
     */
    public static Long getEnterpriseId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
            return null;
        }
        return ((LoginUser) authentication.getPrincipal()).getEnterpriseId();
    }

    /**
     * 获取业务操作必需的企业 ID；当前账号没有企业绑定时立即抛出 403 业务异常，
     * 防止后续 Mapper 在缺少租户条件的情况下执行查询或写入。
     */
    public static Long requireEnterpriseId() {
        Long enterpriseId = getEnterpriseId();
        if (enterpriseId == null) {
            throw new BusinessException(403, "当前账号未关联企业");
        }
        return enterpriseId;
    }
}
