package com.example.insurancesystem.saas.service;

import java.util.Map;

public interface PortalAuthService {
  /** 微信身份已验证后创建与邮箱登录等价的个人会话，不要求先加入企业。 */
  Map<String, Object> wechatLogin(Long userId);

  Map<String, Object> login(Map<String, Object> body);

  Map<String, Object> ssoLogin(Long userId, Long enterpriseId);

  Map<String, Object> register(Map<String, Object> body);

  boolean forgetPassword(Map<String, Object> body);
}
