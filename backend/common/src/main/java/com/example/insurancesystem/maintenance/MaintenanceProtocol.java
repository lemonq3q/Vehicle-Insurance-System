package com.example.insurancesystem.maintenance;

import com.example.insurancesystem.system.MaintenanceState;
import java.time.LocalDate;

/**
 * C 与业务服务之间的内部 HTTP 协议模型。普通 POJO 保持 Spring Boot 2.5/Jackson 的兼容性，
 * 所有改变维护状态的请求都携带 runId 以隔离重复和迟到消息。
 */
public final class MaintenanceProtocol {
    private MaintenanceProtocol() { }

    public static class StartRequest {
        public String runId;
        public LocalDate businessDate;
        public long leaseTimeoutSeconds;
    }

    public static class HeartbeatRequest { public String runId; }

    public static class ExecuteRequest {
        public String runId;
        public String taskId;
        public LocalDate businessDate;
        public long deadlineEpochMillis;
    }

    public static class CancelRequest {
        public String runId;
        public String taskId;
    }

    public static class FinishRequest { public String runId; }

    public static class Response {
        public boolean accepted;
        public String message;
        public MaintenanceState state;
        public MaintenanceTaskResult taskResult;

        public static Response of(boolean accepted, String message, MaintenanceState state) {
            Response response = new Response();
            response.accepted = accepted;
            response.message = message;
            response.state = state;
            return response;
        }
    }
}
