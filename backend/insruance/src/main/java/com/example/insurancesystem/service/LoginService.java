package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.user.User;

/**
 * 定义车险端账号认证、SaaS 单点登录、单会话注销和邮箱找回密码流程。
 */
public interface LoginService {
    /**
     * 校验账号密码并创建 Redis 单登录会话与 JWT。
     */
    ResponseResult login(User user);

    /**
     * 消费 SaaS 一次性授权码并建立等价车险登录会话。
     */
    ResponseResult ssoLogin(String code);

    /**
     * 删除当前用户的匹配 Redis 会话。
     */
    ResponseResult logout();

    /**
     * 生成并发送找回密码邮箱验证码。
     */
    ResponseResult getEmailCode(String email);

    /**
     * 验证邮箱验证码并更新密码。
     */
    ResponseResult forgetPassword(String email, String code, String password);
}
