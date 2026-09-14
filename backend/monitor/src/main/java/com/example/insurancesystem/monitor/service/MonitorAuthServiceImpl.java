package com.example.insurancesystem.monitor.service;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.monitor.mapper.MonitorAuthMapper;
import com.example.insurancesystem.security.SingleLoginSessionManager;
import com.example.insurancesystem.utils.JwtUtil;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 监控后台认证编排服务，复用项目 BCrypt、JWT 与 Redis 单登录会话基础设施。
 * 登录成功后新会话会覆盖该监控账号旧会话；monitor:login: 前缀确保其不会覆盖
 * SaaS 门户和车险系统中主键相同的用户会话。
 */
@Service
public class MonitorAuthServiceImpl implements MonitorAuthService {
    private final AuthenticationManager authenticationManager;
    private final SingleLoginSessionManager sessionManager;
    private final MonitorAuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final MonitorSystemLogService systemLog;

    /**
     * 注入认证管理器、独立会话管理器和用户资料查询入口。
     *
     * @param authenticationManager 执行用户名与 BCrypt 密码校验
     * @param sessionManager 保存和删除 Redis 单登录会话
     * @param authMapper 读取监控用户及角色资料
     */
    public MonitorAuthServiceImpl(AuthenticationManager authenticationManager,
                                  SingleLoginSessionManager sessionManager,
                                  MonitorAuthMapper authMapper, PasswordEncoder passwordEncoder,
                                  MonitorSystemLogService systemLog) {
        this.authenticationManager = authenticationManager;
        this.sessionManager = sessionManager;
        this.authMapper = authMapper;
        this.passwordEncoder = passwordEncoder;
        this.systemLog = systemLog;
    }

    /**
     * 校验必填凭据后执行 Spring Security 认证，成功时签发带 jti 的 24 小时 JWT，
     * 保存 Redis 单会话并更新最近登录时间。账号停用返回 403，错误凭据统一返回 400，
     * 避免向匿名请求泄露账号是否真实存在。
     *
     * @param body 前端提交的 username、password
     * @return token 与不含密码的用户资料
     */
    @Override
    public Map<String, Object> login(Map<String, Object> body) {
        String username = required(body, "username");
        String password = required(body, "password");
        if (!username.matches("^1\\d{10}$")) throw new BusinessException(400, "请输入有效的手机号码");
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (DisabledException exception) {
            throw new BusinessException(403, "账号已停用，请联系管理员");
        } catch (BadCredentialsException exception) {
            throw new BusinessException(400, "手机号或密码错误");
        }
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        Long userId = loginUser.getUser().getId();
        String sessionId = JwtUtil.getUUID();
        String token = JwtUtil.createJWT(userId.toString(), JwtUtil.LOGIN_JWT_TTL, sessionId);
        sessionManager.save(userId, sessionId, loginUser);
        authMapper.updateLastLogin(userId, LocalDateTime.now());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("user", authMapper.findProfile(userId));
        return result;
    }

    /**
     * 从安全上下文获取已由 JWT 和 Redis 双重校验的用户 ID，再查询最新资料。
     * 若账号在会话期间被删除则返回 401，促使前端清除本地残留会话。
     *
     * @return 当前监控账号安全资料
     */
    @Override
    public Map<String, Object> currentUser() {
        LoginUser loginUser = principal();
        Map<String, Object> profile = authMapper.findProfile(loginUser.getUser().getId());
        if (profile == null) throw new BusinessException(401, "登录状态已失效");
        return profile;
    }

