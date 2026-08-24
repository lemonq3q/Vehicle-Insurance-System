package com.example.insurancesystem.config.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 * 在统一 JSON 输出层保护 Java 长整型标识的 JavaScript 精度。
 *
 * <p>序列化器只把语义明确的 ID 字段输出为十进制字符串，普通 Long（例如分页总数、时间戳和数量）
 * 仍输出为 JSON 数字。它同时适用于 JavaBean、DTO、Map 以及 ID 集合中的元素，不要求业务类增加注解，
 * 也不改变数据库、MyBatis 或 Controller 中的 Long 类型。
 */
public class SafeLongIdSerializer extends JsonSerializer<Long> {

  /**
   * 根据当前 JSON 输出上下文选择安全表示。数组元素本身没有字段名，因此会逐级向父上下文查找
   * 所属属性名，使 {@code userIds: List<Long>} 中的每个元素也能作为字符串输出。
   *
   * @param value 后端准备写入响应的 Long 值
   * @param generator 当前响应使用的 JSON 生成器
   * @param serializers Jackson 序列化上下文
   */
  @Override
  public void serialize(Long value, JsonGenerator generator, SerializerProvider serializers)
      throws IOException {
    String fieldName = findOwningFieldName(generator.getOutputContext());
    if (isIdentifierField(fieldName)) {
      generator.writeString(value.toString());
      return;
    }
    generator.writeNumber(value);
  }

  /**
   * 识别项目常用的标识字段命名。严格区分驼峰后缀大小写，避免把 {@code valid}、{@code paid}
   * 等普通英文单词误判为 ID；同时兼容数据库风格的 {@code enterprise_id}。
   */
  static boolean isIdentifierField(String fieldName) {
    if (fieldName == null || fieldName.isEmpty()) return false;
    return "id".equalsIgnoreCase(fieldName)
        || fieldName.endsWith("Id")
        || fieldName.endsWith("Ids")
        || fieldName.endsWith("ID")
        || fieldName.endsWith("IDs")
        || fieldName.toLowerCase().endsWith("_id")
        || fieldName.toLowerCase().endsWith("_ids");
  }

  /** 从当前值向外查找最近的具名属性，兼容对象字段、Map 值和数组元素。 */
  private String findOwningFieldName(JsonStreamContext context) {
    JsonStreamContext current = context;
    while (current != null) {
      if (current.getCurrentName() != null) return current.getCurrentName();
      current = current.getParent();
    }
    return null;
  }
}
