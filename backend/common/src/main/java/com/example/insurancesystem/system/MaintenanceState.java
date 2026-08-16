package com.example.insurancesystem.system;

/**
 * 业务服务参与分布式维护时的本地状态。状态只描述流量和任务接收能力，任务结果由独立执行记录维护；
 * STANDALONE 在当前周期内不可逆，防止协调器恢复后的迟到命令打断单机任务。
 */
public enum MaintenanceState {
    NORMAL,
    DRAINING,
    READY,
    STANDALONE,
    RELEASING
}
