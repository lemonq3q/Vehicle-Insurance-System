package com.example.insurancesystem.saas.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InviteMaintenanceMapper {
  @Update(
      "UPDATE tenant_invite_code SET status=2,deleted=1,updated_at=NOW() "
          + "WHERE deleted=0 AND created_at&lt;DATE_SUB(NOW(),INTERVAL 7 DAY) "
          + "AND ((expires_at IS NOT NULL AND expires_at&lt;NOW()) "
          + "OR (max_use_count IS NOT NULL AND used_count&gt;=max_use_count))")
  int deleteExpiredOrExhaustedInvites();
}
