package com.example.insurancesystem.security;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.handler.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Provides the enterprise bound to the current authenticated insurance user.
 */
public final class EnterpriseContextHolder {

    private EnterpriseContextHolder() {
    }

    public static Long getEnterpriseId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
            return null;
        }
        return ((LoginUser) authentication.getPrincipal()).getEnterpriseId();
    }

    public static Long requireEnterpriseId() {
        Long enterpriseId = getEnterpriseId();
        if (enterpriseId == null) {
            throw new BusinessException(403, "当前账号未关联企业");
        }
        return enterpriseId;
    }
}
