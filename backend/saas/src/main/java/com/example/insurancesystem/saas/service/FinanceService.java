package com.example.insurancesystem.saas.service;

import com.example.insurancesystem.domain.encapsulate.TableData;
import java.util.List;
import java.util.Map;

public interface FinanceService {
  Map<String, Object> overview();

  List<Map<String, Object>> plans();

  Map<String, Object> createRecharge(Map<String, Object> body);

  TableData<Map<String, Object>> recharges(
      int pageNum,
      int pageSize,
      String rechargeNo,
      Integer status,
      String startTime,
      String endTime);

  Map<String, Object> preview(Map<String, Object> body);

  Map<String, Object> subscribe(Map<String, Object> body);

  Map<String, Object> updateAutoRenew(Map<String, Object> body);

  TableData<Map<String, Object>> orders(
      int pageNum,
      int pageSize,
      String orderNo,
      String orderType,
      String startTime,
      String endTime);

  TableData<Map<String, Object>> transactions(
      int pageNum,
      int pageSize,
      String transactionNo,
      String direction,
      String type,
      String startTime,
      String endTime);
}
