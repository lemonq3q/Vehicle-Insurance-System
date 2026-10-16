package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.utils.email.EmailAccountService;
import com.example.insurancesystem.utils.SystemCommonUtil;
import com.example.insurancesystem.service.LoginService;
import com.example.insurancesystem.service.UserService;
import com.example.insurancesystem.integration.client.SaasSsoClient;
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

    @Autowired
    private EmailAccountService emailAccounts;

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
     * 注册经过邮箱验证的共享个人账号；企业创建和成员申请在 SaaS 门户完成。
     */
    public ResponseResult register(@RequestBody Map<String, Object> body){
        return new ResponseResult(200, "注册成功，请前往 SaaS 门户创建或加入企业", emailAccounts.register(body));
    }

    @GetMapping("/logout")
    /**
     * 注销当前 Redis 单登录会话，使现有 JWT 即使尚未过期也无法再次建立认证上下文。
     */
    public ResponseResult logout(){
        return loginService.logout();
    }

    /**
     * 申请注册或重置场景的真实邮件验证码，不允许匿名申请绑定码。
     */
    @PostMapping("/email-code")
    public ResponseResult<?> emailCode(@RequestBody Map<String, Object> body) {
        return new ResponseResult<>(200, "验证码已发送", emailAccounts.sendCode(body));
    }

    /**
     * 使用 JSON 提交密码重置，避免凭据出现在浏览器 URL 和代理访问日志。
     */
    @PostMapping("/forget-password")
    public ResponseResult<?> forgetPassword(@RequestBody Map<String, Object> body) {
        return new ResponseResult<>(200, "密码已重置", emailAccounts.resetPassword(body));
    }

    /**
     * 提供当前账号安全资料，用于无需企业管理权限的邮箱身份展示。
     */
    @GetMapping("/profile")
    public ResponseResult<?> profile() {
        return new ResponseResult<>(200, "操作成功", emailAccounts.profile(SystemCommonUtil.getNowUserId()));
    }

    /**
     * 更新本人普通资料，邮箱与密码均不从此接口写入，避免绕过验证码绑定。
     */
    @PutMapping("/profile")
    public ResponseResult<?> updateProfile(@RequestBody Map<String, Object> body) {
        return new ResponseResult<>(200, "个人资料已更新", emailAccounts.updateProfile(SystemCommonUtil.getNowUserId(), body));
    }

    /**
     * 为当前已登录且尚未绑定邮箱的用户发送专属绑定码。
     */
    @PostMapping("/profile/email-code")
    public ResponseResult<?> bindingCode(@RequestBody Map<String, Object> body) {
        return new ResponseResult<>(200, "验证码已发送", emailAccounts.sendBindingCode(SystemCommonUtil.getNowUserId(), body));
    }

    /**
     * 邮箱验证成功后首次绑定邮箱并设置登录密码，保留原用户及企业关系。
     */
    @PostMapping("/profile/bind-email")
    public ResponseResult<?> bindEmail(@RequestBody Map<String, Object> body) {
        return new ResponseResult<>(200, "邮箱绑定成功", emailAccounts.bindEmail(SystemCommonUtil.getNowUserId(), body));
    }
}
