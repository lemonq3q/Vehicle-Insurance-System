package com.example.insurancesystem.monitor.service;

import java.util.Map;

/**
 * 定义监控后台账号密码登录、当前会话读取和主动退出能力。
 * 监控平台不开放注册或找回密码入口，账号生命周期继续由平台用户管理模块控制。
 */
public interface MonitorAuthService {
    /** 使用用户名和密码建立独立监控 JWT 会话并返回用户资料。 */
    Map<String, Object> login(Map<String, Object> body);

    /** 根据当前 JWT 认证主体返回最新的监控账号与角色资料。 */
    Map<String, Object> currentUser();

    /** 更新当前登录人的姓名、手机号和邮箱，并返回最新安全资料。 */
    Map<String, Object> updateProfile(Map<String, Object> body);

    /** 校验原密码后更新当前登录人的密码，并使当前会话立即失效。 */
    void changePassword(Map<String, Object> body);

    /** 删除当前 JWT 对应的 Redis 会话，使令牌立即失效。 */
    void logout();
}
