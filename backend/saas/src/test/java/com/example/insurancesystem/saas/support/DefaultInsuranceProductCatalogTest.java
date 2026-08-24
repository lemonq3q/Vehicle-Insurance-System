package com.example.insurancesystem.saas.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.insurancesystem.saas.domain.DefaultInsuranceProductDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证随应用发布的标准险种资源可以被企业初始化流程稳定使用。
 *
 * <p>测试读取真实 classpath 文件，避免仅验证模拟数据而遗漏资源 JSON 转义、条目数量或历史脏空白等问题。
 */
class DefaultInsuranceProductCatalogTest {

  /**
   * 加载完整目录并核对当前数据库固化得到的 23 条险种。目录构造过程同时覆盖 JSON 选项、默认值、
   * 类型范围和名称唯一性校验，因此构造成功即表示资源满足批量落库的前置条件。
   */
  @Test
  void shouldLoadCompleteCatalogFromClasspathResource() {
    DefaultInsuranceProductCatalog catalog = new DefaultInsuranceProductCatalog(new ObjectMapper());

    List<DefaultInsuranceProductDefinition> products = catalog.getProducts();
    assertEquals(23, products.size());
    assertEquals("车辆损失险", products.get(0).getName());
    assertEquals("交强险、车船税", products.get(products.size() - 1).getName());
    assertTrue(products.stream().allMatch(product -> product.getName().equals(product.getName().trim())));
    assertFalse(products.stream().anyMatch(product -> product.getName().contains("\t")));
  }
}
