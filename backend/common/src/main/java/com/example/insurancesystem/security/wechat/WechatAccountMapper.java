package com.example.insurancesystem.security.wechat;

import com.example.insurancesystem.domain.user.User;
import java.util.Map;
import org.apache.ibatis.annotations.*;

/**
 * 在共享 tenant_user 上维护一对一微信身份；唯一索引防止两端并发占用同一微信。
 * 不提供账号删除、清理资料、替换已有绑定或合并账号操作。
 */
@Mapper
public interface WechatAccountMapper {
    /** 包含软删除记录的身份查询，避免将历史身份静默分配给新账号。 */
    @Select("SELECT * FROM tenant_user WHERE wechat_app_id=#{appId} AND wechat_open_id=#{openId} LIMIT 1")
    User findIdentity(@Param("appId") String appId, @Param("openId") String openId);

    /** 按用户读取未删除账号，供后端校验状态；不返回任意其他账号资料给浏览器。 */
    @Select("SELECT * FROM tenant_user WHERE id=#{id} AND deleted=0")
    User findUser(Long id);

    /** 创建微信个人账号，不创建企业、成员或任何业务权限，邮箱及密码保持 NULL。 */
    @Insert("INSERT INTO tenant_user(username,real_name,status,deleted,wechat_app_id,wechat_open_id,wechat_union_id,wechat_nickname,wechat_bound_at,created_at,updated_at) VALUES(#{username},#{name},1,0,#{appId},#{openId},NULLIF(#{unionId},''),NULLIF(#{nickname},''),NOW(),NOW(),NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWechatUser(Map<String, Object> values);

    /** 条件更新只允许尚未绑定微信的启用账号；并发冲突由行条件与唯一索引共同拒绝。 */
    @Update("UPDATE tenant_user SET wechat_app_id=#{appId},wechat_open_id=#{openId},wechat_union_id=NULLIF(#{unionId},''),wechat_nickname=NULLIF(#{nickname},''),wechat_bound_at=NOW(),updated_at=NOW() WHERE id=#{id} AND deleted=0 AND status=1 AND wechat_open_id IS NULL AND wechat_app_id IS NULL")
    int bind(Map<String, Object> values);

    /** 查询当前绑定归属，仅返回服务端用于幂等比较的标识，不对前端暴露 openid。 */
    @Select("SELECT wechat_app_id AS appId,wechat_open_id AS openId FROM tenant_user WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Map<String, Object> lockBinding(Long id);
}
