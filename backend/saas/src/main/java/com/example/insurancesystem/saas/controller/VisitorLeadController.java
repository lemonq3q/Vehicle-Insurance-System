package com.example.insurancesystem.saas.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.saas.service.VisitorLeadService;
import com.example.insurancesystem.saas.service.VisitorLeadSubmissionGuard;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SaaS 官网匿名游客线索接口，仅开放新增能力。每次请求先执行服务端来源白名单和短周期频率校验，
 * 再进入业务校验与持久化，避免伪造页面来源或同一客户端连续灌入数据。
 */
@RestController
@RequestMapping("/portal/visitor-leads")
public class VisitorLeadController {
    private final VisitorLeadSubmissionGuard guard;
    private final VisitorLeadService service;

    public VisitorLeadController(VisitorLeadSubmissionGuard guard, VisitorLeadService service) {
        this.guard = guard;
        this.service = service;
    }

    /**
     * 接收官网“联系我们”表单并返回游客编号。HttpServletRequest 只供保护组件读取服务端可验证来源，
     * 请求体中的任何来源或 IP 字段都不会被信任；频率窗口内重复调用返回业务码 429。
     */
    @PostMapping
    public ResponseResult<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        guard.verifyAndAcquire(request);
        return new ResponseResult<>(200, "提交成功", service.create(body));
    }
}
