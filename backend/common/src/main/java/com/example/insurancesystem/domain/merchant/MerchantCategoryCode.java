package com.example.insurancesystem.domain.merchant;

import java.util.Map;

/** Stable merchant categories used by the optimized schema. */
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

    private MerchantCategoryCode() {
    }

    public static String fromLegacyName(String name) {
        return LEGACY_NAME_TO_CODE.get(name);
    }
}
