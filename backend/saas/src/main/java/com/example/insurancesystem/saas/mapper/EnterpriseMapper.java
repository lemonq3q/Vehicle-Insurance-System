package com.example.insurancesystem.saas.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface EnterpriseMapper {
  @Select(
      "SELECT m.id,m.enterprise_id,m.user_id,m.role_code,m.status,m.joined_at,u.username,u.phone,u.real_name "
          + "FROM tenant_member m JOIN tenant_user u ON u.id=m.user_id AND u.deleted=0 "
          + "WHERE m.user_id=#{userId} AND m.deleted=0 AND m.status IN (0,1,2) ORDER BY m.joined_at DESC LIMIT 1")
  Map<String, Object> findCurrentMember(Long userId);

  @Select(
      "SELECT m.id,m.enterprise_id,m.user_id,m.role_code,m.status,m.joined_at,u.username,u.phone,u.real_name "
          + "FROM tenant_member m JOIN tenant_user u ON u.id=m.user_id AND u.deleted=0 "
          + "WHERE m.enterprise_id=#{enterpriseId} AND m.user_id=#{userId} AND m.deleted=0 LIMIT 1")
  Map<String, Object> findMemberByUser(
      @Param("enterpriseId") Long enterpriseId, @Param("userId") Long userId);

  @Select(
      "SELECT m.id,m.enterprise_id,m.user_id,m.role_code,m.status,m.deleted,m.joined_at,u.username,u.phone,u.real_name "
          + "FROM tenant_member m JOIN tenant_user u ON u.id=m.user_id AND u.deleted=0 "
          + "WHERE m.enterprise_id=#{enterpriseId} AND m.user_id=#{userId} LIMIT 1")
  Map<String, Object> findMemberByUserIncludingDeleted(
      @Param("enterpriseId") Long enterpriseId, @Param("userId") Long userId);

  @Select(
      "SELECT e.id,e.name,e.code,e.owner_user_id,e.contact_name,e.contact_phone,e.status,e.source,e.created_at,e.updated_at "
          + "FROM tenant_enterprise e WHERE e.id=#{id} AND e.deleted=0")
  Map<String, Object> findEnterprise(Long id);

  @Insert(
      "INSERT INTO tenant_enterprise(name,code,owner_user_id,contact_name,contact_phone,status,source,created_at,updated_at,deleted) "
          + "VALUES(#{name},#{code},#{ownerUserId},#{contactName},#{contactPhone},1,1,NOW(),NOW(),0)")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertEnterprise(Map<String, Object> enterprise);

  @Insert(
      "INSERT INTO tenant_member(enterprise_id,user_id,role_code,status,joined_by_invite_id,joined_at,created_at,updated_at,updated_by,deleted) "
          + "VALUES(#{enterpriseId},#{userId},#{roleCode},#{status},#{inviteId},NOW(),NOW(),NOW(),#{operatorId},0)")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertMember(Map<String, Object> member);

  @Update(
      "UPDATE tenant_member SET role_code=#{roleCode},status=#{status},joined_by_invite_id=#{inviteId},joined_at=NOW(),updated_at=NOW(),updated_by=#{operatorId},deleted=0 "
          + "WHERE enterprise_id=#{enterpriseId} AND user_id=#{userId} AND deleted=1")
  int reactivateMember(Map<String, Object> member);

  @Insert(
      "INSERT INTO saas_wallet(enterprise_id,balance_amount,frozen_amount,currency,status,created_at,updated_at,updated_by,deleted) "
          + "VALUES(#{enterpriseId},0,0,'CNY',1,NOW(),NOW(),#{userId},0)")
  int insertWallet(@Param("enterpriseId") Long enterpriseId, @Param("userId") Long userId);

  @Insert(
      "INSERT INTO saas_subscription(enterprise_id,status,user_limit,ocr_quota,request_quota,auto_renew_enabled,created_at,updated_at) "
          + "VALUES(#{enterpriseId},0,0,0,0,0,NOW(),NOW())")
  int insertDefaultSubscription(Long enterpriseId);

  @Update(
      "UPDATE tenant_enterprise SET name=#{name},contact_name=#{contactName},contact_phone=#{contactPhone},updated_at=NOW(),updated_by=#{userId} WHERE id=#{id} AND deleted=0")
  int updateEnterprise(Map<String, Object> enterprise);

  @Select("SELECT * FROM tenant_invite_code WHERE code=#{code} AND deleted=0 LIMIT 1")
  Map<String, Object> findInviteByCode(String code);

  @Select("SELECT * FROM tenant_invite_code WHERE code=#{code} AND deleted=0 LIMIT 1 FOR UPDATE")
  Map<String, Object> lockInviteByCode(String code);

  @Select(
      "<script>SELECT * FROM tenant_invite_code WHERE enterprise_id=#{enterpriseId} AND deleted=0 ORDER BY created_at DESC "
          + "LIMIT #{offset},#{pageSize}</script>")
  List<Map<String, Object>> findInvites(
      @Param("enterpriseId") Long enterpriseId,
      @Param("offset") int offset,
      @Param("pageSize") int pageSize);

  @Select(
      "SELECT COUNT(1) FROM tenant_invite_code WHERE enterprise_id=#{enterpriseId} AND deleted=0")
  long countInvites(Long enterpriseId);

  @Insert(
      "INSERT INTO tenant_invite_code(enterprise_id,code,default_role_code,max_use_count,used_count,expires_at,status,created_by,created_at,updated_at,deleted) "
          + "VALUES(#{enterpriseId},#{code},'ISSUER',#{maxUseCount},0,#{expiresAt},1,#{userId},NOW(),NOW(),0)")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insertInvite(Map<String, Object> invite);

  @Update(
      "UPDATE tenant_invite_code SET status=3,deleted=1,updated_at=NOW() WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND deleted=0")
  int deleteInvite(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId);

  @Update(
      "UPDATE tenant_invite_code SET used_count=used_count+1,updated_at=NOW() WHERE id=#{id} AND status=1 AND deleted=0 "
          + "AND (max_use_count IS NULL OR used_count<max_use_count) AND (expires_at IS NULL OR expires_at>NOW())")
  int consumeInvite(Long id);

  @Select(
      "<script>SELECT m.id,m.enterprise_id,m.user_id,m.role_code,m.status,m.joined_at,u.username,u.phone,u.real_name "
          + "FROM tenant_member m JOIN tenant_user u ON u.id=m.user_id AND u.deleted=0 WHERE m.enterprise_id=#{enterpriseId} AND m.deleted=0 "
          + "<if test=\"keyword != null and keyword != ''\">AND (u.real_name LIKE CONCAT('%',#{keyword},'%') OR u.phone LIKE CONCAT('%',#{keyword},'%') OR u.username LIKE CONCAT('%',#{keyword},'%'))</if> "
          + "<if test=\"roleCode != null and roleCode != ''\">AND m.role_code=#{roleCode}</if><if test=\"status != null\">AND m.status=#{status}</if> "
          + "ORDER BY FIELD(m.role_code,'OWNER','ADMIN','ISSUER'),m.joined_at LIMIT #{offset},#{pageSize}</script>")
  List<Map<String, Object>> findMembers(Map<String, Object> query);

  @Select(
      "<script>SELECT COUNT(1) FROM tenant_member m JOIN tenant_user u ON u.id=m.user_id AND u.deleted=0 WHERE m.enterprise_id=#{enterpriseId} AND m.deleted=0 "
          + "<if test=\"keyword != null and keyword != ''\">AND (u.real_name LIKE CONCAT('%',#{keyword},'%') OR u.phone LIKE CONCAT('%',#{keyword},'%') OR u.username LIKE CONCAT('%',#{keyword},'%'))</if> "
          + "<if test=\"roleCode != null and roleCode != ''\">AND m.role_code=#{roleCode}</if><if test=\"status != null\">AND m.status=#{status}</if></script>")
  long countMembers(Map<String, Object> query);

  @Select(
      "SELECT m.id,m.enterprise_id,m.user_id,m.role_code,m.status,m.joined_at,u.username,u.phone,u.real_name FROM tenant_member m JOIN tenant_user u ON u.id=m.user_id WHERE m.id=#{id} AND m.enterprise_id=#{enterpriseId} AND m.deleted=0")
  Map<String, Object> findMember(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId);

  @Select(
      "SELECT m.id,m.enterprise_id,m.user_id,m.role_code,m.status,m.joined_at,u.username,u.phone,u.real_name FROM tenant_member m JOIN tenant_user u ON u.id=m.user_id WHERE m.id=#{id} AND m.enterprise_id=#{enterpriseId} AND m.deleted=0 FOR UPDATE")
  Map<String, Object> lockMember(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId);

  @Update(
      "UPDATE tenant_member SET role_code=#{roleCode},updated_at=NOW(),updated_by=#{userId} WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND deleted=0")
  int updateMemberRole(Map<String, Object> member);

  @Update(
      "UPDATE tenant_member SET status=#{status},updated_at=NOW(),updated_by=#{userId} WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND deleted=0")
  int updateMemberStatus(Map<String, Object> member);

  @Update(
      "UPDATE tenant_member SET status=0,deleted=1,updated_at=NOW(),updated_by=#{userId} WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND deleted=0")
  int softDeleteMember(Map<String, Object> member);

  @Select(
      "SELECT COUNT(1) FROM tenant_member WHERE enterprise_id=#{enterpriseId} AND status=1 AND deleted=0")
  int countActiveMembers(Long enterpriseId);

  @Select("SELECT id FROM tenant_enterprise WHERE id=#{enterpriseId} AND deleted=0 FOR UPDATE")
  Long lockEnterprise(Long enterpriseId);

  @Select("SELECT user_limit FROM saas_subscription WHERE enterprise_id=#{enterpriseId}")
  Integer findCurrentUserLimit(Long enterpriseId);

  @Update(
      "UPDATE tenant_enterprise SET owner_user_id=#{toUserId},updated_at=NOW(),updated_by=#{fromUserId} WHERE id=#{enterpriseId} AND owner_user_id=#{fromUserId}")
  int updateOwner(Map<String, Object> transfer);

  @Insert(
      "INSERT INTO tenant_member_change_log(enterprise_id,event_type,operator_user_id,target_user_id,operator_name_snapshot,target_name_snapshot,before_role_code,after_role_code,invite_id,occurred_at,remark) "
          + "VALUES(#{enterpriseId},#{eventType},#{operatorUserId},#{targetUserId},"
          + "(SELECT real_name FROM tenant_user WHERE id=#{operatorUserId}),"
          + "(SELECT real_name FROM tenant_user WHERE id=#{targetUserId}),"
          + "#{beforeRoleCode},#{afterRoleCode},#{inviteId},NOW(),#{remark})")
  int insertMemberChangeLog(Map<String, Object> change);

  @Update(
      "UPDATE tenant_member SET role_code='ADMIN',updated_at=NOW(),updated_by=#{fromUserId} WHERE enterprise_id=#{enterpriseId} AND user_id=#{fromUserId} AND deleted=0")
  int demoteOldOwner(Map<String, Object> transfer);

  @Update(
      "UPDATE tenant_member SET role_code='OWNER',status=1,updated_at=NOW(),updated_by=#{fromUserId} WHERE id=#{toMemberId} AND enterprise_id=#{enterpriseId} AND deleted=0")
  int promoteNewOwner(Map<String, Object> transfer);

  @Select(
      "<script>SELECT * FROM tenant_member_change_log WHERE enterprise_id=#{enterpriseId} "
          + "<if test=\"eventType != null and eventType != ''\">AND event_type=#{eventType}</if> "
          + "ORDER BY occurred_at DESC,id DESC LIMIT #{offset},#{pageSize}</script>")
  List<Map<String, Object>> findMemberChangeLogs(
      @Param("enterpriseId") Long enterpriseId,
      @Param("eventType") String eventType,
      @Param("offset") int offset,
      @Param("pageSize") int pageSize);

  @Select(
      "<script>SELECT COUNT(1) FROM tenant_member_change_log WHERE enterprise_id=#{enterpriseId} "
          + "<if test=\"eventType != null and eventType != ''\">AND event_type=#{eventType}</if></script>")
  long countMemberChangeLogs(
      @Param("enterpriseId") Long enterpriseId, @Param("eventType") String eventType);
}
