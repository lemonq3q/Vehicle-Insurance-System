package com.example.insurancesystem.saas.support;

import com.example.insurancesystem.saas.domain.DefaultInsuranceProductDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 管理随 SaaS 应用发布的标准险种目录。
 *
 * <p>目录以 classpath 资源作为唯一基准，不再依赖任意存量企业的数据。组件在应用启动时一次性加载并校验
 * 资源，之后向企业创建事务提供不可变快照；资源缺失、格式错误、名称重复或默认选项无效时会阻止应用启动，
 * 避免创建出没有完整险种配置的企业。
 */
@Component
public class DefaultInsuranceProductCatalog {
  static final String RESOURCE_PATH = "insurance/default-insurance-products.json";

  private final ObjectMapper objectMapper;
  private final List<DefaultInsuranceProductDefinition> products;

  public DefaultInsuranceProductCatalog(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.products = loadAndValidate();
  }

  /**
   * 返回企业初始化使用的标准险种定义。
   *
   * @return 启动时完成校验的不可变险种列表，调用方不能修改目录结构
   */
  public List<DefaultInsuranceProductDefinition> getProducts() {
    return products;
  }

  /**
   * 从应用资源读取标准险种，并验证插库所需的关键业务约束。每条记录必须具有唯一名称、合法类型、
   * 非空选项数组以及属于该数组的默认选项；免赔额选项若存在，也必须是合法数组并包含对应默认值。
   *
   * @return 完成规范化和校验的不可变目录
   * @throws IllegalStateException 资源无法读取或任一险种定义不完整时抛出，阻止错误配置进入运行环境
   */
  private List<DefaultInsuranceProductDefinition> loadAndValidate() {
    ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
    try (InputStream input = resource.getInputStream()) {
      List<DefaultInsuranceProductDefinition> definitions =
          objectMapper.readValue(
              input, new TypeReference<List<DefaultInsuranceProductDefinition>>() {});
      if (definitions.isEmpty()) throw new IllegalStateException("标准险种资源不能为空");

      Set<String> names = new HashSet<>();
      for (DefaultInsuranceProductDefinition definition : definitions) {
        validateDefinition(definition, names);
      }
      return List.copyOf(definitions);
    } catch (IOException | IllegalArgumentException exception) {
      throw new IllegalStateException("无法加载标准险种资源: " + RESOURCE_PATH, exception);
    }
  }

  /**
   * 校验并规范化单条资源数据。名称会清除数据库历史数据遗留的首尾空白；JSON 字段继续以字符串保存，
   * 以便 MyBatis 在单条批量 INSERT 中直接写入 MySQL JSON 列。
   *
   * @param definition 待校验的险种资源定义
   * @param names 当前目录中已出现的险种名称集合
   */
  private void validateDefinition(
      DefaultInsuranceProductDefinition definition, Set<String> names) throws IOException {
    if (definition == null || !StringUtils.hasText(definition.getName())) {
      throw new IllegalArgumentException("标准险种名称不能为空");
    }
    definition.setName(definition.getName().trim());
    if (!names.add(definition.getName())) {
      throw new IllegalArgumentException("标准险种名称重复: " + definition.getName());
    }
    if (definition.getType() == null
        || definition.getType() < 1
        || definition.getType() > 3) {
      throw new IllegalArgumentException("标准险种类型无效: " + definition.getName());
    }
    validateOptions(
        definition.getName(), definition.getOptionsJson(), definition.getDefaultOptionJson());

    boolean hasDeductibleOptions = StringUtils.hasText(definition.getDeductibleOptionsJson());
    boolean hasDefaultDeductible =
        StringUtils.hasText(definition.getDefaultDeductibleOptionJson());
    if (hasDeductibleOptions != hasDefaultDeductible) {
      throw new IllegalArgumentException("免赔额选项与默认值必须同时配置: " + definition.getName());
    }
    if (hasDeductibleOptions) {
      validateOptions(
          definition.getName() + "免赔额",
          definition.getDeductibleOptionsJson(),
          definition.getDefaultDeductibleOptionJson());
    }
  }

  /**
   * 验证选择项 JSON 是非空数组，并确认默认值确实存在于某个选项的 value 字段中，防止新企业拿到
   * 无法被前端正确回显或提交的默认配置。
   */
  private void validateOptions(String fieldName, String optionsJson, String defaultValue)
      throws IOException {
    if (!StringUtils.hasText(optionsJson) || !StringUtils.hasText(defaultValue)) {
      throw new IllegalArgumentException("选项和默认值不能为空: " + fieldName);
    }
    JsonNode options = objectMapper.readTree(optionsJson);
    if (!options.isArray() || options.isEmpty()) {
      throw new IllegalArgumentException("选项必须是非空 JSON 数组: " + fieldName);
    }
    for (JsonNode option : options) {
      if (defaultValue.equals(option.path("value").asText())) return;
    }
    throw new IllegalArgumentException("默认值不在选项中: " + fieldName);
  }
}
