package com.example.insurancesystem.saas.maintenance;

import com.example.insurancesystem.saas.mapper.InviteMaintenanceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InviteCleanupService {
  private final InviteMaintenanceMapper mapper;

  public InviteCleanupService(InviteMaintenanceMapper mapper) {
    this.mapper = mapper;
  }

  @Transactional
  public int cleanup() {
    return mapper.deleteExpiredOrExhaustedInvites();
  }
}
