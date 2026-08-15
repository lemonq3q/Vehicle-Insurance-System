package com.example.insurancesystem.domain.merchant;

import java.util.Map;

/**
 * 优化后商户表使用的稳定类别代码，并提供旧中文类别向新代码迁移的兼容映射。
 */
public final class MerchantCategoryCode {
    public static final String INSURANCE_ORG = "INSURANCE_ORG";
    public static final String DEALER_STORE = "DEALER_STORE";
    public static final String AUTO_REPAIR = "AUTO_REPAIR";
    public static final String AGENT = "AGENT";

    private static final Map<String, String> LEGACY_NAME_TO_CODE = Map.of(
            "机构", INSURANCE_ORG,
            "保司", INSURANCE_ORG,
            "车商店铺", DEALER_STORE,
            "汽修厂", AUTO_REPAIR,
            "代理人", AGENT
    );

    /**
     * 常量工具类禁止实例化。
     */
    private MerchantCategoryCode() {
    }

    /**
     * 将旧数据中的“机构、保司、车商店铺、汽修厂、代理人”等名称转换为稳定类别代码，未知名称返回 null。
     */
    public static String fromLegacyName(String name) {
        return LEGACY_NAME_TO_CODE.get(name);
    }
}
