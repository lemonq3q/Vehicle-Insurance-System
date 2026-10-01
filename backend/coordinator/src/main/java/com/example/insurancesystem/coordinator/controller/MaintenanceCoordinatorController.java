package com.example.insurancesystem.coordinator.controller;

import com.example.insurancesystem.coordinator.config.CoordinatorProperties;
import com.example.insurancesystem.coordinator.service.MaintenanceCoordinatorService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/**
 * C 的运维控制接口，用于查询当前运行和在非定时场景手工触发维护。接口使用与参与端相同的内部密钥，
 * 生产环境应仅通过内网或运维网关访问。
 */
@RestController
@RequestMapping("/internal/coordinator")
public class MaintenanceCoordinatorController {
    private final MaintenanceCoordinatorService service;
    private final CoordinatorProperties properties;

    public MaintenanceCoordinatorController(MaintenanceCoordinatorService service,
                                            CoordinatorProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestHeader("X-Maintenance-Secret") String secret) {
        authenticate(secret);
        return service.status();
    }

    /**
     * 无需用户登录，使用查询密钥接受手动维护请求并立即返回启动或重复提示。
     * GET 会产生维护副作用，仅限运维调用；密钥不得写入日志或公开链接。
     * 禁用或计划错误返回真实 503，不把拒绝启动误报为正在维护。
     */
    @GetMapping("/run")
    public Map<String, Object> run(@RequestParam("secret") String secret,
                                  javax.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        authenticate(secret);
        try {
            boolean accepted = service.startManualRun();
            return Map.of("accepted", accepted, "message", accepted ? "维护已启动" : "维护正在进行中");
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "维护无法启动，请检查协调器配置", exception);
        }
    }

    private void authenticate(String secret) {
        if (!properties.getInternalSecret().equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid maintenance secret");
        }
    }
}
