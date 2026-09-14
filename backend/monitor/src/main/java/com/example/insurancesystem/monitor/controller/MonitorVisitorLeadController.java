package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.monitor.service.MonitorVisitorLeadService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控后台游客信息只读接口，复用监控系统 JWT 鉴权，不向匿名调用方暴露任何线索列表或联系方式。
 * 当前业务只允许按编号查询，因此接口不接受姓名、联系方式、角色等扩展筛选条件。
 */
@RestController
@RequestMapping("/monitor/visitor-leads")
public class MonitorVisitorLeadController {
    private final MonitorVisitorLeadService service;

    public MonitorVisitorLeadController(MonitorVisitorLeadService service) { this.service = service; }

    /** 按可选完整游客编号返回分页线索，未传编号时展示最新提交记录。 */
    @GetMapping
    public ResponseResult<Map<String, Object>> page(@RequestParam(required = false) String leadNo,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "10") int pageSize) {
        return new ResponseResult<>(200, service.page(leadNo, pageNo, pageSize));
    }
}
