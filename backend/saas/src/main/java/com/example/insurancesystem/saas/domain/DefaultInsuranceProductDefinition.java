package com.example.insurancesystem.saas.domain;

/**
 * 新企业标准险种目录中的单条资源定义。
 *
 * <p>该对象只保存可跨企业复用的险种业务字段，不携带数据库主键、企业主键和审计时间。企业创建时，
 * SaaS 服务会将资源中的定义复制到 {@code biz_insurance_product}，并补充当前企业和创建人的绑定信息。
 */
public class DefaultInsuranceProductDefinition {
  private String name;
  private Integer type;
  private String optionsJson;
  private String defaultOptionJson;
  private String deductibleOptionsJson;
  private String defaultDeductibleOptionJson;
  private String remark;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getType() {
    return type;
  }

  public void setType(Integer type) {
    this.type = type;
  }

  public String getOptionsJson() {
    return optionsJson;
  }

  public void setOptionsJson(String optionsJson) {
    this.optionsJson = optionsJson;
  }

  public String getDefaultOptionJson() {
    return defaultOptionJson;
  }

  public void setDefaultOptionJson(String defaultOptionJson) {
    this.defaultOptionJson = defaultOptionJson;
  }

  public String getDeductibleOptionsJson() {
    return deductibleOptionsJson;
  }

  public void setDeductibleOptionsJson(String deductibleOptionsJson) {
    this.deductibleOptionsJson = deductibleOptionsJson;
  }

  public String getDefaultDeductibleOptionJson() {
    return defaultDeductibleOptionJson;
  }

  public void setDefaultDeductibleOptionJson(String defaultDeductibleOptionJson) {
    this.defaultDeductibleOptionJson = defaultDeductibleOptionJson;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }
}
