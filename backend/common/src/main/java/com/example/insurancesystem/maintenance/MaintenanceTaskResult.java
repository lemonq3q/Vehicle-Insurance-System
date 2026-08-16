package com.example.insurancesystem.maintenance;

/** 参与端返回给协调器的任务终态，结果与服务 READY 状态相互独立。 */
public enum MaintenanceTaskResult {
    SUCCEEDED,
    FAILED,
    CANCELLED
}
