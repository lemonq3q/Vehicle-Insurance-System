package com.example.insurancesystem.saas.service;

import com.example.insurancesystem.domain.encapsulate.TableData;
import java.util.Map;

public interface EnterpriseService {
  Map<String, Object> current();

  Map<String, Object> create(Map<String, Object> body);

  Map<String, Object> update(Map<String, Object> body);

  Map<String, Object> join(Map<String, Object> body);

  TableData<Map<String, Object>> invites(int pageNum, int pageSize);

  Map<String, Object> createInvite(Map<String, Object> body);

  boolean deleteInvite(Map<String, Object> body);

  TableData<Map<String, Object>> members(
      int pageNum, int pageSize, String keyword, String roleCode, Integer status);

  Map<String, Object> updateRole(Map<String, Object> body);

  Map<String, Object> updateStatus(Map<String, Object> body);

  Map<String, Object> transfer(Map<String, Object> body);

  TableData<Map<String, Object>> transferLogs(int pageNum, int pageSize);

  boolean exit();
}
