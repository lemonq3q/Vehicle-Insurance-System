package com.example.insurancesystem.domain.merchant;

import java.util.Map;

/**
 * 商户内部业务角色代码，只描述联系人、店员和收款人职责，不授予系统菜单或接口权限。
 */
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

    /**
     * 角色常量工具类禁止实例化。
     */
    private MerchantStaffRoleCode() {
    }

    /**
     * 将历史中文角色名转换为稳定代码，无法识别时回退到权限最普通的店员角色。
     */
    public static String fromDisplayName(String name) {
        return NAME_TO_CODE.getOrDefault(name, CLERK);
    }

    /**
     * 将角色代码转换为中文展示名，未知代码按店员显示以兼容历史数据。
     */
    public static String displayName(String code) {
        return switch (code) {
            case CONTACT -> "联系人";
            case PAYEE -> "收款人";
            default -> "店员";
        };
    }
}
