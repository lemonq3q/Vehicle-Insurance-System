package com.example.insurancesystem.saas.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.utils.email.EmailAccountService;
import com.example.insurancesystem.saas.service.PortalAuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * 门户匿名邮箱认证入口，验证码和身份写入沿用共享服务及统一响应。
 */
@RestController
@RequestMapping("/portal/auth")
public class PortalAuthController {
  private final PortalAuthService authService;
  private final EmailAccountService emailAccounts;

  public PortalAuthController(PortalAuthService authService, EmailAccountService emailAccounts) {
    this.authService = authService;
    this.emailAccounts = emailAccounts;
  }

  @PostMapping("/login")
  public ResponseResult<?> login(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "登录成功", authService.login(body));
  }

  /**
   * 按注册或重置场景申请真实邮箱验证码，响应仅返回有效期和重发等待时间。
   */
  @PostMapping("/email-code")
  public ResponseResult<?> emailCode(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "验证码已发送", emailAccounts.sendCode(body));
  }

  @PostMapping("/register")
  public ResponseResult<?> register(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "注册成功", authService.register(body));
  }

  @PostMapping("/forget-password")
  public ResponseResult<?> forget(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "密码已重置", authService.forgetPassword(body));
  }
}
