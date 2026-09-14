package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.monitor.service.MonitorReminderService;
import java.util.Map;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控平台提醒处理接口，负责组合筛选、分页读取和人工处理确认。所有入口使用监控账号 JWT；
 * 类型字典只负责解释和筛选，提醒事实仍由 SaaS 每日维护任务产生并经内部接口合并。
 */
@RestController
@RequestMapping("/monitor/reminders")
public class MonitorReminderController {
    private final MonitorReminderService service;

    public MonitorReminderController(MonitorReminderService service) { this.service = service; }

    /** 按严重等级、类别、一个或多个具体类型、企业和处理状态组合查询提醒。 */
    @GetMapping
    public ResponseResult<Map<String, Object>> page(@RequestParam(required = false) String severity,
            @RequestParam(required = false) String categoryCode, @RequestParam(required = false) String typeCode,
            @RequestParam(required = false) String typeCodes,
            @RequestParam(required = false) Long enterpriseId, @RequestParam(required = false) Integer processStatus,
            @RequestParam(defaultValue = "1") int pageNo, @RequestParam(defaultValue = "10") int pageSize) {
        String resolvedTypeCodes = typeCodes == null || typeCodes.isBlank() ? typeCode : typeCodes;
        return new ResponseResult<>(200, service.page(severity, categoryCode, resolvedTypeCodes, enterpriseId, processStatus, pageNo, pageSize));
    }

    /** 一次返回级联类别/类型和有提醒企业选项，降低页面初始化数据库访问次数。 */
    @GetMapping("/filter-options")
    public ResponseResult<Map<String, Object>> filterOptions() { return new ResponseResult<>(200, service.filterOptions()); }

    /** 根据用户实时输入的名称或编码返回最多 20 个企业候选项，不在页面初始化时加载企业全集。 */
    @GetMapping("/enterprise-options")
    public ResponseResult<List<Map<String, Object>>> enterpriseOptions(@RequestParam String keyword) {
        return new ResponseResult<>(200, service.searchEnterprises(keyword));
    }

    /** 经确认弹窗提交后，以登录监控用户身份和页面版本号将提醒标记为已处理。 */
    @PatchMapping("/{id}/processed")
    public ResponseResult<?> markProcessed(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) throw new BusinessException(401, "请先登录");
        Object revision = body == null ? null : body.get("revision");
        Integer value = revision instanceof Number ? ((Number) revision).intValue() : null;
        String remark = body == null || body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        Long userId = loginUser.getUser().getId();
        service.markProcessed(id, value, userId, loginUser.getUser().getRealName(), remark);
        return new ResponseResult<>(200, "提醒已标记为已处理", null);
    }

    /** 将已处理提醒恢复到待处理队列，并以页面版本号防止覆盖并发状态变化。 */
    @PatchMapping("/{id}/unprocessed")
    public ResponseResult<?> restoreUnprocessed(@PathVariable Long id, @RequestBody Map<String, Object> body,
            Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) throw new BusinessException(401, "请先登录");
        Object revision = body == null ? null : body.get("revision");
        Integer value = revision instanceof Number ? ((Number) revision).intValue() : null;
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        service.restoreUnprocessed(id, value, loginUser.getUser().getId(), loginUser.getUser().getRealName());
        return new ResponseResult<>(200, "提醒已恢复为待处理", null);
    }
}
