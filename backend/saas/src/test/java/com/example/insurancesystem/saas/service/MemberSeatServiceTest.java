package com.example.insurancesystem.saas.service;

import static org.mockito.Mockito.*;

import com.example.insurancesystem.saas.mapper.EnterpriseMapper;
import com.example.insurancesystem.saas.mapper.SubscriptionMaintenanceMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemberSeatServiceTest {
  private EnterpriseMapper enterpriseMapper;
  private SubscriptionMaintenanceMapper maintenanceMapper;
  private MemberSeatService service;

  @BeforeEach
  void setUp() {
    enterpriseMapper = mock(EnterpriseMapper.class);
    maintenanceMapper = mock(SubscriptionMaintenanceMapper.class);
    service = new MemberSeatService(enterpriseMapper, maintenanceMapper);
  }

  @Test
  void disablesOnlyTheExcessMembersSelectedByRolePriority() {
    when(enterpriseMapper.countActiveMembers(20L)).thenReturn(6);
    when(maintenanceMapper.findMembersToDisable(20L, 2)).thenReturn(List.of(5L, 6L));

    service.synchronize(20L, 4);

    verify(maintenanceMapper).findMembersToDisable(20L, 2);
    verify(maintenanceMapper).updateMemberStatuses(List.of(5L, 6L), 0);
    verify(maintenanceMapper, never()).findMembersToEnable(anyLong(), anyInt());
  }

  @Test
  void enablesAvailableMembersUpToTheNewLimit() {
    when(enterpriseMapper.countActiveMembers(20L)).thenReturn(2);
    when(maintenanceMapper.findMembersToEnable(20L, 3)).thenReturn(List.of(1L, 2L));

    service.synchronize(20L, 5);

    verify(maintenanceMapper).findMembersToEnable(20L, 3);
    verify(maintenanceMapper).updateMemberStatuses(List.of(1L, 2L), 1);
    verify(maintenanceMapper, never()).findMembersToDisable(anyLong(), anyInt());
  }

  @Test
  void disablesAllActiveMembersWhenTheCurrentSubscriptionLimitIsZero() {
    when(enterpriseMapper.countActiveMembers(20L)).thenReturn(3);
    when(maintenanceMapper.findMembersToDisable(20L, 3)).thenReturn(List.of(1L, 2L, 3L));

    service.synchronize(20L, 0);

    verify(maintenanceMapper).findMembersToDisable(20L, 3);
    verify(maintenanceMapper).updateMemberStatuses(List.of(1L, 2L, 3L), 0);
    verify(maintenanceMapper, never()).findMembersToEnable(anyLong(), anyInt());
  }
}
