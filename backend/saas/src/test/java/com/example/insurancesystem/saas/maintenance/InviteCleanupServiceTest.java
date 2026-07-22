package com.example.insurancesystem.saas.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.insurancesystem.saas.mapper.InviteMaintenanceMapper;
import org.junit.jupiter.api.Test;

class InviteCleanupServiceTest {
  @Test
  void returnsTheNumberOfSoftDeletedInvites() {
    InviteMaintenanceMapper mapper = mock(InviteMaintenanceMapper.class);
    when(mapper.deleteExpiredOrExhaustedInvites()).thenReturn(3);

    int deleted = new InviteCleanupService(mapper).cleanup();

    assertEquals(3, deleted);
    verify(mapper).deleteExpiredOrExhaustedInvites();
  }
}
