package com.example.insurancesystem.saas.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.saas.integration.sms.SmsVerificationService;
import com.example.insurancesystem.saas.service.PortalAuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/portal/auth")
public class PortalAuthController {
  private final PortalAuthService authService;
  private final SmsVerificationService smsService;

  public PortalAuthController(PortalAuthService authService, SmsVerificationService smsService) {
    this.authService = authService;
    this.smsService = smsService;
  }

  @PostMapping("/login")
  public ResponseResult<?> login(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "登录成功", authService.login(body));
  }

  @PostMapping("/sms-code")
  public ResponseResult<?> sms(@RequestBody Map<String, Object> body) {
    Map<String, Object> data =
        smsService.send(String.valueOf(body.get("phone")), String.valueOf(body.get("scene")));
    Object mockCode = data.remove("mockCode");
    return new ResponseResult<>(200, "验证码已发送，mock 验证码为 " + mockCode, data);
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
