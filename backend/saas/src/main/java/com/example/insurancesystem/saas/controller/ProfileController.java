package com.example.insurancesystem.saas.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.saas.service.ProfileService;
import com.example.insurancesystem.utils.email.EmailAccountService;
import com.example.insurancesystem.utils.SystemCommonUtil;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * 门户个人中心入口，普通资料编辑与首次邮箱验证绑定采用独立接口。
 * 所有操作要求登录，绑定只使用服务端用户身份，不接受请求中的目标用户 ID。
 */
@RestController
@RequestMapping("/portal/user/profile")
public class ProfileController {
  private final ProfileService service;

  private final EmailAccountService emailAccounts;

  /**
   * 注入个人资料编辑与共享邮件身份服务，绑定用户由会话确定。
   */
  public ProfileController(ProfileService service, EmailAccountService emailAccounts) {
    this.service = service;
    this.emailAccounts = emailAccounts;
  }

  @GetMapping
  /**
   * 查询登录用户可展示的个人资料，包含邮箱绑定状态，不包含密码。
   */
  public ResponseResult<?> get() {
    return new ResponseResult<>(200, "操作成功", service.get());
  }

  @PutMapping
  /**
   * 更新本人普通联系资料；邮箱、角色和密码不会通过此接口写入。
   */
  public ResponseResult<?> update(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "个人资料已更新", service.update(body));
  }
  /**
   * 为当前尚未绑定邮箱的账号发送身份专属验证码。
   */
  @PostMapping("/email-code")
  public ResponseResult<?> emailCode(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "验证码已发送", emailAccounts.sendBindingCode(SystemCommonUtil.getNowUserId(), body));
  }

  /**
   * 完成邮箱验证并首次设置登录密码，不接受客户端提供用户 ID。
   */
  @PostMapping("/bind-email")
  public ResponseResult<?> bindEmail(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "邮箱绑定成功", emailAccounts.bindEmail(SystemCommonUtil.getNowUserId(), body));
  }
}
