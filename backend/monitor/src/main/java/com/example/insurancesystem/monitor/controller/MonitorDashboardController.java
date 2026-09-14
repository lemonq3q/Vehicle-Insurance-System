package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.monitor.service.MonitorDashboardService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营监控仪表盘只读接口。接口按独立刷新区域拆分，使系统调用、OCR 和排行可以使用不同时间范围；
 * 每个入口在服务层固定只触发一条 SQL，顶部刷新可由前端并行请求，减少串行等待。
 */
@RestController
@RequestMapping("/monitor/dashboard")
public class MonitorDashboardController {
    private final MonitorDashboardService dashboardService;

    /** 注入仪表盘聚合服务。 */
    public MonitorDashboardController(MonitorDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 返回当前月与上月五项核心指标及环比。 */
    @GetMapping("/summary")
    public ResponseResult<Map<String, Object>> summary() {
        return new ResponseResult<>(200, dashboardService.summary());
    }

    /** 返回包含本月在内近十二个月的已支付充值流水。 */
    @GetMapping("/recharge-trend")
    public ResponseResult<Map<String, Object>> rechargeTrend() {
        return new ResponseResult<>(200, dashboardService.rechargeTrend());
    }

    /** 返回指定指标和独立时间范围的系统级趋势。 */
    @GetMapping("/usage-trend")
    public ResponseResult<Map<String, Object>> usageTrend(@RequestParam String metric,
                                                          @RequestParam(defaultValue = "30d") String range) {
        return new ResponseResult<>(200, dashboardService.usageTrend(metric, range));
    }

    /** 返回指定范围内按系统调用量排序的企业 Top 5/10/20。 */
    @GetMapping("/enterprise-ranking")
    public ResponseResult<Map<String, Object>> ranking(@RequestParam(defaultValue = "30d") String range,
                                                       @RequestParam(defaultValue = "5") int top) {
        return new ResponseResult<>(200, dashboardService.ranking(range, top));
    }
}
