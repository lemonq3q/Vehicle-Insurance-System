package com.example.insurancesystem.system;

import com.example.insurancesystem.mapper.WorkorderMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RenewalStatusManager {

    private final WorkorderMapper workorderMapper;

    public RenewalStatusManager(WorkorderMapper workorderMapper) {
        this.workorderMapper = workorderMapper;
    }

    @Transactional
    public int resetExpiredStatuses() {
        return workorderMapper.resetExpiredRenewalStatuses();
    }
}
