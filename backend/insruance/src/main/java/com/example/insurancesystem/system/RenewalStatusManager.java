package com.example.insurancesystem.system;

import com.example.insurancesystem.mapper.WorkorderMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
/**
 * 维护工单续保提醒状态，使已经跨过当前提醒周期的临时状态恢复为可再次计算状态。
 */
public class RenewalStatusManager {

    private final WorkorderMapper workorderMapper;

    /**
     * 注入负责批量更新续保状态的工单数据访问组件。
     */
    public RenewalStatusManager(WorkorderMapper workorderMapper) {
        this.workorderMapper = workorderMapper;
    }

    @Transactional
    /**
     * 批量重置已经过期的续保状态，并返回实际更新的工单数量。
     */
    public int resetExpiredStatuses() {
        return workorderMapper.resetExpiredRenewalStatuses();
    }
}
