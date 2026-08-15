package com.example.insurancesystem.saas.service;

import java.util.Map;

public interface SsoService {
  Map<String, Object> authorize();

  Map<String, Object> exchange(String clientSecret, Map<String, Object> body);

  Map<String, Object> authorizePortal(String clientSecret, Map<String, Object> body);

  Map<String, Object> exchangePortal(Map<String, Object> body);
}
