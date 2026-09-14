package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.monitor.service.MonitorUserService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控平台用户管理入口，仅允许 ADMIN 管理独立的 monitor_user 账号及角色。
 * 账号的 username 字段固定承载 11 位登录手机号，不再接收或返回重复 phone 字段；
 * 所有写操作由服务层在同一事务内完成账号、角色和系统日志更新。
 */
@RestController
@RequestMapping("/monitor/users")
@PreAuthorize("hasRole('ADMIN')")
public class MonitorUserController {
    private final MonitorUserService service;

    public MonitorUserController(MonitorUserService service) { this.service = service; }

    /** 按关键词、角色、状态和分页条件查询未删除平台账号。 */
    @GetMapping
    public ResponseResult<Map<String, Object>> list(@RequestParam Map<String, String> query,
            Authentication authentication) {
        return new ResponseResult<>(200, service.list(query, operator(authentication).getUser().getId()));
    }

    /** 返回账号资料和当前账号标记，供编辑页展示。 */
    @GetMapping("/{id}")
    public ResponseResult<Map<String, Object>> detail(@PathVariable Long id, Authentication authentication) {
        return new ResponseResult<>(200, service.detail(id, operator(authentication).getUser().getId()));
    }

    /** 创建以手机号为登录账号的平台用户，并仅在本次响应返回随机初始密码。 */
    @PostMapping
    public ResponseResult<Map<String, Object>> create(@RequestBody Map<String, Object> body,
            Authentication authentication) {
        LoginUser operator = operator(authentication);
        return new ResponseResult<>(200, service.create(body, operator.getUser().getId(),
                operator.getUser().getRealName()));
    }

    /** 修改登录手机号、姓名、邮箱、角色和状态，并执行当前账号及最后管理员保护。 */
    @PutMapping("/{id}")
    public ResponseResult<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body,
            Authentication authentication) {
        LoginUser operator = operator(authentication);
        return new ResponseResult<>(200, service.update(id, body, operator.getUser().getId(),
                operator.getUser().getRealName()));
    }

    /** 独立启停账号，使列表页操作不会覆盖其他资料字段。 */
    @PatchMapping("/{id}/status")
    public ResponseResult<Map<String, Object>> status(@PathVariable Long id, @RequestBody Map<String, Object> body,
            Authentication authentication) {
        LoginUser operator = operator(authentication);
        return new ResponseResult<>(200, service.updateStatus(id, body, operator.getUser().getId(),
                operator.getUser().getRealName()));
    }

    /** 生成新初始密码并撤销目标账号现有会话，密码明文不进入日志。 */
    @PostMapping("/{id}/reset-password")
    public ResponseResult<Map<String, Object>> resetPassword(@PathVariable Long id,
            @RequestBody Map<String, Object> body, Authentication authentication) {
        LoginUser operator = operator(authentication);
        return new ResponseResult<>(200, service.resetPassword(id, body, operator.getUser().getId(),
                operator.getUser().getRealName()));
    }

    /** 软删除非当前账号，并保留历史角色关系和审计记录。 */
    @DeleteMapping("/{id}")
    public ResponseResult<Void> delete(@PathVariable Long id, @RequestBody Map<String, Object> body,
            Authentication authentication) {
        LoginUser operator = operator(authentication);
        service.delete(id, body, operator.getUser().getId(), operator.getUser().getRealName());
        return new ResponseResult<>(200, "删除成功");
    }

    /** 从安全上下文取得经过 JWT 与 Redis 校验的真实操作人。 */
    private LoginUser operator(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser))
            throw new BusinessException(401, "请先登录");
        return (LoginUser) authentication.getPrincipal();
    }
}
