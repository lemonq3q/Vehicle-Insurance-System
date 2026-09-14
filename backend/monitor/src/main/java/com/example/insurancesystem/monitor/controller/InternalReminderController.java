package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.monitor.domain.ReminderMergeRequest;
import com.example.insurancesystem.monitor.domain.ReminderDeleteRequest;
import com.example.insurancesystem.monitor.service.MonitorReminderService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅供 SaaS 维护任务访问的监控提醒入口。接口不接受终端用户 JWT，通过服务间共享密钥鉴权；
 * 当前包体尚未开放客服端业务接口，避免监控账号体系完成前暴露未受控能力。
 */
@RestController
@RequestMapping("/internal/reminders")
public class InternalReminderController {
    private final MonitorReminderService service;
    private final String internalSecret;

    public InternalReminderController(MonitorReminderService service,
            @Value("${maintenance.participant.internal-secret}") String internalSecret) {
        this.service = service;
        this.internalSecret = internalSecret;
    }

    /** 校验共享密钥后幂等合并提醒，并把实际动作返回给 SaaS 维护日志。 */
    @PostMapping("/merge")
    public ResponseResult<?> merge(@RequestHeader("X-Maintenance-Secret") String secret,
            @RequestBody ReminderMergeRequest request) {
        if (!internalSecret.equals(secret)) throw new BusinessException(403, "内部调用凭证无效");
        return new ResponseResult<>(200, "操作成功", Map.of("action", service.merge(request)));
    }

    /** 校验共享密钥后幂等删除已恢复的自动续费套餐下架提醒。 */
    @PostMapping("/delete")
    public ResponseResult<?> delete(@RequestHeader("X-Maintenance-Secret") String secret,
            @RequestBody ReminderDeleteRequest request) {
        if (!internalSecret.equals(secret)) throw new BusinessException(403, "内部调用凭证无效");
        if (request == null) throw new BusinessException(400, "提醒清理参数不能为空");
        return new ResponseResult<>(200, "操作成功",
                Map.of("action", service.deleteAutoRenewPlanUnavailable(request.enterpriseId, request.reminderKey)));
    }
}
