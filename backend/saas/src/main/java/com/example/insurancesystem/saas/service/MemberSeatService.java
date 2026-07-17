package com.example.insurancesystem.saas.service;

import com.example.insurancesystem.saas.mapper.EnterpriseMapper;
import com.example.insurancesystem.saas.mapper.SubscriptionMaintenanceMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MemberSeatService {
  private final EnterpriseMapper enterpriseMapper;
  private final SubscriptionMaintenanceMapper maintenanceMapper;

  public MemberSeatService(
      EnterpriseMapper enterpriseMapper, SubscriptionMaintenanceMapper maintenanceMapper) {
    this.enterpriseMapper = enterpriseMapper;
    this.maintenanceMapper = maintenanceMapper;
  }

  public void synchronize(Long enterpriseId, int userLimit) {
    int normalizedLimit = Math.max(0, userLimit);
    int activeCount = enterpriseMapper.countActiveMembers(enterpriseId);
    if (activeCount > normalizedLimit) {
      update(
          maintenanceMapper.findMembersToDisable(enterpriseId, activeCount - normalizedLimit), 0);
    } else if (activeCount < normalizedLimit) {
      update(maintenanceMapper.findMembersToEnable(enterpriseId, normalizedLimit - activeCount), 1);
    }
  }

  public void disableAll(Long enterpriseId) {
    maintenanceMapper.disableAllMembers(enterpriseId);
  }

  private void update(List<Long> memberIds, int status) {
    if (!memberIds.isEmpty()) maintenanceMapper.updateMemberStatuses(memberIds, status);
  }
}
