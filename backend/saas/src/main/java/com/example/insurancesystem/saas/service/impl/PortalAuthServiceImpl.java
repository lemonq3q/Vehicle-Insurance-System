package com.example.insurancesystem.saas.service.impl;

import com.example.insurancesystem.domain.authenticate.LoginUser;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.utils.email.EmailAccountService;
import com.example.insurancesystem.utils.email.EmailVerificationService;
import com.example.insurancesystem.saas.mapper.EnterpriseMapper;
import com.example.insurancesystem.saas.mapper.PortalUserMapper;
import com.example.insurancesystem.saas.service.PortalAuthService;
import com.example.insurancesystem.saas.support.PortalMaps;
import com.example.insurancesystem.security.SingleLoginSessionManager;
import com.example.insurancesystem.utils.JwtUtil;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 编排门户邮箱登录与共享账号注册、重置，登录后仍由既有会话和企业上下文服务加载权限。
 * 不保存原始密码，注册和重置委托共享邮件账号服务保证两端规则一致。
 */
@Service
public class PortalAuthServiceImpl implements PortalAuthService {
  private final AuthenticationManager authenticationManager;
  private final SingleLoginSessionManager sessionManager;
  private final PortalUserMapper userMapper;
  private final EnterpriseMapper enterpriseMapper;
  private final PasswordEncoder passwordEncoder;
  private final EmailAccountService emailAccounts;

  public PortalAuthServiceImpl(
      AuthenticationManager authenticationManager,
      SingleLoginSessionManager sessionManager,
      PortalUserMapper userMapper,
      EnterpriseMapper enterpriseMapper,
      PasswordEncoder passwordEncoder,
      EmailAccountService emailAccounts) {
    this.authenticationManager = authenticationManager;
    this.sessionManager = sessionManager;
    this.userMapper = userMapper;
    this.enterpriseMapper = enterpriseMapper;
    this.passwordEncoder = passwordEncoder;
    this.emailAccounts = emailAccounts;
  }

  public Map<String, Object> login(Map<String, Object> body) {
    String username = EmailVerificationService.normalizeEmail(text(body, "email"));
    String password = body.get("password") == null ? "" : body.get("password").toString();
    if (password.isEmpty()) throw new BusinessException(400, "密码不能为空");
    Authentication authentication;
    try {
      authentication =
          authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(username, password));
    } catch (DisabledException exception) {
      throw new BusinessException(403, "账号未启用");
    } catch (BadCredentialsException exception) {
      throw new BusinessException(400, "邮箱或密码错误");
    }
    LoginUser loginUser = (LoginUser) authentication.getPrincipal();
    return createSession(loginUser, null);
  }

  @Override
  public Map<String, Object> ssoLogin(Long userId, Long enterpriseId) {
    User user = userMapper.findById(userId);
    if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
      throw new BusinessException(403, "用户账号不可用");
    }
    Map<String, Object> member = PortalMaps.camel(enterpriseMapper.findMemberByUser(enterpriseId, userId));
    if (member == null || ((Number) member.get("status")).intValue() != 1) {
      throw new BusinessException(403, "用户不属于当前企业或成员状态不可用");
    }
    Map<String, Object> enterprise = PortalMaps.camel(enterpriseMapper.findEnterprise(enterpriseId));
    if (enterprise == null || ((Number) enterprise.get("status")).intValue() != 1) {
      throw new BusinessException(403, "当前企业不可用");
    }
    user.setEnterpriseId(enterpriseId);
    user.setMemberStatus(((Number) member.get("status")).intValue());
    LoginUser loginUser = new LoginUser(user, Collections.singletonList("portal:access"));
    return createSession(loginUser, enterpriseId);
  }

  private Map<String, Object> createSession(LoginUser loginUser, Long preferredEnterpriseId) {
    Long userId = loginUser.getUser().getId();
    String sessionId = JwtUtil.getUUID();
    String token = JwtUtil.createJWT(userId.toString(), JwtUtil.LOGIN_JWT_TTL, sessionId);
    sessionManager.save(userId, sessionId, loginUser);
    userMapper.updateLastLogin(userId, LocalDateTime.now());
    List<Map<String, Object>> enterprises = PortalMaps.camel(userMapper.findEnterprises(userId));
    Map<String, Object> currentMember = PortalMaps.camel(
        preferredEnterpriseId == null
            ? enterpriseMapper.findCurrentMember(userId)
            : enterpriseMapper.findMemberByUser(preferredEnterpriseId, userId));
    Long currentEnterpriseId =
        currentMember == null ? null : ((Number) currentMember.get("enterpriseId")).longValue();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("token", token);
    result.put("user", PortalMaps.camel(userMapper.findProfile(userId)));
    result.put("enterprises", enterprises);
    result.put("currentEnterpriseId", currentEnterpriseId);
    result.put(
        "currentEnterprise",
        currentEnterpriseId == null
            ? null
            : PortalMaps.camel(enterpriseMapper.findEnterprise(currentEnterpriseId)));
    result.put("currentMember", currentMember);
    return result;
  }

  /**
   * 创建经过真实邮箱验证的共享个人账号，不自动分配企业权限。
   */
  @Transactional
  public Map<String, Object> register(Map<String, Object> body) {
    return emailAccounts.register(body);
  }

  /**
   * 验证邮件后重置共享密码，保证门户与车险端使用同一账号资料。
   */
  @Transactional
  public boolean forgetPassword(Map<String, Object> body) {
    return emailAccounts.resetPassword(body);
  }

  private String text(Map<String, Object> body, String key) {
    Object value = body.get(key);
    if (value == null || value.toString().isBlank()) throw new BusinessException(400, key + "不能为空");
    return value.toString().trim();
  }
}
