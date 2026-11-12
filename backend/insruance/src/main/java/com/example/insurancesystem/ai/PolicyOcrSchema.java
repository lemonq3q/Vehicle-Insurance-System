package com.example.insurancesystem.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 为纯文本整理模型提供严格的三险 JSON Schema。
 * 所有键固定且允许 null，业务格式和险种归属仍由本地校验器判断。
 */
public final class PolicyOcrSchema {
    private PolicyOcrSchema() {
    }

    public static final Map<String, Object> SCHEMA = Map.of(
        "name", "vehicle_policy_ocr",
        "strict", true,
        "schema", object(Map.of(
            "commercial", group(List.of("insurerName", "policyNo", "startTime", "endTime", "premiumAmount")),
            "compulsory", group(List.of("insurerName", "policyNo", "startTime", "endTime", "premiumAmount", "vehicleAndTaxAmount")),
            "nonMotor", group(List.of("insurerName", "policyNo", "startTime", "endTime", "premiumAmount", "insuranceName", "coverageAmount"))
        )));

    /**
     * 生成一组全部键必填、值可为字符串或 null 的字段结构。
     * null 表达原始资料缺失，避免整理模型为符合必填约束而编造值。
     */
    private static Map<String, Object> group(List<String> fields) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String field : fields) properties.put(field, Map.of("type", List.of("string", "null")));
        return object(properties);
    }

    /**
     * 统一设置对象属性、完整必填键及禁止额外字段的约束。
     */
    private static Map<String, Object> object(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties,
            "required", List.copyOf(properties.keySet()), "additionalProperties", false);
    }
}
