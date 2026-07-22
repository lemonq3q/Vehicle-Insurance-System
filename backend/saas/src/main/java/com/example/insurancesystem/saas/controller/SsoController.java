package com.example.insurancesystem.saas.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.saas.service.SsoService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class SsoController {
  private final SsoService ssoService;

  public SsoController(SsoService ssoService) {
    this.ssoService = ssoService;
  }

  @PostMapping("/portal/sso/authorize")
  public ResponseResult<?> authorize() {
    return new ResponseResult<>(200, "授权成功", ssoService.authorize());
  }

  @PostMapping("/internal/sso/exchange")
  public ResponseResult<?> exchange(
      @RequestHeader(value = "X-Insurance-Client-Secret", required = false) String clientSecret,
      @RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "授权码兑换成功", ssoService.exchange(clientSecret, body));
  }
}
