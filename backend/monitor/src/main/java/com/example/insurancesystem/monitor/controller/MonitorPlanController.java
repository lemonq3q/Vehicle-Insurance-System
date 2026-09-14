package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.monitor.service.MonitorPlanService;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控平台套餐配置入口。负责向套餐列表与编辑页提供真实 SaaS 套餐数据，并把新建、修改、
 * 上下架操作连同当前监控账号传给事务服务。套餐权益中的工单额度表示企业订阅期内可免费存储的
 * 工单数量；本接口不实现体验套餐或试用期等特殊规则。
 */
@RestController
@RequestMapping("/monitor/plans")
public class MonitorPlanController {
    private final MonitorPlanService service;

    public MonitorPlanController(MonitorPlanService service) { this.service = service; }

    /** 返回全部未删除套餐，供套餐管理卡片按配置顺序展示。 */
    @GetMapping
    public ResponseResult<List<Map<String, Object>>> list() {
        return new ResponseResult<>(200, service.list());
    }

    /** 返回一个套餐的完整计费、成员和工单额度配置。 */
    @GetMapping("/{id}")
    public ResponseResult<Map<String, Object>> detail(@PathVariable Long id) {
        return new ResponseResult<>(200, service.detail(id));
    }

    /** 新建普通正式套餐，并在同一事务内记录操作人、原因和新套餐快照。 */
    @PostMapping
    public ResponseResult<Map<String, Object>> create(@RequestBody Map<String, Object> body,
            Authentication authentication) {
        LoginUser operator = operator(authentication);
        return new ResponseResult<>(200, service.create(body, operator.getUser().getId(),
                operator.getUser().getRealName()));
    }

    /** 修改套餐基础配置；套餐编码保持不可变，已有订阅快照不会被追溯更新。 */
    @PutMapping("/{id}")
    public ResponseResult<Map<String, Object>> update(@PathVariable Long id,
            @RequestBody Map<String, Object> body, Authentication authentication) {
        LoginUser operator = operator(authentication);
        return new ResponseResult<>(200, service.update(id, body, operator.getUser().getId(),
                operator.getUser().getRealName()));
    }

    /** 单独切换套餐上下架状态，不覆盖页面未提交的其他套餐字段。 */
    @PatchMapping("/{id}/status")
    public ResponseResult<Map<String, Object>> updateStatus(@PathVariable Long id,
            @RequestBody Map<String, Object> body, Authentication authentication) {
        LoginUser operator = operator(authentication);
        return new ResponseResult<>(200, service.updateStatus(id, body, operator.getUser().getId(),
                operator.getUser().getRealName()));
    }

    /** 从安全上下文读取真实监控账号，确保所有套餐写操作均可追溯。 */
    private LoginUser operator(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser))
            throw new BusinessException(401, "请先登录");
        return (LoginUser) authentication.getPrincipal();
    }
}
