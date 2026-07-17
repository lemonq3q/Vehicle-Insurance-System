package com.example.insurancesystem.saas.integration.sms;

import java.util.Map;

public interface SmsVerificationService {
  Map<String, Object> send(String phone, String scene);

  void verify(String phone, String scene, String code);
}
