package com.example.insurancesystem.maintenance;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 暴露给协调服务 C 的内部维护接口。接口绕过普通维护过滤器，但每次调用都校验独立共享密钥，
 * 避免外部用户通过内部路径改变服务状态或执行维护代码。
 */
@RestController
@RequestMapping("/internal/maintenance")
public class MaintenanceParticipantController {
    private final MaintenanceParticipantService service;
    private final MaintenanceParticipantProperties properties;

    public MaintenanceParticipantController(MaintenanceParticipantService service,
                                            MaintenanceParticipantProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping("/start")
    public MaintenanceProtocol.Response start(@RequestHeader("X-Maintenance-Secret") String secret,
                                              @RequestBody MaintenanceProtocol.StartRequest request) {
        authenticate(secret);
        return service.start(request);
    }

    @PostMapping("/heartbeat")
    public MaintenanceProtocol.Response heartbeat(@RequestHeader("X-Maintenance-Secret") String secret,
                                                  @RequestBody MaintenanceProtocol.HeartbeatRequest request) {
        authenticate(secret);
        return service.heartbeat(request.runId);
    }

    @PostMapping("/execute")
    public MaintenanceProtocol.Response execute(@RequestHeader("X-Maintenance-Secret") String secret,
                                                @RequestBody MaintenanceProtocol.ExecuteRequest request) {
        authenticate(secret);
        return service.execute(request);
    }

    @PostMapping("/cancel")
    public MaintenanceProtocol.Response cancel(@RequestHeader("X-Maintenance-Secret") String secret,
                                               @RequestBody MaintenanceProtocol.CancelRequest request) {
        authenticate(secret);
        return service.requestCancel(request);
    }

    @PostMapping("/finish")
    public MaintenanceProtocol.Response finish(@RequestHeader("X-Maintenance-Secret") String secret,
                                               @RequestBody MaintenanceProtocol.FinishRequest request) {
        authenticate(secret);
        return service.finish(request.runId);
    }

    @GetMapping("/status")
    public MaintenanceProtocol.Response status(@RequestHeader("X-Maintenance-Secret") String secret) {
        authenticate(secret);
        return service.status();
    }

    private void authenticate(String secret) {
        if (!properties.getInternalSecret().equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid maintenance secret");
        }
    }
}
