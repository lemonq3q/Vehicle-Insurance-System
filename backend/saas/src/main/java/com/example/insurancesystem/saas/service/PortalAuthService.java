package com.example.insurancesystem.saas.service;

import java.util.Map;

public interface PortalAuthService {
  Map<String, Object> login(Map<String, Object> body);

  Map<String, Object> register(Map<String, Object> body);

  boolean forgetPassword(Map<String, Object> body);
}
