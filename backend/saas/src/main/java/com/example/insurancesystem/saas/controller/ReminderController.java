package com.example.insurancesystem.saas.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.saas.service.ReminderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 企业门户近期提醒查询入口；时间范围和排序均由后端固定，防止不同页面形成不一致的风险优先级。 */
@RestController
@RequestMapping("/portal/reminders")
public class ReminderController {
    private final ReminderService service;
    public ReminderController(ReminderService service) { this.service = service; }

    /** 返回当前企业最近一个自然月、未失效的提醒，严重程度优先且同级最新在前。 */
    @GetMapping("/recent")
    public ResponseResult<?> recent() {
        return new ResponseResult<>(200, "操作成功", service.recent());
    }
}
