package com.example.insurancesystem.saas.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SubscriptionMaintenanceMapper {
  @Select("SELECT id FROM saas_subscription WHERE status=1 AND end_at<=NOW() ORDER BY end_at,id")
  List<Long> findDueSubscriptionIds();

  @Select("SELECT * FROM saas_subscription WHERE id=#{id} FOR UPDATE")
  Map<String, Object> lockSubscription(Long id);

  @Select("SELECT owner_user_id FROM tenant_enterprise WHERE id=#{enterpriseId} AND deleted=0")
  Long findOwnerUserId(Long enterpriseId);

  @Update(
      "UPDATE saas_subscription SET status=2,user_limit=0,ocr_quota=0,request_quota=0,auto_renew_enabled=0,auto_renew_plan_id=NULL,next_renew_at=NULL,cancel_auto_renew_at=NOW(),updated_at=NOW() WHERE id=#{id} AND status=1")
  int expireSubscription(Long id);

  @Update(
      "UPDATE tenant_member SET status=0,updated_at=NOW(),updated_by=NULL WHERE enterprise_id=#{enterpriseId} AND deleted=0 AND status IN (1,2)")
  int disableAllMembers(Long enterpriseId);

  @Select(
      "SELECT id FROM tenant_member WHERE enterprise_id=#{enterpriseId} AND deleted=0 AND status=1 "
          + "ORDER BY FIELD(role_code,'ISSUER','ADMIN','OWNER'),joined_at DESC,id DESC LIMIT #{count}")
  List<Long> findMembersToDisable(
      @Param("enterpriseId") Long enterpriseId, @Param("count") int count);

  @Select(
      "SELECT id FROM tenant_member WHERE enterprise_id=#{enterpriseId} AND deleted=0 AND status=0 "
          + "ORDER BY FIELD(role_code,'OWNER','ADMIN','ISSUER'),joined_at,id LIMIT #{count}")
  List<Long> findMembersToEnable(
      @Param("enterpriseId") Long enterpriseId, @Param("count") int count);

  @Update(
      "<script>UPDATE tenant_member SET status=#{status},updated_at=NOW(),updated_by=NULL WHERE id IN "
          + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
  int updateMemberStatuses(@Param("ids") List<Long> ids, @Param("status") int status);
}
