package com.example.insurancesystem.saas.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.saas.service.DashboardStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供当前企业门户仪表盘经营指标。接口不接受企业 ID 参数，租户范围由认证上下文强制确定，
 * 防止用户通过修改查询参数读取其他企业统计。
 */
@RestController
@RequestMapping("/portal/dashboard")
public class DashboardController {
    private final DashboardStatisticsService service;

    /**
     * 注入仪表盘聚合服务，Controller 只负责统一响应封装。
     */
    public DashboardController(DashboardStatisticsService service) {
        this.service = service;
    }

    /**
     * 查询本月与上月工单、盈利环比，本月客户及一周内续保提醒。
     */
    @GetMapping("/statistics")
    public ResponseResult<?> statistics() {
        return new ResponseResult<>(200, "操作成功", service.currentEnterpriseDashboard());
    }
}
