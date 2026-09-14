package com.example.insurancesystem.monitor.mapper;

import com.example.insurancesystem.domain.user.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 监控平台认证数据访问入口，只读取独立的 monitor_user 与 monitor_role 表。
 * 它负责提供密码认证主体、有效角色和登录后的安全资料快照，不接触租户账号，
 * 从数据边界上保证平台运营人员不会被误识别为企业成员。
 */
@Mapper
public interface MonitorAuthMapper {

    /**
     * 按用户名查询未删除的监控账号，并把监控表字段映射到公共认证主体所需字段。
     * status 会继续由 Spring Security 判断，停用账号不能建立会话。
     *
     * @param username 登录页提交并去除首尾空白的账号
     * @return 可供 BCrypt 校验的用户主体，不存在时返回 null
     */
    @Select("SELECT id,username,password_hash AS password,real_name,status,email "
            + "FROM monitor_user WHERE username=#{username} AND deleted=0 LIMIT 1")
    User findForLogin(String username);

    /**
     * 查询账号当前启用的角色代码。一个账号可配置多个角色，ADMIN 排在首位，
     * 便于前端选择权限最高的角色作为导航展示依据。
     *
     * @param userId 已通过密码认证的监控账号主键
     * @return 有效角色代码列表
     */
    @Select("SELECT r.code FROM monitor_user_role ur JOIN monitor_role r ON r.id=ur.role_id "
            + "WHERE ur.user_id=#{userId} AND r.status=1 ORDER BY CASE WHEN r.code='ADMIN' THEN 0 ELSE 1 END,r.id")
    List<String> findRoleCodes(Long userId);

    /**
     * 返回前端会话所需的最小用户资料以及首要角色，密码摘要不会出现在查询结果中。
     *
     * @param userId JWT 对应的监控账号主键
     * @return 用户安全资料；账号删除时返回 null
     */
    @Select("SELECT u.id,u.username,u.real_name AS realName,u.email,u.status,u.last_login_at AS lastLoginAt," 
            + "(SELECT r.code FROM monitor_user_role ur JOIN monitor_role r ON r.id=ur.role_id "
            + "WHERE ur.user_id=u.id AND r.status=1 ORDER BY CASE WHEN r.code='ADMIN' THEN 0 ELSE 1 END,r.id LIMIT 1) roleCode," 
            + "(SELECT r.name FROM monitor_user_role ur JOIN monitor_role r ON r.id=ur.role_id "
            + "WHERE ur.user_id=u.id AND r.status=1 ORDER BY CASE WHEN r.code='ADMIN' THEN 0 ELSE 1 END,r.id LIMIT 1) roleName "
            + "FROM monitor_user u WHERE u.id=#{userId} AND u.deleted=0 LIMIT 1")
    Map<String, Object> findProfile(Long userId);

    /**
     * 在成功签发会话后记录最近登录时间，用于后台账号审计和用户列表展示。
     *
     * @param userId 成功登录的监控账号主键
     * @param loginAt Asia/Shanghai 业务时间下的登录时间
     * @return 更新行数
     */
    @Update("UPDATE monitor_user SET last_login_at=#{loginAt},updated_at=NOW() WHERE id=#{userId} AND deleted=0")
    int updateLastLogin(@Param("userId") Long userId, @Param("loginAt") LocalDateTime loginAt);

    /** 按主键读取当前 BCrypt 密码摘要，供本人修改密码前校验原密码。 */
    @Select("SELECT password_hash FROM monitor_user WHERE id=#{userId} AND deleted=0 LIMIT 1")
    String findPasswordHash(Long userId);

    /** 检查邮箱是否已被其他未删除监控账号使用，空值不会参与唯一性判断。 */
    @Select("SELECT COUNT(1) FROM monitor_user WHERE id<>#{userId} AND deleted=0 "
            + "AND #{email}<>'' AND email=#{email}")
    int countProfileConflicts(@Param("userId") Long userId, @Param("email") String email);

    /** 更新当前账号可自助维护的姓名和邮箱，不允许借此修改登录手机号、角色或状态。 */
    @Update("UPDATE monitor_user SET real_name=#{realName},email=NULLIF(#{email},''),"
            + "updated_by=#{userId},updated_at=NOW() WHERE id=#{userId} AND deleted=0")
    int updateProfile(@Param("userId") Long userId, @Param("realName") String realName,
            @Param("email") String email);

    /** 保存新的 BCrypt 摘要并记录改密时间，密码明文不会进入数据库或操作日志。 */
    @Update("UPDATE monitor_user SET password_hash=#{passwordHash},password_changed_at=NOW(),"
            + "updated_by=#{userId},updated_at=NOW() WHERE id=#{userId} AND deleted=0")
    int updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);
}
