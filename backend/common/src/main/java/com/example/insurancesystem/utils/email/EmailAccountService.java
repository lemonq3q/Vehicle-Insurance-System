package com.example.insurancesystem.utils.email;

import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.handler.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

/**
 * 两端共享的邮箱注册、重置和首次绑定流程，只处理个人账号，不授予企业权限。
 * 与验证码服务、账号 Mapper 和密码编码器协作；写操作使用事务和邮箱唯一约束。
 * 绑定保留用户 ID、内部账号标识和所有企业关系，普通资料编辑不能替代邮件验证。
 */
@Service
@ConditionalOnProperty(name = "insurance.email.account-enabled", havingValue = "true")
public class EmailAccountService {
    private final EmailAccountMapper mapper;
    private final EmailVerificationService codes;
    private final PasswordEncoder encoder;

    /**
     * 注入共享账号存储、真实验证码服务和既有 BCrypt 密码编码器。
     */
    public EmailAccountService(EmailAccountMapper mapper, EmailVerificationService codes, PasswordEncoder encoder) {
        this.mapper = mapper; this.codes = codes; this.encoder = encoder;
    }

    /**
     * 匿名申请注册或重置验证码，绑定必须使用独立的登录态接口。
     */
    public Map<String, Object> sendCode(Map<String, Object> body) {
        String email = email(body), scene = text(body, "scene");
        if ("REGISTER".equals(scene)) requireAvailable(email);
        else if ("RESET_PASSWORD".equals(scene)) requireEnabled(findEmailUser(email));
        else throw new BusinessException(400, "邮箱验证码场景不正确");
        return codes.send(email, scene, null);
    }

    /**
     * 创建邮箱所有权已经验证的共享个人账号，不依赖企业上下文。
     * @return 不含密码的个人资料；邮箱冲突由预检及数据库唯一索引共同拦截
     */
    @Transactional
    public Map<String, Object> register(Map<String, Object> body) {
        String email = email(body), password = password(body), name = text(body, "realName");
        if (name.length() > 100) throw new BusinessException(400, "姓名最多 100 字符");
        requireAvailable(email);
        codes.verifyAndConsume(email, "REGISTER", null, text(body, "emailCode"));
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("email", email); user.put("password", encoder.encode(password)); user.put("realName", name);
        try { mapper.insertUser(user); }
        catch (DuplicateKeyException exception) { throw new BusinessException(409, "邮箱已被使用"); }
        return profile(((Number) user.get("id")).longValue());
    }

    /**
     * 验证邮箱后仅按对应用户 ID 重置密码，校验失败不修改资料；验证码不能用于其他场景。
     */
    @Transactional
    public boolean resetPassword(Map<String, Object> body) {
        String email = email(body), password = password(body);
        User user = findEmailUser(email); requireEnabled(user);
        codes.verifyAndConsume(email, "RESET_PASSWORD", null, text(body, "emailCode"));
        if (mapper.updatePassword(user.getId(), encoder.encode(password)) != 1)
            throw new BusinessException(409, "账号状态已变化，请重新验证");
        return true;
    }

    /**
     * 当前账号尚无邮箱时发送绑定码，验证码关联该登录用户，防止跨账号复用。
     */
    public Map<String, Object> sendBindingCode(Long userId, Map<String, Object> body) {
        requireUnbound(mapper.findById(userId));
        String email = email(body); requireAvailable(email);
        return codes.send(email, "BIND_EMAIL", userId);
    }

    /**
     * 首次绑定邮箱并设置登录密码；在事务中锁定当前账号，防止并发覆盖已有绑定。
     * @param userId 来自服务端登录上下文，禁止使用请求 body 提供的用户 ID
     * @return 绑定后的安全个人资料
     */
    @Transactional
    public Map<String, Object> bindEmail(Long userId, Map<String, Object> body) {
        String email = email(body), password = password(body);
        requireUnbound(mapper.lockById(userId)); requireAvailable(email);
        codes.verifyAndConsume(email, "BIND_EMAIL", userId, text(body, "emailCode"));
        try {
            if (mapper.bindEmail(userId, email, encoder.encode(password)) != 1)
                throw new BusinessException(409, "当前账号已绑定邮箱或状态已变化");
        } catch (DuplicateKeyException exception) { throw new BusinessException(409, "邮箱已被使用"); }
        return profile(userId);
    }

