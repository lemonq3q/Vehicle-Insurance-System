package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.domain.authenticate.UserDTO;
import com.example.insurancesystem.service.LoginService;
import com.example.insurancesystem.service.UserService;
import com.example.insurancesystem.integration.SaasSsoClient;
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
        return createSession(loginUser);
    }

    @Override
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
        return createSession(loginUser);
    }

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

    private Long number(Object value, String label) {
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception exception) {
            throw new BusinessException(502, "SaaS 认证结果缺少" + label);
        }
    }

    @Override
    public ResponseResult logout() {
        UsernamePasswordAuthenticationToken authentication = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        Long userid = loginUser.getUser().getId();
        String sessionId = loginUser.getSessionId();
        sessionManager.remove(userid, sessionId);
        return new ResponseResult(200, "logout succeed");
    }

    @Override
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
