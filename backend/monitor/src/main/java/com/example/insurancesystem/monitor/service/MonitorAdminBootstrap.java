package com.example.insurancesystem.monitor.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 监控平台首个管理员的显式环境变量引导器，仅用于全新且没有任何监控账号的环境。
 * username 配置项承载管理员登录手机号；未配置手机号或密码时完全不写数据库，已有账号时也不会创建或覆盖用户，后续账号维护
 * 必须通过管理员功能完成，从而避免默认密码和每次启动重复初始化的安全风险。
 */
@Component
public class MonitorAdminBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String realName;

    /**
     * 注入数据库、项目 BCrypt 编码器及可选的首管环境变量。
     * 密码只保存在进程配置中用于一次编码，任何日志和数据库字段都不会记录明文。
     */
    public MonitorAdminBootstrap(JdbcTemplate jdbcTemplate,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${monitor.auth.bootstrap.username:}") String username,
                                 @Value("${monitor.auth.bootstrap.password:}") String password,
                                 @Value("${monitor.auth.bootstrap.real-name:初始管理员}") String realName) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.realName = realName == null || realName.trim().isEmpty() ? "初始管理员" : realName.trim();
    }

    /**
     * 应用启动时仅在显式配置凭据且 monitor_user 为空时创建首个 ADMIN。
     * 用户与角色关联在同一事务中提交；密码少于八位或缺少 ADMIN 基础角色时启动失败，
     * 防止系统以无法登录或弱凭据的半初始化状态继续运行。
     *
     * @param args Spring Boot 启动参数，本流程不读取其中内容
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isEmpty() || password.isEmpty()) return;
        if (!username.matches("^1\\d{10}$")) throw new IllegalStateException("MONITOR_INITIAL_ADMIN_USERNAME 必须是有效的手机号码");
        if (password.length() < 8) throw new IllegalStateException("MONITOR_INITIAL_ADMIN_PASSWORD 至少需要 8 位");
        Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM monitor_user WHERE deleted=0", Integer.class);
        if (userCount != null && userCount > 0) return;
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM monitor_role WHERE code='ADMIN' AND status=1 LIMIT 1", Long.class);
        if (roleId == null) throw new IllegalStateException("monitor_role 缺少启用的 ADMIN 角色");
        jdbcTemplate.update(
                "INSERT IGNORE INTO monitor_user(username,password_hash,real_name,status,password_changed_at,created_at,updated_at,deleted) " +
                        "VALUES(?,?,?,1,NOW(),NOW(),NOW(),0)",
                username, passwordEncoder.encode(password), realName);
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM monitor_user WHERE username=? AND deleted=0 LIMIT 1", Long.class, username);
        jdbcTemplate.update(
                "INSERT IGNORE INTO monitor_user_role(user_id,role_id,created_by,created_at) VALUES(?,?,NULL,NOW())",
                userId, roleId);
    }
}
