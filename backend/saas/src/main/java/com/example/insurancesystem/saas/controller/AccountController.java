package com.example.insurancesystem.saas.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.saas.service.AccountService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/portal/account")
public class AccountController {
  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @GetMapping("/context")
  public ResponseResult<?> current() {
    return new ResponseResult<>(200, "操作成功", accountService.currentContext());
  }
}
