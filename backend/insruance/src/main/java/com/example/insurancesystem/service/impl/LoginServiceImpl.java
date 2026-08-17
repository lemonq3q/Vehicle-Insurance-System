package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.domain.authenticate.UserDTO;
import com.example.insurancesystem.service.LoginService;
import com.example.insurancesystem.service.UserService;
import com.example.insurancesystem.integration.client.SaasSsoClient;
import com.example.insurancesystem.mapper.MenuMapper;
import com.example.insurancesystem.mapper.UserMapper;
import com.example.insurancesystem.security.SingleLoginSessionManager;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.utils.EmailUtil;
import com.example.insurancesystem.utils.JwtUtil;
import com.example.insurancesystem.utils.RedisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
/**
 * 编排车险系统的账号密码登录、SaaS 门户单点登录、会话注销和邮箱找回密码流程。
 * 两种登录方式最终统一建立带会话标识的 JWT，并由会话管理器落实单账号会话控制。
 */
public class LoginServiceImpl implements LoginService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private SingleLoginSessionManager sessionManager;

    @Autowired
    private SaasSsoClient saasSsoClient;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MenuMapper menuMapper;

    @Override
    /**
     * 使用 Spring Security 校验账号密码，并要求用户已加入企业后才允许进入车险业务系统。
     */
    public ResponseResult login(User user) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword());
        Authentication authenticate;
        try {
            authenticate = authenticationManager.authenticate(authenticationToken);
        } catch (DisabledException exception) {
            throw new BusinessException(403, "账号未启用");
        } catch (BadCredentialsException exception) {
            throw new BusinessException(400, "用户名或密码错误");
        }
        if(authenticate == null) {
            throw new RuntimeException("Authentication failed");
        }
        LoginUser loginUser = (LoginUser) authenticate.getPrincipal();
        if (loginUser.getEnterpriseId() == null) {
            throw new BusinessException(403, "尚未加入企业，请先前往 SaaS 门户创建或加入企业");
        }
        validateSubscriptionAccess(loginUser.getUser());
        return createSession(loginUser);
    }

    @Override
    /**
     * 用 SaaS 门户签发的一次性授权码换取用户和企业身份，再从本系统加载成员状态及菜单权限。
     * 只有仍属于目标企业且账号、成员均启用的用户才能建立车险系统会话。
     */
    public ResponseResult ssoLogin(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(400, "授权码不能为空");
        }
        Map<String, Object> identity = saasSsoClient.exchange(code);
        Long userId = number(identity.get("userId"), "用户标识");
        Long enterpriseId = number(identity.get("enterpriseId"), "企业标识");
        User ssoUser = userMapper.selectSsoUser(userId, enterpriseId);
        if (ssoUser == null) {
            throw new BusinessException(403, "用户不属于当前企业或企业不可用");
        }
        LoginUser loginUser = new LoginUser(
                ssoUser, menuMapper.selectPermsByUserId(userId, enterpriseId));
        if (!loginUser.isEnabled()) {
            throw new BusinessException(403, "账号或企业成员未启用");
        }
        validateSubscriptionAccess(ssoUser);
        return createSession(loginUser);
    }

    /**
     * 在两种车险登录方式建立会话前统一校验套餐访问状态。
     * 欠费暂停提供可执行的充值提示；未订阅、到期及其他暂停状态统一拒绝进入车险后台，但不影响用户
     * 登录 SaaS 门户完成充值、续订或联系平台处理。
     *
     * @param user 登录查询聚合出的用户、企业成员及当前订阅状态
     */
    private void validateSubscriptionAccess(User user) {
        if (Integer.valueOf(1).equals(user.getSubscriptionStatus())) return;
        if (Integer.valueOf(3).equals(user.getSubscriptionStatus())
                && "ARREARS".equals(user.getSubscriptionSuspendReason())) {
            throw new BusinessException(403, "企业套餐因余额欠费已暂停，请前往 SaaS 门户充值");
        }
        throw new BusinessException(403, "企业当前没有可用的有效套餐，请前往 SaaS 门户查看订阅状态");
    }

    /**
     * 为已经完成身份校验的用户创建统一登录结果。
     * JWT 中写入独立 jti，并将同一标识连同登录主体保存到会话存储，供后续鉴权及主动失效使用。
     */
    private ResponseResult createSession(LoginUser loginUser) {
        String userid = loginUser.getUser().getId().toString();
        // 用jti来作为会话级标识
        String jti = JwtUtil.getUUID();
        // jwt有效时间设为一周
        String jwt = JwtUtil.createJWT(userid, JwtUtil.LOGIN_JWT_TTL, jti);
        UserDTO userDTO = new UserDTO(loginUser.getUser().getId(), loginUser.getUser().getUsername(), loginUser.getUser().getRealName(), loginUser.getPermissions());
        Map<String, Object> map = new HashMap<>();
        map.put("user", userDTO);
        map.put("token", jwt);
        // 无操作24个小时之后过期
        sessionManager.save(loginUser.getUser().getId(), jti, loginUser);
        ResponseResult result = new ResponseResult(200, "login succeed", map);
        return result;
    }

    /**
     * 将 SaaS 身份响应中的数值字段安全转换为 Long；字段缺失或格式异常视为上游认证结果错误。
     */
    private Long number(Object value, String label) {
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception exception) {
            throw new BusinessException(502, "SaaS 认证结果缺少" + label);
        }
    }

    @Override
    /**
     * 删除当前 JWT 对应的服务端会话，使该令牌后续无法继续通过鉴权。
     */
    public ResponseResult logout() {
        UsernamePasswordAuthenticationToken authentication = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        Long userid = loginUser.getUser().getId();
        String sessionId = loginUser.getSessionId();
        sessionManager.remove(userid, sessionId);
        return new ResponseResult(200, "logout succeed");
    }

    @Override
    /**
     * 向已登记邮箱发送六位找回密码验证码。
     * 同一邮箱一分钟内禁止重复发送，验证码及发送时间在 Redis 中保留五分钟。
     */
    public ResponseResult getEmailCode(String email) {
        String storeCode = redisCache.getCacheObject("email:" + email);
        if (storeCode != null) {
            Long storeTime = Long.parseLong(storeCode.split(":")[1]);
            if (System.currentTimeMillis() - storeTime < 60 * 1000) {
                return new ResponseResult(400, "请一分钟后再尝试发送");
            }
        }
        ResponseResult<User> result = userService.selectByEmail(email);
        if (result.getCode() != 200) {
            return result;
        }
        Random random = new Random();
        StringBuilder codeSb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            codeSb.append(random.nextInt(10));
        }
        String code = codeSb.toString();
        if(!EmailUtil.sendEmail(email, "验证码", "您的验证码是" + code + "，请勿泄露此验证码。")){
            return new ResponseResult(400, "发送失败，请检查邮箱是否正确");
        }
        redisCache.setCacheObject("email:" + email, code + ":" + System.currentTimeMillis(), 5, TimeUnit.MINUTES);
        return new ResponseResult(200, "发送成功");
    }

    @Override
    /**
     * 校验邮箱验证码后更新密码，并在验证成功时立即删除验证码，防止重复使用。
     */
    public ResponseResult forgetPassword(String email, String code, String password) {
        String realCode = redisCache.getCacheObject("email:" + email);
        if(realCode == null){
            return new ResponseResult(400, "验证码错误");
        }
        realCode = realCode.split(":")[0];
        if (!realCode.equals(code)) {
            return new ResponseResult(400, "验证码错误");
        }
        else{
            redisCache.deleteObject("email:" + email);
        }
        return userService.updatePasswordByEmail(email, password);
    }
}
