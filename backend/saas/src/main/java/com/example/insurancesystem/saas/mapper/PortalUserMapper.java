package com.example.insurancesystem.saas.mapper;

import com.example.insurancesystem.domain.user.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PortalUserMapper {

  @Select(
      "SELECT * FROM tenant_user WHERE deleted=0 AND LOWER(TRIM(email))=#{login} AND (SELECT COUNT(*) FROM tenant_user u WHERE u.deleted=0 AND LOWER(TRIM(u.email))=#{login})=1 LIMIT 1")
  User findForLogin(String login);

  @Select("SELECT * FROM tenant_user WHERE id=#{id} AND deleted=0 LIMIT 1")
  User findById(Long id);

  @Select(
      "SELECT id,username,email,phone,real_name,id_num,avatar_file_id,status,last_login_time,created_at,updated_at,CASE WHEN wechat_open_id IS NOT NULL THEN TRUE ELSE FALSE END AS wechat_bound,wechat_nickname,wechat_bound_at "
          + "FROM tenant_user WHERE id=#{id} AND deleted=0")
  Map<String, Object> findProfile(Long id);

  @Select("SELECT COUNT(1) FROM tenant_user WHERE deleted=0 AND phone=#{phone} AND id<>#{id}")
  int countPhoneExcept(@Param("phone") String phone, @Param("id") Long id);

  @Update("UPDATE tenant_user SET last_login_time=#{time},updated_at=NOW() WHERE id=#{id}")
  int updateLastLogin(@Param("id") Long id, @Param("time") LocalDateTime time);

  @Update(
      "UPDATE tenant_user SET phone=#{phone},real_name=#{realName},id_num=#{idNum},updated_at=NOW(),updated_by=#{id} "
          + "WHERE id=#{id} AND deleted=0")
  int updateProfile(Map<String, Object> profile);

  @Select(
      "SELECT e.id,e.name,e.code,e.owner_user_id,e.contact_name,e.contact_phone,e.status,e.created_at,e.updated_at "
          + "FROM tenant_enterprise e JOIN tenant_member m ON m.enterprise_id=e.id AND m.deleted=0 "
          + "WHERE m.user_id=#{userId} AND m.status IN (0,1,2) AND e.deleted=0 ORDER BY m.joined_at DESC")
  List<Map<String, Object>> findEnterprises(Long userId);
}
