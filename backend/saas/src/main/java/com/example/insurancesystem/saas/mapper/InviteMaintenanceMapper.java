package com.example.insurancesystem.saas.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 维护本地邀请码生命周期，供每日清理任务在事务内调用。
 * 仅清理创建超过七天且已过期或次数耗尽的记录，使用逻辑删除保留归档前的数据。
 */
@Mapper
public interface InviteMaintenanceMapper {
  /**
   * 标记满足保留期和失效条件的邀请码，返回本轮更新行数。
   * 无外部参数；普通注解 SQL 使用原始比较符号，不使用 XML 实体转义。
   */
  @Update(
      "UPDATE tenant_invite_code SET status=2,deleted=1,updated_at=NOW() "
          + "WHERE deleted=0 AND created_at<DATE_SUB(NOW(),INTERVAL 7 DAY) "
          + "AND ((expires_at IS NOT NULL AND expires_at<NOW()) "
          + "OR (max_use_count IS NOT NULL AND used_count>=max_use_count))")
  int deleteExpiredOrExhaustedInvites();
}