    /**
     * 返回实际当前账号资料并规范数据库字段名，不暴露认证哈希。
     */
    public Map<String, Object> profile(Long userId) {
        Map<String, Object> row = mapper.findProfile(userId);
        if (row == null) throw new BusinessException(404, "用户不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        row.forEach((key, value) -> {
            StringBuilder name = new StringBuilder(); boolean upper = false;
            for (char ch : key.toCharArray()) {
                if (ch == '_') upper = true;
                else { name.append(upper ? Character.toUpperCase(ch) : ch); upper = false; }
            }
            result.put(name.toString(), value);
        });
        Object bound = result.get("wechatBound");
        result.put("wechatBound", Boolean.TRUE.equals(bound) || (bound instanceof Number && ((Number) bound).intValue() == 1));
        return result;
    }

    /**
     * 从认证上下文确定本人后更新普通资料，联系电话可选；请求中的 email 和角色字段被忽略。
     * @return 更新后的安全个人资料，账号不存在或电话已被占用时拒绝更新
     */
    public Map<String, Object> updateProfile(Long userId, Map<String, Object> body) {
        String name = text(body, "realName");
        if (name.length() > 100) throw new BusinessException(400, "姓名最多 100 字符");
        String phone = body.get("phone") == null ? null : body.get("phone").toString().trim();
        if (phone != null && phone.isBlank()) phone = null;
        if (phone != null && !phone.matches("^1\\d{10}$")) throw new BusinessException(400, "手机号格式错误");
        if (phone != null && mapper.countPhoneExcept(phone, userId) > 0) throw new BusinessException(409, "手机号已被使用");
        Object idNum = body.get("idNum");
        if (idNum != null && idNum.toString().length() > 100) throw new BusinessException(400, "证件号最多 100 字符");
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", userId); values.put("realName", name); values.put("phone", phone); values.put("idNum", idNum);
        if (mapper.updateProfile(values) != 1) throw new BusinessException(404, "用户不存在");
        return profile(userId);
    }

    /**
     * 规范邮箱请求参数，允许大小写和首尾空白差异但不允许空身份。
     */
    private String email(Map<String, Object> body) { return EmailVerificationService.normalizeEmail(text(body, "email")); }
    /**
     * 邮箱唯一身份查询；历史重复资料拒绝认证，避免随机取一个账号。
     */
    private User findEmailUser(String email) {
        List<User> users = mapper.findByEmail(email);
        if (users.isEmpty()) throw new BusinessException(404, "邮箱未注册");
        if (users.size() != 1) throw new BusinessException(409, "邮箱关联多个账号，请联系管理员");
        return users.get(0);
    }
    /**
     * 只有未被有效账号使用的邮箱可以注册或首次绑定。
     */
    private void requireAvailable(String email) {
        if (!mapper.findByEmail(email).isEmpty()) throw new BusinessException(409, "邮箱已被使用");
    }
    /**
     * 软删除、不存在或停用账号不能进行密码重置和邮箱绑定。
     */
    private void requireEnabled(User user) {
        if (user == null) throw new BusinessException(404, "用户不存在");
        if (!Integer.valueOf(1).equals(user.getStatus())) throw new BusinessException(403, "账号未启用");
    }
    /**
     * 首次绑定入口不得覆盖已有邮箱，允许后续微信创建的无邮箱用户沿用。
     */
    private void requireUnbound(User user) {
        requireEnabled(user);
        if (user.getEmail() != null && !user.getEmail().isBlank()) throw new BusinessException(409, "当前账号已绑定邮箱");
    }
    /**
     * 新密码保留原始空白字符，并检查字符与 BCrypt 字节上限。
     */
    private String password(Map<String, Object> body) {
        Object value = body.get("password"); String password = value == null ? "" : value.toString();
        if (password.length() < 8 || password.length() > 64 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException(400, "密码需为 8 至 64 位，且编码长度不超过 72 字节");
        return password;
    }
    /**
     * 读取并校验普通文本参数；只有密码使用专门方法保留首尾空白。
     */
    private String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) throw new BusinessException(400, key + "不能为空");
        return value.toString().trim();
    }
}
