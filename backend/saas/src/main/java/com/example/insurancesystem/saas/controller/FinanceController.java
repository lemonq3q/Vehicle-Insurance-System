package com.example.insurancesystem.saas.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.saas.service.FinanceService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/portal/finance")
public class FinanceController {
  private final FinanceService service;

  public FinanceController(FinanceService service) {
    this.service = service;
  }

  @GetMapping("/overview")
  public ResponseResult<?> overview() {
    return ok(service.overview());
  }

  @GetMapping("/plans")
  public ResponseResult<?> plans() {
    return ok(service.plans());
  }

  @PostMapping("/recharge-orders")
  public ResponseResult<?> recharge(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "充值订单已创建", service.createRecharge(body));
  }

  @GetMapping("/recharge-orders")
  public ResponseResult<?> recharges(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "10") int pageSize,
      @RequestParam(required = false) String rechargeNo,
      @RequestParam(required = false) Integer status,
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime) {
    return ok(service.recharges(pageNum, pageSize, rechargeNo, status, startTime, endTime));
  }

  @PostMapping("/subscription-orders/preview")
  public ResponseResult<?> preview(@RequestBody Map<String, Object> body) {
    return ok(service.preview(body));
  }

  @PostMapping("/subscription-orders")
  public ResponseResult<?> subscribe(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "订阅订单已支付，套餐已生效", service.subscribe(body));
  }

  @PutMapping("/subscription/auto-renew")
  public ResponseResult<?> renew(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "自动续费设置已更新", service.updateAutoRenew(body));
  }

  @GetMapping("/subscription-orders")
  public ResponseResult<?> orders(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "10") int pageSize,
      @RequestParam(required = false) String orderNo,
      @RequestParam(required = false) String orderType,
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime) {
    return ok(service.orders(pageNum, pageSize, orderNo, orderType, startTime, endTime));
  }

  @GetMapping("/wallet-transactions")
  public ResponseResult<?> transactions(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "10") int pageSize,
      @RequestParam(required = false) String transactionNo,
      @RequestParam(required = false) String direction,
      @RequestParam(required = false) String transactionType,
      @RequestParam(required = false) String startTime,
      @RequestParam(required = false) String endTime) {
    return ok(
        service.transactions(
            pageNum, pageSize, transactionNo, direction, transactionType, startTime, endTime));
  }

  private ResponseResult<?> ok(Object data) {
    return new ResponseResult<>(200, "操作成功", data);
  }
}
