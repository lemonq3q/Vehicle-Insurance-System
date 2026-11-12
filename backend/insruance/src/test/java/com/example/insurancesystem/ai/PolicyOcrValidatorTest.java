package com.example.insurancesystem.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证三险字段完整遍历、日期与金额约束，防止首个问题阻断后续校验。
 */
class PolicyOcrValidatorTest {
    private final PolicyOcrValidator validator = new PolicyOcrValidator(new ObjectMapper());

    /**
     * 同一响应内多个险种和多个字段都有错误时，应一次返回全部已发现问题。
     */
    @Test
    void collectsAllFieldErrorsBeforeOrganizing() {
        String raw = """
            {"commercial":{"insurerName":"公司","policyNo":12,"startTime":"2026-02-30 00:00:00","endTime":null,"premiumAmount":"1,000.00"},
             "compulsory":{"insurerName":null,"policyNo":null,"startTime":null,"endTime":null,"premiumAmount":"950元","vehicleAndTaxAmount":null},
             "nonMotor":{"insurerName":null,"policyNo":null,"startTime":null,"endTime":null,"premiumAmount":null,"insuranceName":null,"coverageAmount":null}}
            """;
        PolicyOcrValidator.ValidationResult result = validator.validate(raw);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("commercial.policyNo")));
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("commercial.startTime")));
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("commercial.premiumAmount")));
        assertTrue(result.errors().stream().anyMatch(item -> item.contains("compulsory.premiumAmount")));
        assertEquals("公司", result.values().get("commercialInsurerName"));
    }

    /**
     * 完整时间和纯日期均可回填；纯日期使用北京时间零点，且不产生人工补时间的警告。
     */
    @Test
    void convertsFullTimeAndDateOnlyWithoutWarning() {
        String raw = """
            {"commercial":{"insurerName":null,"policyNo":"0001","startTime":"2026-01-01 00:00:00","endTime":"2027-01-01","premiumAmount":"0"},
             "compulsory":{"insurerName":null,"policyNo":null,"startTime":null,"endTime":null,"premiumAmount":null,"vehicleAndTaxAmount":null},
             "nonMotor":{"insurerName":null,"policyNo":null,"startTime":null,"endTime":null,"premiumAmount":null,"insuranceName":null,"coverageAmount":null}}
            """;
        PolicyOcrValidator.ValidationResult result = validator.validate(raw);
        assertTrue(result.valid(), result.errors().toString());
        assertEquals("0001", result.values().get("commercialPolicyNo"));
        assertEquals("0.00", result.values().get("commercialAmount"));
        assertEquals(1767196800L, result.values().get("commercialInsuranceStartTime"));
        assertEquals(1798732800L, result.values().get("commercialInsuranceEndTime"));
        assertTrue(result.warnings().isEmpty());
    }

    /**
     * 复核实际整理日志中的交强险结构：只有交强险有值时仍应通过完整校验，
     * 并生成前端承保表单使用的扁平字段，而不要求其他两险也有资料。
     */
    @Test
    void acceptsCompulsoryOnlyOrganizerResult() {
        String raw = """
            {"commercial":{"insurerName":null,"policyNo":null,"startTime":null,"endTime":null,"premiumAmount":null},
             "compulsory":{"insurerName":"华泰财产保险有限公司上海分公司","policyNo":"602EA1022026004723","startTime":"2026-11-16 00:00:00","endTime":"2027-11-15 23:59:00","premiumAmount":"475.00","vehicleAndTaxAmount":"360.00"},
             "nonMotor":{"insurerName":null,"policyNo":null,"startTime":null,"endTime":null,"premiumAmount":null,"insuranceName":null,"coverageAmount":null}}
            """;
        PolicyOcrValidator.ValidationResult result = validator.validate(raw);
        assertTrue(result.valid(), result.errors().toString());
        assertEquals(6, result.values().size());
        assertEquals("602EA1022026004723", result.values().get("compulsoryPolicyNo"));
        assertEquals("475.00", result.values().get("compulsoryAmount"));
        assertEquals("360.00", result.values().get("vehicleAndTaxAmount"));
    }

    /**
     * 常见展示日期、千分位及 JSON 数字可保留有效值，但仍触发整理以要求模型输出标准字符串。
     * 非法日期和错误逗号分组不得被宽松转换为看似有效的业务值。
     */
    @Test
    void acceptsSafeVariantsButRequestsCanonicalModelOutput() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.readTree(PolicyOcrPrompt.SHAPE);
        var group = (com.fasterxml.jackson.databind.node.ObjectNode) root.get("commercial");
        for (String date : java.util.List.of("2026/11/12", "2026年11月12日", "2026.11.12", "2026-11-12T00:00:00")) {
            group.put("startTime", date);
            group.put("premiumAmount", "￥1,000.00元");
            var result = validator.validate(root.toString());
            assertEquals(java.time.LocalDate.of(2026, 11, 12).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toEpochSecond(), result.values().get("commercialInsuranceStartTime"));
            assertEquals("1000.00", result.values().get("commercialAmount"));
            assertFalse(result.valid());
        }
        group.put("startTime", "2026-11-12");
        group.put("premiumAmount", 950);
        var numeric = validator.validate(root.toString());
        assertEquals("950.00", numeric.values().get("commercialAmount"));
        assertFalse(numeric.valid());
        group.put("startTime", "2026/02/30");
        group.put("premiumAmount", "1,00.00");
        var invalid = validator.validate(root.toString());
        assertFalse(invalid.values().containsKey("commercialInsuranceStartTime"));
        assertFalse(invalid.values().containsKey("commercialAmount"));
        assertTrue(validator.validate(root + " {}").values().isEmpty());
    }
}
