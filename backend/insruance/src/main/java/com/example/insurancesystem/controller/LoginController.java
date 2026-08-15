package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.service.LoginService;
import com.example.insurancesystem.service.UserService;
import com.example.insurancesystem.integration.SaasSsoClient;
import com.example.insurancesystem.domain.authenticate.LoginUser;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/auth")
/**
 * 车险端认证入口，协调账号密码登录、SaaS 单点登录交换、返回门户授权、注册、退出和邮箱找回密码流程。
 */
public class LoginController {

    @Autowired
    private LoginService loginService;

    @Autowired
    private UserService userService;

    @Autowired
    private SaasSsoClient saasSsoClient;

    @PostMapping("/login")
    /**
     * 接收账号密码并交由 LoginService 完成 Spring Security 认证、Redis 单会话保存及 JWT 签发。
     */
    public ResponseResult login(@RequestBody User user){
        return loginService.login(user);
    }

    @PostMapping("/sso/exchange")
    /**
     * 将 SaaS 门户发放的一次性授权码交换为车险端登录会话；空请求或空码仍交给服务层返回统一校验错误。
     */
    public ResponseResult ssoExchange(@RequestBody Map<String, Object> body) {
        Object code = body == null ? null : body.get("code");
        return loginService.ssoLogin(code == null ? "" : code.toString().trim());
    }

    @PostMapping("/sso/portal-authorize")
    /**
     * 为已登录车险用户申请返回 SaaS 门户的一次性授权码，将当前用户和企业身份提交给 SaaS 内部接口，
     * 从而避免在浏览器地址中直接传递长期 JWT 或用户资料。
     */
    public ResponseResult portalAuthorize() {
        LoginUser loginUser = (LoginUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        Map<String, Object> result = saasSsoClient.authorizePortal(
                loginUser.getUser().getId(), loginUser.getEnterpriseId());
        return new ResponseResult(200, "授权成功", result);
    }

    @PostMapping("/register")
    /**
     * 注册个人车险账号，账号审核、重复字段和密码编码规则由 UserService 统一处理。
     */
    public ResponseResult register(@RequestBody User user){
        return userService.registerPersonal(user);
    }

    @GetMapping("/logout")
    /**
     * 注销当前 Redis 单登录会话，使现有 JWT 即使尚未过期也无法再次建立认证上下文。
     */
    public ResponseResult logout(){
        return loginService.logout();
    }

    @GetMapping("/code")
    /**
     * 向已注册邮箱发送短期找回密码验证码，并由 Redis 控制有效期。
     */
    public ResponseResult getCode(String email){
        return loginService.getEmailCode(email);
    }

    @GetMapping("/forget")
    /**
     * 校验邮箱验证码后重置密码；验证码消费与密码编码由登录服务完成。
     */
    public ResponseResult forgetPassword(String email, String code, String password){
        return loginService.forgetPassword(email, code, password);
    }

}
