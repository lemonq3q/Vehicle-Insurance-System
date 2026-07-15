package com.example.insurancesystem.domain.merchant;

import java.util.Map;

/** Business-only staff roles. They never grant system permissions. */
public final class MerchantStaffRoleCode {
    public static final String CONTACT = "CONTACT";
    public static final String CLERK = "CLERK";
    public static final String PAYEE = "PAYEE";

    private static final Map<String, String> NAME_TO_CODE = Map.of(
            "联系人", CONTACT,
            "店员", CLERK,
            "普通人员", CLERK,
            "收款人", PAYEE
    );

    private MerchantStaffRoleCode() {
    }

    public static String fromDisplayName(String name) {
        return NAME_TO_CODE.getOrDefault(name, CLERK);
    }

    public static String displayName(String code) {
        return switch (code) {
            case CONTACT -> "联系人";
            case PAYEE -> "收款人";
            default -> "店员";
        };
    }
}
