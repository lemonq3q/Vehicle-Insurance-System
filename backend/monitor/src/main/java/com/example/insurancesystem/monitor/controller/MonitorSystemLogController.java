package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.monitor.service.MonitorSystemLogQueryService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控平台统一系统日志查询入口。日志包含操作人、IP、前后快照和异常摘要等敏感运营信息，
 * 因此只向监控平台 ADMIN 角色开放；前端菜单隐藏不代替此服务端权限校验。
 */
@RestController
@RequestMapping("/monitor/system-logs")
@PreAuthorize("hasRole('ADMIN')")
public class MonitorSystemLogController {
    private final MonitorSystemLogQueryService service;

    public MonitorSystemLogController(MonitorSystemLogQueryService service) { this.service = service; }

    /**
     * 按日志类别、严重等级和发生日期的闭区间分页查询。
     * 空条件表示不限制；日期使用 yyyy-MM-dd，结束日在服务层转为次日零点的开区间以覆盖全天。
     */
    @GetMapping
    public ResponseResult<Map<String, Object>> page(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return new ResponseResult<>(200, service.page(category, severity, startDate, endDate, pageNo, pageSize));
    }
}
