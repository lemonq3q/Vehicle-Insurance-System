package com.example.insurancesystem.monitor.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.monitor.service.MonitorAuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控后台认证 HTTP 入口，向前端提供登录、会话恢复和主动退出接口。
 * 仅登录接口允许匿名访问；用户注册、密码找回和角色变更不属于此入口，
 * 避免运营后台形成未经管理员审批的自助账号通道。
 */
@RestController
@RequestMapping("/monitor/auth")
public class MonitorAuthController {
    private final MonitorAuthService authService;

    /** 注入监控认证业务服务。 */
    public MonitorAuthController(MonitorAuthService authService) {
        this.authService = authService;
    }

    /** 使用用户名和密码创建监控平台会话。 */
    @PostMapping("/login")
    public ResponseResult<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        return new ResponseResult<>(200, "登录成功", authService.login(body));
    }

    /** 返回当前令牌对应的最新账号和角色资料，供浏览器刷新后恢复上下文。 */
    @GetMapping("/me")
    public ResponseResult<Map<String, Object>> me() {
        return new ResponseResult<>(200, authService.currentUser());
    }

    /** 更新当前监控账号允许自助维护的个人资料。 */
    @PutMapping("/profile")
    public ResponseResult<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> body) {
        return new ResponseResult<>(200, "个人资料已更新", authService.updateProfile(body));
    }

    /** 校验当前密码后设置新密码，成功后当前会话立即失效。 */
    @PutMapping("/password")
    public ResponseResult<Void> changePassword(@RequestBody Map<String, Object> body) {
        authService.changePassword(body);
        return new ResponseResult<>(200, "密码已修改，请重新登录");
    }

    /** 主动注销当前 Redis 会话，使现有令牌不可再次访问受保护接口。 */
    @PostMapping("/logout")
    public ResponseResult<Void> logout() {
        authService.logout();
        return new ResponseResult<>(200, "退出成功");
    }
}
