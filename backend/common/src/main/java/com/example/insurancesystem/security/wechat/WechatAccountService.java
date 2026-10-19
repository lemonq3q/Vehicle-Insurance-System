package com.example.insurancesystem.security.wechat;

import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.handler.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 维护微信身份与共享账号的一对一关系；门户可注册，车险仅查询已有身份。
 * 注册和首次绑定各使用独立数据库事务，不改变企业关系；所有冲突均拒绝，不合并账号。
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "insurance.wechat.account-enabled", havingValue = "true")
public class WechatAccountService {
    private final WechatAccountMapper mapper;

    /** 注入共享用户表访问器，微信网络请求由上层在事务外完成。 */
    public WechatAccountService(WechatAccountMapper mapper) { this.mapper = mapper; }

    /**
     * 查找有效微信账号，门户允许在不存在时自动注册；并发注册使用唯一索引回收相同结果。
     * @param identity 微信服务器确认的身份，不包含头像和长期凭证
     * @param allowRegistration 仅门户登录传 true，车险固定 false
     * @return 已启用共享用户 ID；不存在、软删除或停用时拒绝
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Long resolve(WechatClient.Identity identity, boolean allowRegistration) {
        User user = mapper.findIdentity(identity.appId(), identity.openId());
        if (user != null) return enabledId(user);
        if (!allowRegistration) throw new BusinessException(404, "账号不存在，请先前往企业门户注册或绑定微信");
        Map<String, Object> values = values(identity);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        values.put("username", "wx_" + suffix);
        values.put("name", identity.nickname().isBlank() ? "微信用户" + suffix.substring(0, 8) : identity.nickname());
        try {
            mapper.insertWechatUser(values);
            return ((Number) values.get("id")).longValue();
        } catch (DuplicateKeyException exception) {
            /* 并发首次扫码只保留一个账号；不把其他数据库错误降级为注册成功。 */
            user = mapper.findIdentity(identity.appId(), identity.openId());
            if (user == null) throw new BusinessException(409, "微信身份注册冲突，请重新扫码");
            return enabledId(user);
        }
    }

    /**
     * 为当前认证账号首次绑定微信；同身份重复提交幂等，不允许替换现有身份或转移他人身份。
     * @param userId 从绑定事务和当前登录上下文交叉确认的用户 ID
     * @param identity 微信端验证的身份
     */
    @Transactional
    public void bind(Long userId, WechatClient.Identity identity) {
        Map<String, Object> current = mapper.lockBinding(userId);
        enabledId(mapper.findUser(userId));
        if (current != null && current.get("openId") != null) {
            if (identity.appId().equals(current.get("appId")) && identity.openId().equals(current.get("openId"))) return;
            throw new BusinessException(409, "当前账号已绑定微信，不支持替换");
        }
        User owner = mapper.findIdentity(identity.appId(), identity.openId());
        if (owner != null) throw new BusinessException(409, "该微信已关联其他账号");
        Map<String, Object> values = values(identity); values.put("id", userId);
        try {
            if (mapper.bind(values) != 1) throw new BusinessException(409, "账号绑定状态已变化，请重试");
        } catch (DuplicateKeyException exception) { throw new BusinessException(409, "该微信已关联其他账号"); }
    }

    /** 只接受真实存在且未删除、启用的账号，身份保留不代表允许停用账号重新登录。 */
    private Long enabledId(User user) {
        if (user == null || mapper.findUser(user.getId()) == null || !Integer.valueOf(1).equals(user.getStatus()))
            throw new BusinessException(403, "账号不可用，请联系管理员");
        return user.getId();
    }

    /** 生成参数化写入数据，昵称仅用于注册初值，不包含头像、角色或浏览器传入的用户 ID。 */
    private Map<String, Object> values(WechatClient.Identity identity) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("appId", identity.appId()); values.put("openId", identity.openId());
        values.put("unionId", identity.unionId()); values.put("nickname", identity.nickname());
        return values;
    }
}
