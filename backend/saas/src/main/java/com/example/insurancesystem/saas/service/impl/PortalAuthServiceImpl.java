package com.example.insurancesystem.saas.service.impl;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.saas.integration.sms.SmsVerificationService;
import com.example.insurancesystem.saas.mapper.PortalUserMapper;
import com.example.insurancesystem.saas.service.PortalAuthService;
import com.example.insurancesystem.saas.support.PortalMaps;
import com.example.insurancesystem.security.SingleLoginSessionManager;
import com.example.insurancesystem.utils.JwtUtil;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalAuthServiceImpl implements PortalAuthService {
  private final AuthenticationManager authenticationManager;
  private final SingleLoginSessionManager sessionManager;
  private final PortalUserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final SmsVerificationService smsService;

  public PortalAuthServiceImpl(
      AuthenticationManager authenticationManager,
      SingleLoginSessionManager sessionManager,
      PortalUserMapper userMapper,
      PasswordEncoder passwordEncoder,
      SmsVerificationService smsService) {
    this.authenticationManager = authenticationManager;
    this.sessionManager = sessionManager;
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.smsService = smsService;
  }

  public Map<String, Object> login(Map<String, Object> body) {
    String username = text(body, "username");
    String password = text(body, "password");
    Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(username, password));
    LoginUser loginUser = (LoginUser) authentication.getPrincipal();
    Long userId = loginUser.getUser().getId();
    String sessionId = JwtUtil.getUUID();
    String token = JwtUtil.createJWT(userId.toString(), JwtUtil.LOGIN_JWT_TTL, sessionId);
    sessionManager.save(userId, sessionId, loginUser);
    userMapper.updateLastLogin(userId, LocalDateTime.now());
    List<Map<String, Object>> enterprises = PortalMaps.camel(userMapper.findEnterprises(userId));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("token", token);
    result.put("user", PortalMaps.camel(userMapper.findProfile(userId)));
    result.put("enterprises", enterprises);
    result.put("currentEnterpriseId", enterprises.isEmpty() ? null : enterprises.get(0).get("id"));
    return result;
  }

  @Transactional
  public Map<String, Object> register(Map<String, Object> body) {
    String phone = text(body, "phone"),
        code = text(body, "smsCode"),
        realName = text(body, "realName"),
        password = text(body, "password");
    if (userMapper.countByPhone(phone) > 0) throw new BusinessException(400, "手机号已注册");
    validatePassword(password);
    smsService.verify(phone, "REGISTER", code);
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("phone", phone);
    user.put("realName", realName);
    user.put("password", passwordEncoder.encode(password));
    userMapper.insertUser(user);
    return PortalMaps.camel(userMapper.findProfile(((Number) user.get("id")).longValue()));
  }

  @Transactional
  public boolean forgetPassword(Map<String, Object> body) {
    String phone = text(body, "phone"),
        code = text(body, "smsCode"),
        password = text(body, "password");
    validatePassword(password);
    smsService.verify(phone, "RESET_PASSWORD", code);
    if (userMapper.updatePassword(phone, passwordEncoder.encode(password)) == 0)
      throw new BusinessException(404, "手机号不存在");
    return true;
  }

  private void validatePassword(String password) {
    if (password.length() < 8) throw new BusinessException(400, "密码至少需要 8 位");
  }

  private String text(Map<String, Object> body, String key) {
    Object value = body.get(key);
    if (value == null || value.toString().isBlank()) throw new BusinessException(400, key + "不能为空");
    return value.toString().trim();
  }
}
