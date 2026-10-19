package com.example.insurancesystem.utils.email;

import com.example.insurancesystem.domain.user.User;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/**
 * 访问两端共享的 tenant_user 邮箱身份，不涉及企业角色或业务数据。
 * 注册、绑定依靠有效邮箱唯一索引防止并发重复；绑定更新只允许尚无邮箱的当前用户。
 */
@Mapper
public interface EmailAccountMapper {
    /**
     * 查找规范邮箱的有效账号，最多两条用于发现未迁移的历史重复邮箱。
     */
    @Select("SELECT * FROM tenant_user WHERE deleted=0 AND LOWER(TRIM(email))=#{email} LIMIT 2")
    List<User> findByEmail(String email);

    /**
     * 读取当前个人账号，软删除或不存在时返回 null。
     */
    @Select("SELECT * FROM tenant_user WHERE id=#{id} AND deleted=0")
    User findById(Long id);

    /**
     * 在绑定事务中锁定当前用户，防止同一账号同时绑定不同邮箱。
     */
    @Select("SELECT * FROM tenant_user WHERE id=#{id} AND deleted=0 FOR UPDATE")
    User lockById(Long id);

    /**
     * 返回不含认证密钥的个人资料，供两端个人中心和账号上下文使用。
     */
    @Select("SELECT id,username,email,phone,real_name,id_num,avatar_file_id,status,last_login_time,created_at,updated_at,CASE WHEN wechat_open_id IS NOT NULL THEN TRUE ELSE FALSE END AS wechat_bound,wechat_nickname,wechat_bound_at FROM tenant_user WHERE id=#{id} AND deleted=0")
    Map<String, Object> findProfile(Long id);

    /**
     * 普通个人资料更新只允许修改姓名、联系电话和证件号，不写入邮箱、密码、角色或状态。
     */
    @Update("UPDATE tenant_user SET real_name=#{realName},phone=#{phone},id_num=#{idNum},updated_by=#{id},updated_at=NOW() WHERE id=#{id} AND deleted=0")
    int updateProfile(Map<String, Object> profile);

    /**
     * 联系电话为空时不参与唯一性预检，填写时排除本人已有记录。
     */
    @Select("SELECT COUNT(*) FROM tenant_user WHERE deleted=0 AND phone=#{phone} AND id<>#{id}")
    int countPhoneExcept(@Param("phone") String phone, @Param("id") Long id);

    /**
     * 创建已验证邮箱的共享个人账号，不自动创建企业或成员权限。
     */
    @Insert("INSERT INTO tenant_user(username,email,password,real_name,status,created_at,updated_at,deleted) VALUES(#{email},#{email},#{password},#{realName},1,NOW(),NOW(),0)")
    @Options(useGeneratedKeys=true, keyProperty="id")
    int insertUser(Map<String, Object> user);

    /**
     * 按已验证身份的主键更新密码，禁止批量更新同邮箱的历史重复账号。
     */
    @Update("UPDATE tenant_user SET password=#{password},updated_at=NOW() WHERE id=#{id} AND deleted=0 AND status=1")
    int updatePassword(@Param("id") Long id, @Param("password") String password);

    /**
     * 首次绑定时同时设置邮箱和密码，保留内部 username、用户 ID 及企业关系。
     */
    @Update("UPDATE tenant_user SET email=#{email},password=#{password},updated_by=#{id},updated_at=NOW() WHERE id=#{id} AND deleted=0 AND status=1 AND (email IS NULL OR TRIM(email)='')")
    int bindEmail(@Param("id") Long id, @Param("email") String email, @Param("password") String password);
}
