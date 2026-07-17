package com.example.insurancesystem.saas.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.saas.service.EnterpriseService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/portal/enterprise")
public class EnterpriseController {
  private final EnterpriseService service;

  public EnterpriseController(EnterpriseService service) {
    this.service = service;
  }

  @GetMapping("/current")
  public ResponseResult<?> current() {
    return ok(service.current());
  }

  @PostMapping
  public ResponseResult<?> create(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "企业创建成功", service.create(body));
  }

  @PutMapping("/current")
  public ResponseResult<?> update(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "企业信息已更新", service.update(body));
  }

  @PostMapping("/join-by-invite")
  public ResponseResult<?> join(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "加入企业成功", service.join(body));
  }

  @GetMapping("/invite-codes")
  public ResponseResult<?> invites(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "5") int pageSize) {
    return ok(service.invites(pageNum, pageSize));
  }

  @PostMapping("/invite-codes")
  public ResponseResult<?> createInvite(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "邀请码创建成功", service.createInvite(body));
  }

  @DeleteMapping("/invite-codes")
  public ResponseResult<?> deleteInvite(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "邀请码已删除", service.deleteInvite(body));
  }

  @GetMapping("/members")
  public ResponseResult<?> members(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "5") int pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String roleCode,
      @RequestParam(required = false) Integer status) {
    return ok(service.members(pageNum, pageSize, keyword, roleCode, status));
  }

  @PutMapping("/members/role")
  public ResponseResult<?> role(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "成员角色已更新", service.updateRole(body));
  }

  @PutMapping("/members/status")
  public ResponseResult<?> status(@RequestBody Map<String, Object> body) {
    Map<String, Object> member = service.updateStatus(body);
    return new ResponseResult<>(
        200, ((Number) member.get("status")).intValue() == 1 ? "成员已启用" : "成员已停用", member);
  }

  @PostMapping("/owner-transfer")
  public ResponseResult<?> transfer(@RequestBody Map<String, Object> body) {
    return new ResponseResult<>(200, "企业拥有者已转让", service.transfer(body));
  }

  @GetMapping("/owner-transfer-logs")
  public ResponseResult<?> transferLogs(
      @RequestParam(defaultValue = "1") int pageNum,
      @RequestParam(defaultValue = "5") int pageSize) {
    return ok(service.transferLogs(pageNum, pageSize));
  }

  @PostMapping("/members/exit")
  public ResponseResult<?> exit() {
    return new ResponseResult<>(200, "已退出企业", service.exit());
  }

  private ResponseResult<?> ok(Object data) {
    return new ResponseResult<>(200, "操作成功", data);
  }
}
