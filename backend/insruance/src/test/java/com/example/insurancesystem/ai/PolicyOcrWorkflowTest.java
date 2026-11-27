package com.example.insurancesystem.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 确保整理阶段收到原始 OCR 文本与全部问题，重试配置限制的是首次整理之后的额外次数。
 */
class PolicyOcrWorkflowTest {
    private final PolicyOcrValidator validator = new PolicyOcrValidator(new ObjectMapper());

    /**
     * 结构不合格时最多整理三次：首次加默认两次重试，不重复读取文件。
     */
    @Test
    void retriesOrganizerTwiceAfterInitialAttempt() {
        PolicyOcrModelClient client = mock(PolicyOcrModelClient.class);
        when(client.organize(eq("invalid"), anyList())).thenReturn("still invalid");
        PolicyOcrWorkflow workflow = new PolicyOcrWorkflow(client, validator, 2);
        PolicyOcrValidator.ValidationResult result = workflow.validateAndOrganize("invalid");
        assertTrue(result.values().isEmpty());
        verify(client, times(3)).organize(eq("invalid"), anyList());
    }

    /**
     * 首次 OCR 已通过本地完整校验时不产生任何整理调用。
     */
    @Test
    void skipsOrganizerForValidOcrResult() {
        PolicyOcrModelClient client = mock(PolicyOcrModelClient.class);
        PolicyOcrWorkflow workflow = new PolicyOcrWorkflow(client, validator, 2);
        String raw = """
            {"commercial":{"insurerName":"公司","policyNo":null,"startTime":null,"endTime":null,"premiumAmount":null},
             "compulsory":{"insurerName":null,"policyNo":null,"startTime":null,"endTime":null,"premiumAmount":null,"vehicleAndTaxAmount":null},
             "nonMotor":{"insurerName":null,"policyNo":null,"startTime":null,"endTime":null,"premiumAmount":null,"insuranceName":null,"coverageAmount":null}}
            """;
        assertTrue(workflow.validateAndOrganize(raw).valid());
        verify(client, times(0)).organize(eq(raw), anyList());
    }

    /**
     * 整理耗尽后保留有效 JSON 中已校验的字段，后续畸形响应不能清空它们；非法金额被丢弃。
     */
    @Test
    void retainsValidatedFieldsWhenOrganizerExhaustsAttempts() {
        String raw = "{\"commercial\":{\"policyNo\":\"0001\",\"premiumAmount\":\"错误金额\"}}";
        PolicyOcrModelClient client = mock(PolicyOcrModelClient.class);
        when(client.organize(eq(raw), anyList())).thenReturn(raw, "not json", "not json");
        var result = new PolicyOcrWorkflow(client, validator, 2).validateAndOrganize(raw);
        assertEquals("0001", result.values().get("commercialPolicyNo"));
        assertTrue(!result.values().containsKey("commercialAmount"));
        verify(client, times(3)).organize(eq(raw), anyList());
    }
    /**
     * 非保单等输入按提示词返回完整全null时，直接给出合法空结果，不调用整理模型浪费次数。
     */
    @Test void skipsOrganizerForAllNullResult() {
        PolicyOcrModel client=mock(PolicyOcrModel.class);
        var result=new PolicyOcrWorkflow(client,validator,2).validateAndOrganize(PolicyOcrPrompt.SHAPE);
        assertTrue(result.valid());assertTrue(result.values().isEmpty());
        org.mockito.Mockito.verifyNoInteractions(client);
    }

}