    /**
     * 校验本人可编辑姓名、邮箱的长度、格式和唯一性，在同一事务中更新账号并写入安全审计日志。
     * 登录手机号、角色和启停状态不接受个人中心修改，避免自助入口扩大权限边界。
     */
    @Override
    @Transactional
    public Map<String, Object> updateProfile(Map<String, Object> body) {
        LoginUser loginUser = principal();
        Long userId = loginUser.getUser().getId();
        String realName = text(body, "realName", true, 64, "姓名");
        String email = text(body, "email", false, 100, "邮箱");
        if (!email.isEmpty() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new BusinessException(400, "请输入有效的电子邮箱");
        if (authMapper.countProfileConflicts(userId, email) > 0)
            throw new BusinessException(409, "邮箱已被其他账号使用");
        Map<String, Object> before = authMapper.findProfile(userId);
        if (before == null || authMapper.updateProfile(userId, realName, email) != 1)
            throw new BusinessException(404, "账号不存在或已删除");
        Map<String, Object> after = authMapper.findProfile(userId);
        systemLog.recordOperation("PROFILE_UPDATE", "修改个人资料", "profile", userId,
                String.valueOf(before.get("realName")), null, null, "MONITOR_USER", userId,
                String.valueOf(before.get("username")), "本人修改", "更新个人资料", before, after);
        return after;
    }

    /**
     * 使用 BCrypt 校验当前密码，要求新密码至少八位且两次输入一致；更新成功后只记录非敏感审计事件，
     * 随即删除当前 Redis 会话，确保旧令牌和旧密码环境不能继续使用后台。
     */
    @Override
    @Transactional
    public void changePassword(Map<String, Object> body) {
        LoginUser loginUser = principal();
        Long userId = loginUser.getUser().getId();
        String currentPassword = text(body, "currentPassword", true, 200, "当前密码");
        String newPassword = text(body, "newPassword", true, 200, "新密码");
        String confirmPassword = text(body, "confirmPassword", true, 200, "确认密码");
        if (newPassword.length() < 8) throw new BusinessException(400, "新密码至少需要 8 位");
        if (!newPassword.equals(confirmPassword)) throw new BusinessException(400, "两次输入的新密码不一致");
        String hash = authMapper.findPasswordHash(userId);
        if (hash == null) throw new BusinessException(404, "账号不存在或已删除");
        if (!passwordEncoder.matches(currentPassword, hash)) throw new BusinessException(400, "当前密码不正确");
        if (passwordEncoder.matches(newPassword, hash)) throw new BusinessException(400, "新密码不能与当前密码相同");
        if (authMapper.updatePassword(userId, passwordEncoder.encode(newPassword)) != 1)
            throw new BusinessException(404, "账号不存在或已删除");
        Map<String, Object> profile = authMapper.findProfile(userId);
        systemLog.recordOperation("PASSWORD_CHANGE", "修改登录密码", "profile", userId,
                String.valueOf(profile.get("realName")), null, null, "MONITOR_USER", userId,
                String.valueOf(profile.get("username")), "本人修改", "当前账号已修改登录密码", null, null);
        sessionManager.remove(userId, loginUser.getSessionId());
    }

    /**
     * 仅删除当前 jti 对应的 Redis 会话。若账号已在其他设备重新登录，旧令牌执行退出
     * 不会误删新设备会话；接口保持幂等，不修改账号状态或角色关系。
     */
    @Override
    public void logout() {
        LoginUser loginUser = principal();
        sessionManager.remove(loginUser.getUser().getId(), loginUser.getSessionId());
    }

    /** 获取已经通过过滤器校验的监控登录主体，缺失时按未认证处理。 */
    private LoginUser principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser)) {
            throw new BusinessException(401, "请先登录");
        }
        return (LoginUser) authentication.getPrincipal();
    }

    /** 读取并清理字符串参数，空值直接返回明确的业务校验错误。 */
    private String required(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value == null || value.toString().trim().isEmpty()) {
            throw new BusinessException(400, "username".equals(key) ? "请输入手机号码" : "请输入密码");
        }
        return value.toString().trim();
    }

    /** 读取个人中心文本字段，统一执行必填、去空白和数据库列宽限制。 */
    private String text(Map<String, Object> body, String key, boolean required, int maxLength, String label) {
        Object raw = body == null ? null : body.get(key);
        String value = raw == null ? "" : raw.toString().trim();
        if (required && value.isEmpty()) throw new BusinessException(400, "请填写" + label);
        if (value.length() > maxLength) throw new BusinessException(400, label + "不能超过 " + maxLength + " 个字符");
        return value;
    }
}
