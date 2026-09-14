package com.example.insurancesystem.saas.support;

public interface BusinessCodeGenerator {
  String enterpriseCode();

  String inviteCode();

  String rechargeNo();

  String subscriptionOrderNo();

  String transactionNo();

  /** 为官网游客线索生成不暴露数据库主键的公开编号。 */
  String visitorLeadNo();
}
