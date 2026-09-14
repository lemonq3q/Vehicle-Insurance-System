package com.example.insurancesystem.monitor.service;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.security.SingleLoginSessionManager;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 监控平台账号管理服务，维护 monitor_user 与 monitor_user_role，并与监控认证会话协作。
 * username 是唯一登录手机号；服务负责手机号/邮箱唯一性、角色合法性、当前账号保护、
 * 最后一个启用管理员保护、密码哈希、会话撤销和敏感操作审计。
 */
@Service
public class MonitorUserService {
    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789@#$%";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SELECT_FIELDS = "SELECT u.id,u.username,u.real_name realName,u.email,u.status," +
            "u.last_login_at lastLoginAt,u.password_changed_at passwordChangedAt,u.created_at createdAt," +
            "creator.real_name createdByName,r.code roleCode,r.name roleName " +
            "FROM monitor_user u LEFT JOIN monitor_user creator ON creator.id=u.created_by " +
            "LEFT JOIN monitor_user_role ur ON ur.user_id=u.id " +
            "LEFT JOIN monitor_role r ON r.id=ur.role_id AND r.status=1 ";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final SingleLoginSessionManager sessions;
    private final MonitorSystemLogService systemLog;

    public MonitorUserService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
            SingleLoginSessionManager sessions, MonitorSystemLogService systemLog) {
        this.jdbc = jdbc; this.passwordEncoder = passwordEncoder; this.sessions = sessions; this.systemLog = systemLog;
    }

    /**
     * 构造参数化分页查询。关键词覆盖姓名、登录手机号和邮箱；页码最小为 1，每页最多 100，
     * 返回字段不包含密码摘要，current 标记由当前 JWT 用户 ID 计算。
     */
    public Map<String, Object> list(Map<String, String> query, Long currentUserId) {
        int pageNo = integer(query.get("pageNo"), 1, 1, Integer.MAX_VALUE, "pageNo");
        int pageSize = integer(query.get("pageSize"), 10, 1, 100, "pageSize");
        StringBuilder where = new StringBuilder(" WHERE u.deleted=0 ");
        List<Object> args = new ArrayList<>();
        String keyword = clean(query.get("keyword"));
        if (!keyword.isEmpty()) { where.append("AND (u.real_name LIKE ? OR u.username LIKE ? OR u.email LIKE ?) "); String like = "%" + keyword + "%"; args.add(like); args.add(like); args.add(like); }
        String roleCode = clean(query.get("roleCode"));
        if (!roleCode.isEmpty()) { validateRole(roleCode); where.append("AND r.code=? "); args.add(roleCode); }
        String statusText = clean(query.get("status"));
        if (!statusText.isEmpty()) { where.append("AND u.status=? "); args.add(integer(statusText, 1, 0, 1, "status")); }
        Long total = jdbc.queryForObject("SELECT COUNT(DISTINCT u.id) FROM monitor_user u LEFT JOIN monitor_user_role ur ON ur.user_id=u.id LEFT JOIN monitor_role r ON r.id=ur.role_id AND r.status=1" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(pageSize); pageArgs.add((pageNo - 1) * pageSize);
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT_FIELDS + where + "ORDER BY u.created_at DESC,u.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        rows.forEach(row -> row.put("current", currentUserId.equals(number(row.get("id")).longValue())));
        Map<String, Object> result = new LinkedHashMap<>(); result.put("list", rows); result.put("pageNo", pageNo);
        result.put("pageSize", pageSize); result.put("total", total == null ? 0 : total); return result;
    }

    /** 返回未删除账号详情，并附加当前登录账号标记。 */
    public Map<String, Object> detail(Long id, Long currentUserId) {
        Map<String, Object> row = detailRow(id, false); row.put("current", id.equals(currentUserId)); return row;
    }

    /**
     * 校验手机号、资料、角色和审计原因后生成一次性初始密码。账号与角色关联共享事务，
     * 数据库唯一索引作为并发创建的最终防线；日志快照不含密码明文或摘要。
     */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body, Long operatorId, String operatorName) {
        UserInput input = validate(body, true); String initialPassword = initialPassword();
        Long roleId = roleId(input.roleCode); KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> { PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO monitor_user(username,password_hash,real_name,email,status,password_changed_at,created_by,updated_by,created_at,updated_at,deleted) VALUES(?,?,?,?,1,NOW(),?,?,NOW(),NOW(),0)", Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, input.username); statement.setString(2, passwordEncoder.encode(initialPassword));
                statement.setString(3, input.realName); statement.setString(4, emptyToNull(input.email));
                statement.setObject(5, operatorId); statement.setObject(6, operatorId); return statement; }, keys);
        } catch (DuplicateKeyException exception) { throw new BusinessException(409, "登录手机号或邮箱已存在"); }
        Number key = keys.getKey(); if (key == null) throw new BusinessException(500, "账号创建失败");
        Long userId = key.longValue(); jdbc.update("INSERT INTO monitor_user_role(user_id,role_id,created_by,created_at) VALUES(?,?,?,NOW())", userId, roleId, operatorId);
        Map<String, Object> user = detailRow(userId, false);
        systemLog.recordOperation("USER_CREATE", "创建平台用户", "user", operatorId, operatorName, null, null,
                "MONITOR_USER", userId, input.realName, input.reason, "创建平台用户“" + input.realName + "”", null, user);
        Map<String, Object> result = new LinkedHashMap<>(); result.put("user", user); result.put("initialPassword", initialPassword); return result;
    }

    /**
     * 加锁账号后更新手机号、资料、角色与状态。当前账号不能自助停用；若目标是最后一个启用
     * ADMIN，也不能停用或移除 ADMIN 角色。登录手机号、角色或状态变化后撤销目标会话。
     */
    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body, Long operatorId, String operatorName) {
        Map<String, Object> before = detailRow(id, true); UserInput input = validate(body, false);
        int status = integer(body.get("status"), 1, 0, 1, "status");
        protect(id, operatorId, number(before.get("status")).intValue(), String.valueOf(before.get("roleCode")), status, input.roleCode);
        try { jdbc.update("UPDATE monitor_user SET username=?,real_name=?,email=?,status=?,updated_by=?,updated_at=NOW() WHERE id=? AND deleted=0",
                input.username, input.realName, emptyToNull(input.email), status, operatorId, id); }
        catch (DuplicateKeyException exception) { throw new BusinessException(409, "登录手机号或邮箱已存在"); }
        Long roleId = roleId(input.roleCode); jdbc.update("DELETE FROM monitor_user_role WHERE user_id=?", id);
        jdbc.update("INSERT INTO monitor_user_role(user_id,role_id,created_by,created_at) VALUES(?,?,?,NOW())", id, roleId, operatorId);
        Map<String, Object> after = detailRow(id, false);
        systemLog.recordOperation("USER_UPDATE", "修改平台用户", "user", operatorId, operatorName, null, null,
                "MONITOR_USER", id, input.realName, input.reason, "修改平台用户“" + input.realName + "”", before, after);
        if (!String.valueOf(before.get("username")).equals(input.username) || number(before.get("status")).intValue() != status || !String.valueOf(before.get("roleCode")).equals(input.roleCode)) sessions.removeAll(id);
        after.put("current", id.equals(operatorId)); return after;
    }

    /** 仅变更启停状态；重复状态幂等返回，停用后立即撤销目标账号会话。 */
    @Transactional
    public Map<String, Object> updateStatus(Long id, Map<String, Object> body, Long operatorId, String operatorName) {
        Map<String, Object> before = detailRow(id, true); int oldStatus = number(before.get("status")).intValue();
        int status = integer(body.get("status"), 1, 0, 1, "status"); String reason = required(body, "reason", "操作原因", 500);
        protect(id, operatorId, oldStatus, String.valueOf(before.get("roleCode")), status, String.valueOf(before.get("roleCode")));
        if (oldStatus == status) { before.put("current", id.equals(operatorId)); return before; }
        jdbc.update("UPDATE monitor_user SET status=?,updated_by=?,updated_at=NOW() WHERE id=? AND deleted=0", status, operatorId, id);
        Map<String, Object> after = detailRow(id, false); String action = status == 1 ? "启用" : "停用";
        systemLog.recordOperation("USER_STATUS", action + "平台用户", "user", operatorId, operatorName, null, null,
                "MONITOR_USER", id, String.valueOf(before.get("realName")), reason, action + "平台用户“" + before.get("realName") + "”", before, after);
        if (status == 0) sessions.removeAll(id); after.put("current", id.equals(operatorId)); return after;
    }

    /** 重置密码摘要、记录改密时间并撤销目标会话；响应只返回一次性明文密码。 */
    @Transactional
    public Map<String, Object> resetPassword(Long id, Map<String, Object> body, Long operatorId, String operatorName) {
        Map<String, Object> user = detailRow(id, true); String reason = required(body, "reason", "操作原因", 500);
        String password = initialPassword(); jdbc.update("UPDATE monitor_user SET password_hash=?,password_changed_at=NOW(),updated_by=?,updated_at=NOW() WHERE id=? AND deleted=0", passwordEncoder.encode(password), operatorId, id);
        systemLog.recordOperation("USER_PASSWORD_RESET", "重置平台用户密码", "user", operatorId, operatorName, null, null,
                "MONITOR_USER", id, String.valueOf(user.get("realName")), reason, "重置平台用户“" + user.get("realName") + "”的登录密码", null, null);
        sessions.removeAll(id); return Map.of("initialPassword", password);
    }

    /** 软删除非当前账号及非最后启用管理员，并立即撤销其登录会话。 */
    @Transactional
    public void delete(Long id, Map<String, Object> body, Long operatorId, String operatorName) {
        Map<String, Object> before = detailRow(id, true); String reason = required(body, "reason", "删除原因", 500);
        if (id.equals(operatorId)) throw new BusinessException(409, "不能删除当前登录账号");
        protectLastAdmin(number(before.get("status")).intValue(), String.valueOf(before.get("roleCode")), 0, "CUSTOMER_SERVICE");
        jdbc.update("UPDATE monitor_user SET deleted=1,status=0,updated_by=?,updated_at=NOW() WHERE id=? AND deleted=0", operatorId, id);
        systemLog.recordOperation("USER_DELETE", "删除平台用户", "user", operatorId, operatorName, null, null,
                "MONITOR_USER", id, String.valueOf(before.get("realName")), reason, "删除平台用户“" + before.get("realName") + "”", before, null);
        sessions.removeAll(id);
    }

    /** 查询账号单行；写操作可追加 FOR UPDATE 保证保护判断与后续更新使用同一快照。 */
    private Map<String, Object> detailRow(Long id, boolean lock) {
        if (id == null) throw new BusinessException(400, "用户标识不能为空");
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT_FIELDS + "WHERE u.id=? AND u.deleted=0" + (lock ? " FOR UPDATE" : ""), id);
        if (rows.isEmpty()) throw new BusinessException(404, "平台用户不存在"); return rows.get(0);
    }

    /** 校验手机号、姓名、邮箱、角色和原因，并生成写入阶段使用的强类型输入。 */
    private UserInput validate(Map<String, Object> body, boolean creating) {
        String username = required(body, "username", "登录手机号", 11);
        if (!PHONE.matcher(username).matches()) throw new BusinessException(400, "请输入有效的登录手机号");
        String email = optional(body, "email", 100, "电子邮箱");
        if (!email.isEmpty() && !EMAIL.matcher(email).matches()) throw new BusinessException(400, "请输入有效的电子邮箱");
        String roleCode = required(body, "roleCode", "平台角色", 32); validateRole(roleCode);
        return new UserInput(username, required(body, "realName", "真实姓名", 64), email, roleCode,
                required(body, "reason", creating ? "创建原因" : "修改原因", 500));
    }

    /** 当前账号禁止停用，最后启用管理员禁止被停用或移除管理员角色。 */
    private void protect(Long id, Long operatorId, int oldStatus, String oldRole, int newStatus, String newRole) {
        if (id.equals(operatorId) && newStatus == 0) throw new BusinessException(409, "不能停用当前登录账号");
        protectLastAdmin(oldStatus, oldRole, newStatus, newRole);
    }

    /** 当操作会减少启用管理员数量时加锁计数，保证并发请求不能同时移除最后管理员。 */
    private void protectLastAdmin(int oldStatus, String oldRole, int newStatus, String newRole) {
        if (oldStatus != 1 || !"ADMIN".equals(oldRole) || (newStatus == 1 && "ADMIN".equals(newRole))) return;
        List<Long> adminIds = jdbc.queryForList("SELECT u.id FROM monitor_user u JOIN monitor_user_role ur ON ur.user_id=u.id JOIN monitor_role r ON r.id=ur.role_id WHERE u.deleted=0 AND u.status=1 AND r.status=1 AND r.code='ADMIN' FOR UPDATE", Long.class);
        if (adminIds.size() <= 1) throw new BusinessException(409, "不能停用或删除最后一个启用管理员");
    }

    /** 查找启用角色主键，不允许写入未知或已停用角色。 */
    private Long roleId(String code) { List<Long> ids = jdbc.queryForList("SELECT id FROM monitor_role WHERE code=? AND status=1", Long.class, code); if (ids.isEmpty()) throw new BusinessException(400, "平台角色无效"); return ids.get(0); }
    private void validateRole(String role) { if (!List.of("ADMIN", "CUSTOMER_SERVICE").contains(role)) throw new BusinessException(400, "平台角色无效"); }
    private String initialPassword() { StringBuilder value = new StringBuilder("Xm@"); while (value.length() < 12) value.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length()))); return value.toString(); }
    private String required(Map<String, Object> body, String key, String label, int max) { String value = optional(body, key, max, label); if (value.isEmpty()) throw new BusinessException(400, "请填写" + label); return value; }
    private String optional(Map<String, Object> body, String key, int max, String label) { String value = clean(body == null ? null : body.get(key)); if (value.length() > max) throw new BusinessException(400, label + "不能超过" + max + "个字符"); return value; }
    private String clean(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String emptyToNull(String value) { return value.isEmpty() ? null : value; }
    private Number number(Object value) { if (!(value instanceof Number)) throw new BusinessException(500, "用户数据异常"); return (Number) value; }
    private int integer(Object raw, int defaultValue, int min, int max, String label) { if (raw == null || clean(raw).isEmpty()) return defaultValue; try { int value = Integer.parseInt(clean(raw)); if (value < min || value > max) throw new NumberFormatException(); return value; } catch (NumberFormatException exception) { throw new BusinessException(400, label + "参数无效"); } }

    /** 保存完成校验的平台账号输入，避免事务写入阶段重复解析请求数据。 */
    private static final class UserInput {
        private final String username; private final String realName; private final String email;
        private final String roleCode; private final String reason;
        private UserInput(String username, String realName, String email, String roleCode, String reason) {
            this.username = username; this.realName = realName; this.email = email; this.roleCode = roleCode; this.reason = reason;
        }
    }
}
