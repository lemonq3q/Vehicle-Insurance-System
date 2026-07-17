package com.example.insurancesystem.saas.service;

import java.util.Map;

public interface ProfileService {
  Map<String, Object> get();

  Map<String, Object> update(Map<String, Object> body);
}
