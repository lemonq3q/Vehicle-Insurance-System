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
      "UPDATE tenant_member SET role_code=#{roleCode},status=1,joined_by_invite_id=#{inviteId},joined_at=NOW(),updated_at=NOW(),updated_by=#{operatorId} "
          + "WHERE enterprise_id=#{enterpriseId} AND user_id=#{userId} AND deleted=0 AND status=3")
  int reactivateMember(Map<String, Object> member);

  @Insert(
      "INSERT INTO saas_wallet(enterprise_id,balance_amount,frozen_amount,currency,status,created_at,updated_at,updated_by,deleted) "
          + "VALUES(#{enterpriseId},0,0,'CNY',1,NOW(),NOW(),#{userId},0)")
  int insertWallet(@Param("enterpriseId") Long enterpriseId, @Param("userId") Long userId);

  @Update(
      "UPDATE tenant_enterprise SET name=#{name},contact_name=#{contactName},contact_phone=#{contactPhone},updated_at=NOW(),updated_by=#{userId} WHERE id=#{id} AND deleted=0")
  int updateEnterprise(Map<String, Object> enterprise);

  @Select("SELECT * FROM tenant_invite_code WHERE code=#{code} AND deleted=0 LIMIT 1")
  Map<String, Object> findInviteByCode(String code);

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

  @Update(
      "UPDATE tenant_member SET role_code=#{roleCode},updated_at=NOW(),updated_by=#{userId} WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND deleted=0")
  int updateMemberRole(Map<String, Object> member);

  @Update(
      "UPDATE tenant_member SET status=#{status},updated_at=NOW(),updated_by=#{userId} WHERE id=#{id} AND enterprise_id=#{enterpriseId} AND deleted=0")
  int updateMemberStatus(Map<String, Object> member);

  @Select(
      "SELECT COUNT(1) FROM tenant_member WHERE enterprise_id=#{enterpriseId} AND status=1 AND deleted=0")
  int countActiveMembers(Long enterpriseId);

  @Select("SELECT id FROM tenant_enterprise WHERE id=#{enterpriseId} AND deleted=0 FOR UPDATE")
  Long lockEnterprise(Long enterpriseId);

  @Select(
      "SELECT s.user_limit FROM saas_subscription s WHERE s.enterprise_id=#{enterpriseId} AND s.status=1 AND s.end_at&gt;NOW() ORDER BY s.end_at DESC LIMIT 1")
  Integer findCurrentUserLimit(Long enterpriseId);

  @Update(
      "UPDATE tenant_enterprise SET owner_user_id=#{toUserId},updated_at=NOW(),updated_by=#{fromUserId} WHERE id=#{enterpriseId} AND owner_user_id=#{fromUserId}")
  int updateOwner(Map<String, Object> transfer);

  @Insert(
      "INSERT INTO tenant_owner_transfer_log(enterprise_id,from_user_id,to_user_id,status,transferred_at,created_by,remark) VALUES(#{enterpriseId},#{fromUserId},#{toUserId},1,NOW(),#{fromUserId},'门户主动转让')")
  int insertTransferLog(Map<String, Object> transfer);

  @Update(
      "UPDATE tenant_member SET role_code='ADMIN',updated_at=NOW(),updated_by=#{fromUserId} WHERE enterprise_id=#{enterpriseId} AND user_id=#{fromUserId} AND deleted=0")
  int demoteOldOwner(Map<String, Object> transfer);

  @Update(
      "UPDATE tenant_member SET role_code='OWNER',status=1,updated_at=NOW(),updated_by=#{fromUserId} WHERE id=#{toMemberId} AND enterprise_id=#{enterpriseId} AND deleted=0")
  int promoteNewOwner(Map<String, Object> transfer);

  @Select(
      "SELECT l.id,l.enterprise_id,l.from_user_id,l.to_user_id,l.status,l.transferred_at,l.remark,"
          + "from_user.real_name from_user_name,to_user.real_name to_user_name "
          + "FROM tenant_owner_transfer_log l "
          + "LEFT JOIN tenant_user from_user ON from_user.id=l.from_user_id "
          + "LEFT JOIN tenant_user to_user ON to_user.id=l.to_user_id "
          + "WHERE l.enterprise_id=#{enterpriseId} ORDER BY l.transferred_at DESC,l.id DESC LIMIT #{offset},#{pageSize}")
  List<Map<String, Object>> findTransferLogs(
      @Param("enterpriseId") Long enterpriseId,
      @Param("offset") int offset,
      @Param("pageSize") int pageSize);

  @Select("SELECT COUNT(1) FROM tenant_owner_transfer_log WHERE enterprise_id=#{enterpriseId}")
  long countTransferLogs(Long enterpriseId);
}
