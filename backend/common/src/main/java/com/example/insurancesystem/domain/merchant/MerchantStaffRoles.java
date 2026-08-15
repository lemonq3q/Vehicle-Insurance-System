package com.example.insurancesystem.domain.merchant;

import java.util.List;

/**
 * 聚合商户员工角色相关常量和校验辅助能力，避免控制器与服务层散落角色代码字符串。
 */
public final class MerchantStaffRoles {
    public static final long CONTACT_ID = 1001L;
    public static final long CLERK_ID = 1002L;
    public static final long PAYEE_ID = 1003L;

    /**
     * 角色常量聚合类禁止实例化。
     */
    private MerchantStaffRoles() {
    }

    /**
     * 按固定 ID、代码和名称返回全部商户角色选项，供前端角色下拉框和服务层校验共用。
     */
    public static List<MerchantStaffRoleOption> options() {
        return List.of(
                new MerchantStaffRoleOption(CONTACT_ID, MerchantStaffRoleCode.CONTACT, "联系人"),
                new MerchantStaffRoleOption(CLERK_ID, MerchantStaffRoleCode.CLERK, "店员"),
                new MerchantStaffRoleOption(PAYEE_ID, MerchantStaffRoleCode.PAYEE, "收款人")
        );
    }

    /**
     * 将历史角色 ID 转换为稳定角色代码，空值和未知 ID 均回退到店员。
     */
    public static String codeOf(Long id) {
        if (id == null) return MerchantStaffRoleCode.CLERK;
        if (id == CONTACT_ID) return MerchantStaffRoleCode.CONTACT;
        if (id == PAYEE_ID) return MerchantStaffRoleCode.PAYEE;
        return MerchantStaffRoleCode.CLERK;
    }
}
