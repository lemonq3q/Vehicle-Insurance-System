package com.example.insurancesystem.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 验证统一 Long 协议只保护 ID，不改变分页、时间和普通数值，并确保前端字符串可以映射回 Java Long。
 */
class SafeLongIdSerializerTest {
  private static final long LARGE_ID = 2090661322625298433L;
  private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

  /** JavaBean 中的主键和关联主键应为字符串，而分页总数、时间戳和政策数值仍必须是数字节点。 */
  @Test
  void shouldOnlySerializeIdentifierPropertiesAsStrings() {
    ProtocolSample sample = new ProtocolSample();
    sample.id = LARGE_ID;
    sample.enterpriseId = LARGE_ID;
    sample.total = 128L;
    sample.createTime = 1787289600L;
    sample.policyAmount = 1250L;

    JsonNode json = objectMapper.valueToTree(sample);

    assertEquals(Long.toString(LARGE_ID), json.path("id").textValue());
    assertEquals(Long.toString(LARGE_ID), json.path("enterpriseId").textValue());
    assertTrue(json.path("total").isIntegralNumber());
    assertTrue(json.path("createTime").isIntegralNumber());
    assertTrue(json.path("policyAmount").isIntegralNumber());
  }

  /** Map 和 ID 数组也应遵循同一协议，避免 SaaS 中动态 Map 响应成为保护盲区。 */
  @Test
  void shouldProtectIdentifiersInMapsAndCollections() {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("merchant_id", LARGE_ID);
    response.put("memberIds", List.of(LARGE_ID, LARGE_ID - 1));
    response.put("total", 2L);

    JsonNode json = objectMapper.valueToTree(response);

    assertTrue(json.path("merchant_id").isTextual());
    assertTrue(json.path("memberIds").get(0).isTextual());
    assertTrue(json.path("total").isIntegralNumber());
  }

  /** 前端以字符串提交 ID 时应直接恢复为 Long，Controller 和 MyBatis 不需要手动转换。 */
  @Test
  void shouldDeserializeStringIdentifiersBackToLong() throws Exception {
    ProtocolSample sample =
        objectMapper.readValue(
            "{\"id\":\"2090661322625298433\",\"enterpriseId\":\"2090661322625298433\"}",
            ProtocolSample.class);

    assertEquals(LARGE_ID, sample.id);
    assertEquals(LARGE_ID, sample.enterpriseId);
  }

  /** 测试载体同时包含 ID 和本次回归涉及的非 ID Long 字段。 */
  public static class ProtocolSample {
    public Long id;
    public Long enterpriseId;
    public Long total;
    public Long createTime;
    public long policyAmount;
  }
}
