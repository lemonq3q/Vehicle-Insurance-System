package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.monitor.service.MonitorEnterpriseService;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控平台企业列表与概览接口。该控制器只接受监控账号认证上下文，
 * 读取接口返回月度宽表聚合结果，套餐变更接口把登录人身份传入事务服务完成审计留痕。
 */
@RestController
@RequestMapping("/monitor/enterprises")
public class MonitorEnterpriseController {
    private final MonitorEnterpriseService service;

    public MonitorEnterpriseController(MonitorEnterpriseService service) { this.service = service; }

    /** 按关键字、状态、套餐和到期范围分页查询，并返回本月三项用量。 */
    @GetMapping
    public ResponseResult<Map<String, Object>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) Integer expireDays,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return new ResponseResult<>(200, service.page(keyword, status, planId, expireDays, pageNo, pageSize));
    }

    /** 根据名称或企业编码关键词返回有限候选项，供自定义对比的远程搜索框使用。 */
    @GetMapping("/options")
    public ResponseResult<List<Map<String, Object>>> options(
            @RequestParam(required = false) String keyword) {
        return new ResponseResult<>(200, service.options(keyword));
    }

    /** 按指定统计指标和周期返回平台用量排名靠前的企业，供系统推荐模式生成初始勾选集合。 */
    @GetMapping("/usage-top")
    public ResponseResult<List<Map<String, Object>>> usageTop(
            @RequestParam(defaultValue = "request") String metric,
            @RequestParam(defaultValue = "30d") String range,
            @RequestParam(defaultValue = "5") int top) {
        return new ResponseResult<>(200, service.usageTop(metric, range, top));
    }

    /** 对 1 至 20 家企业执行同周期用量聚合，并返回对齐后的公共时间轴和三类指标。 */
    @GetMapping("/usage-comparison")
    public ResponseResult<Map<String, Object>> usageComparison(
            @RequestParam String enterpriseIds,
            @RequestParam(defaultValue = "30d") String range) {
        return new ResponseResult<>(200, service.usageComparison(enterpriseIds, range));
    }

    /** 返回单个企业基本资料、订阅、钱包和本月用量快照。 */
    @GetMapping("/{id}")
    public ResponseResult<Map<String, Object>> detail(@PathVariable Long id) {
        return new ResponseResult<>(200, service.detail(id));
    }

    /** 按白名单时间范围返回指定企业的工单、API 和 OCR 趋势。 */
    @GetMapping("/{id}/usage")
    public ResponseResult<Map<String, Object>> usage(@PathVariable Long id,
            @RequestParam(defaultValue = "30d") String range) {
        return new ResponseResult<>(200, service.usage(id, range));
    }

    /** 按姓名、账号、手机号、企业角色和成员状态分页查询企业成员，只提供监控侧只读数据。 */
    @GetMapping("/{id}/members")
    public ResponseResult<Map<String, Object>> members(@PathVariable Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return new ResponseResult<>(200, service.members(id, keyword, roleName, status, pageNo, pageSize));
    }

    /**
     * 汇总指定企业的实时钱包余额、累计已支付充值、累计已支付订阅支出和本月钱包流水数量。
     * 该接口只提供监控侧只读视图，不改变任何财务状态。
     */
    @GetMapping("/{id}/finance-summary")
    public ResponseResult<Map<String, Object>> financeSummary(@PathVariable Long id) {
        return new ResponseResult<>(200, service.financeSummary(id));
    }

    /** 返回企业筛选和套餐设置共用的套餐精简选项。 */
    @GetMapping("/subscription-plans")
    public ResponseResult<List<Map<String, Object>>> subscriptionPlans() {
        return new ResponseResult<>(200, service.subscriptionPlans());
    }

    /** 按充值单号和创建日期范围分页读取指定企业充值订单。 */
    @GetMapping("/{id}/recharge-orders")
    public ResponseResult<Map<String, Object>> rechargeOrders(@PathVariable Long id,
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return new ResponseResult<>(200, service.financeRecords(id, "recharge-orders", businessNo, startDate, endDate, pageNo, pageSize));
    }

    /** 按订阅单号和创建日期范围分页读取指定企业套餐订单。 */
    @GetMapping("/{id}/subscription-orders")
    public ResponseResult<Map<String, Object>> subscriptionOrders(@PathVariable Long id,
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return new ResponseResult<>(200, service.financeRecords(id, "subscription-orders", businessNo, startDate, endDate, pageNo, pageSize));
    }

    /** 按流水号和发生日期范围分页读取指定企业钱包流水。 */
    @GetMapping("/{id}/wallet-transactions")
    public ResponseResult<Map<String, Object>> walletTransactions(@PathVariable Long id,
            @RequestParam(required = false) String businessNo,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return new ResponseResult<>(200, service.financeRecords(id, "wallet-transactions", businessNo, startDate, endDate, pageNo, pageSize));
    }

    /**
     * 接收监控人员的人工调账金额和必填原因，在事务内更新钱包、生成 ADJUST 流水并记录操作审计。
     * 登录人来自监控账号认证上下文，不能由请求体伪造。
     */
    @PostMapping("/{id}/balance-adjustments")
    public ResponseResult<Map<String, Object>> adjustBalance(@PathVariable Long id,
            @RequestBody Map<String, Object> body, Authentication authentication) {
        LoginUser operator = operator(authentication);
        return new ResponseResult<>(200, service.adjustBalance(id, body,
                operator.getUser().getId(), operator.getUser().getRealName()));
    }

    /** 平台人工开通或更换企业套餐，订阅状态与专用审计记录在同一事务内写入。 */
    @PutMapping("/{id}/subscription")
    public ResponseResult<Map<String, Object>> setSubscription(@PathVariable Long id,
            @RequestBody Map<String, Object> body, Authentication authentication) {
        LoginUser operator = operator(authentication);
        return new ResponseResult<>(200, service.setSubscription(id, body, operator.getUser().getId(), operator.getUser().getRealName()));
    }

    /** 平台人工取消企业当前套餐，清空当前权益并强制记录操作原因。 */
    @DeleteMapping("/{id}/subscription")
    public ResponseResult<?> cancelSubscription(@PathVariable Long id,
            @RequestBody Map<String, Object> body, Authentication authentication) {
        LoginUser operator = operator(authentication);
        service.cancelSubscription(id, body, operator.getUser().getId(), operator.getUser().getRealName());
        return new ResponseResult<>(200, "企业套餐已取消", null);
    }

    /** 从 Spring Security 上下文严格获取监控账号，防止审计记录出现匿名操作人。 */
    private LoginUser operator(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser))
            throw new BusinessException(401, "请先登录");
        return (LoginUser) authentication.getPrincipal();
    }
}
