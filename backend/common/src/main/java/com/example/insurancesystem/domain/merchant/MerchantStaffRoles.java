package com.example.insurancesystem.domain.merchant;

import java.util.List;

public final class MerchantStaffRoles {
    public static final long CONTACT_ID = 1001L;
    public static final long CLERK_ID = 1002L;
    public static final long PAYEE_ID = 1003L;

    private MerchantStaffRoles() {
    }

    public static List<MerchantStaffRoleOption> options() {
        return List.of(
                new MerchantStaffRoleOption(CONTACT_ID, MerchantStaffRoleCode.CONTACT, "联系人"),
                new MerchantStaffRoleOption(CLERK_ID, MerchantStaffRoleCode.CLERK, "店员"),
                new MerchantStaffRoleOption(PAYEE_ID, MerchantStaffRoleCode.PAYEE, "收款人")
        );
    }

    public static String codeOf(Long id) {
        if (id == null) return MerchantStaffRoleCode.CLERK;
        if (id == CONTACT_ID) return MerchantStaffRoleCode.CONTACT;
        if (id == PAYEE_ID) return MerchantStaffRoleCode.PAYEE;
        return MerchantStaffRoleCode.CLERK;
    }
}
