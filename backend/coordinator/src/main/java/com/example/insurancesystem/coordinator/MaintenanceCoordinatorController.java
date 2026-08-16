package com.example.insurancesystem.coordinator;

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

    @PostMapping("/run")
    public Map<String, Object> run(@RequestHeader("X-Maintenance-Secret") String secret) {
        authenticate(secret);
        boolean accepted = service.runNow();
        return Map.of("accepted", accepted);
    }

    private void authenticate(String secret) {
        if (!properties.getInternalSecret().equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid maintenance secret");
        }
    }
}
