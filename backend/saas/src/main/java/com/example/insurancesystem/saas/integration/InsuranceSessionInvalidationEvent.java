package com.example.insurancesystem.saas.integration;

/**
 * 表示 SaaS 业务事务完成后需要在车险系统执行的会话失效动作。
 * 事件只允许企业和用户两种明确范围，发布方不感知 HTTP 细节，监听方也不参与余额或成员业务判断。
 */
public record InsuranceSessionInvalidationEvent(Scope scope, Long targetId) {
  public enum Scope {
    ENTERPRISE,
    USER
  }

  public static InsuranceSessionInvalidationEvent enterprise(Long enterpriseId) {
    return new InsuranceSessionInvalidationEvent(Scope.ENTERPRISE, enterpriseId);
  }

  public static InsuranceSessionInvalidationEvent user(Long userId) {
    return new InsuranceSessionInvalidationEvent(Scope.USER, userId);
  }
}
