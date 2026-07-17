package com.example.insurancesystem.saas.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.saas.service.ProfileService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/portal/user/profile")
public class ProfileController {
  private final ProfileService service;

  public ProfileController(ProfileService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseResult<?> get() {
    return new ResponseResult<>(200, "操作成功", service.get());
  }

  @PutMapping
  public ResponseResult<?> update(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "个人资料已更新", service.update(body));
  }
}
