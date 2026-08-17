package com.example.insurancesystem.coordinator.domain;

/** C 对单个计划任务的权威状态；所有终态均可用于判断下游 dependsOn。 */
public enum CoordinatorTaskStatus {
    PENDING,
    DISPATCHED,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    CANCELLED,
    SKIPPED
}
