package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.domain.authenticate.UserDTO;
import com.example.insurancesystem.service.LoginService;
import com.example.insurancesystem.service.UserService;
import com.example.insurancesystem.utils.EmailUtil;
import com.example.insurancesystem.utils.JwtUtil;
import com.example.insurancesystem.utils.RedisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
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

    @Override
    public ResponseResult login(User user) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword());
        Authentication authenticate = authenticationManager.authenticate(authenticationToken);
        if(authenticate == null) {
            throw new RuntimeException("Authentication failed");
        }
        LoginUser loginUser = (LoginUser) authenticate.getPrincipal();
        String userid = loginUser.getUser().getId().toString();
        // 用jti来作为会话级标识
        String jti = JwtUtil.getUUID();
        // jwt有效时间设为一周
        String jwt = JwtUtil.createJWT(userid, JwtUtil.LOGIN_JWT_TTL, jti);
        loginUser.setSessionId(jti);
        UserDTO userDTO = new UserDTO(loginUser.getUser().getId(), loginUser.getUser().getUsername(), loginUser.getUser().getName(), loginUser.getPermissions());
        Map<String, Object> map = new HashMap<>();
        map.put("user", userDTO);
        map.put("token", jwt);
        // 无操作24个小时之后过期
        redisCache.setCacheObject("login:"+userid+":"+jti, loginUser, 24 ,TimeUnit.HOURS);
        ResponseResult result = new ResponseResult(200, "login succeed", map);
        return result;
    }

    @Override
    public ResponseResult logout() {
        UsernamePasswordAuthenticationToken authentication = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        Long userid = loginUser.getUser().getId();
        String sessionId = loginUser.getSessionId();
        redisCache.deleteObject("login:" + userid + ":" + sessionId);
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
