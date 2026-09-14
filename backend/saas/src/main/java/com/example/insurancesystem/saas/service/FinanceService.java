package com.example.insurancesystem.saas.service;

import com.example.insurancesystem.domain.encapsulate.TableData;
import java.util.List;
import java.util.Map;

public interface FinanceService {
  Map<String, Object> overview();

  List<Map<String, Object>> plans();

  Map<String, Object> createRecharge(Map<String, Object> body);

  Map<String, Object> rechargeDetail(Long id);

  Map<String, Object> completeRecharge(Map<String, Object> body);

  Map<String, Object> cancelRecharge(Long id);

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

  /**
   * 查询当前企业的一笔历史订阅订单，返回下单时的套餐权益快照及完整金额、支付和状态信息。
   *
   * @param id 订阅订单主键
   * @return 可供门户只读详情页展示的订单数据
   */
  Map<String, Object> orderDetail(Long id);

  TableData<Map<String, Object>> transactions(
      int pageNum,
      int pageSize,
      String transactionNo,
      String direction,
      String type,
      String startTime,
      String endTime);
}
