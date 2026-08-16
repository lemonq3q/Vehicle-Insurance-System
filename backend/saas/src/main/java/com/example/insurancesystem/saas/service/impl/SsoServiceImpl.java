package com.example.insurancesystem.saas.service.impl;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.saas.mapper.EnterpriseMapper;
import com.example.insurancesystem.saas.mapper.FinanceMapper;
import com.example.insurancesystem.saas.service.SsoService;
import com.example.insurancesystem.saas.service.PortalAuthService;
import com.example.insurancesystem.saas.support.PortalContextService;
import com.example.insurancesystem.saas.support.PortalMaps;
import com.example.insurancesystem.utils.RedisCache;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class SsoServiceImpl implements SsoService {
  private static final String CODE_NAMESPACE = "sso:insurance:code:";
  private static final String PORTAL_CODE_NAMESPACE = "sso:portal:code:";

  private final PortalContextService context;
  private final EnterpriseMapper enterpriseMapper;
  private final FinanceMapper financeMapper;
  private final RedisCache redisCache;
  private final PortalAuthService portalAuthService;
  private final String insuranceFrontendUrl;
  private final String portalFrontendUrl;
  private final String clientSecret;
  private final int codeTtlSeconds;

  public SsoServiceImpl(
      PortalContextService context,
      EnterpriseMapper enterpriseMapper,
      FinanceMapper financeMapper,
      RedisCache redisCache,
      PortalAuthService portalAuthService,
      @Value("${portal.sso.insurance-frontend-url:http://localhost:8888}") String insuranceFrontendUrl,
      @Value("${portal.sso.portal-frontend-url:http://localhost:8889}") String portalFrontendUrl,
      @Value("${portal.sso.insurance-client-secret:change-me-in-production}") String clientSecret,
      @Value("${portal.sso.code-ttl-seconds:60}") int codeTtlSeconds) {
    this.context = context;
    this.enterpriseMapper = enterpriseMapper;
    this.financeMapper = financeMapper;
    this.redisCache = redisCache;
    this.portalAuthService = portalAuthService;
    this.insuranceFrontendUrl = trimTrailingSlash(insuranceFrontendUrl);
    this.portalFrontendUrl = trimTrailingSlash(portalFrontendUrl);
    this.clientSecret = clientSecret;
    this.codeTtlSeconds = codeTtlSeconds;
  }

  @Override
  public Map<String, Object> authorize() {
    Long userId = context.userId();
    Map<String, Object> member = PortalMaps.camel(enterpriseMapper.findCurrentMember(userId));
    if (member == null) throw new BusinessException(403, "请先创建或加入企业");
    if (((Number) member.get("userStatus")).intValue() != 1)
      throw new BusinessException(403, "当前用户账号未启用");
    if (((Number) member.get("status")).intValue() != 1)
      throw new BusinessException(403, "当前企业成员状态不可用");

    Long enterpriseId = ((Number) member.get("enterpriseId")).longValue();
    Map<String, Object> enterprise = PortalMaps.camel(enterpriseMapper.findEnterprise(enterpriseId));
    if (enterprise == null || ((Number) enterprise.get("status")).intValue() != 1)
      throw new BusinessException(403, "当前企业不可用");
    Map<String, Object> subscription = PortalMaps.camel(financeMapper.findSubscription(enterpriseId));
    if (!hasActiveSubscription(subscription)) {
      if (subscription != null
          && ((Number) subscription.get("status")).intValue() == 3
          && "ARREARS".equals(subscription.get("suspendReason")))
        throw new BusinessException(403, "企业套餐因余额欠费已暂停，请先充值后再进入车险系统");
      throw new BusinessException(403, "企业当前没有正常生效的套餐，暂时无法进入车险系统");
    }

    String code = java.util.UUID.randomUUID().toString().replace("-", "")
        + java.util.UUID.randomUUID().toString().replace("-", "");
    long issuedAt = Instant.now().toEpochMilli();
    Map<String, Object> ticket = new LinkedHashMap<>();
    ticket.put("userId", userId);
    ticket.put("enterpriseId", enterpriseId);
    ticket.put("target", "INSURANCE");
    ticket.put("issuedAt", issuedAt);
    ticket.put("expiresAt", issuedAt + codeTtlSeconds * 1000L);
    redisCache.setCacheObject(CODE_NAMESPACE + code, ticket, codeTtlSeconds, TimeUnit.SECONDS);

    String redirectUrl = UriComponentsBuilder.fromHttpUrl(insuranceFrontendUrl)
        .path("/sso/callback")
        .queryParam("code", code)
        .build(true)
        .toUriString();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("redirectUrl", redirectUrl);
    result.put("expiresIn", codeTtlSeconds);
    return result;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> exchange(String suppliedSecret, Map<String, Object> body) {
    if (!secureEquals(clientSecret, suppliedSecret))
      throw new BusinessException(403, "车险服务认证失败");
    String code = body == null ? "" : String.valueOf(body.getOrDefault("code", "")).trim();
    if (code.isEmpty()) throw new BusinessException(400, "授权码不能为空");

    Map<String, Object> ticket = redisCache.getAndDeleteOnce(
        CODE_NAMESPACE + code, codeTtlSeconds, TimeUnit.SECONDS);
    if (ticket == null) throw new BusinessException(401, "授权码无效、已使用或已过期");
    if (!"INSURANCE".equals(ticket.get("target")))
      throw new BusinessException(401, "授权目标不正确");
    return ticket;
  }

  @Override
  public Map<String, Object> authorizePortal(String suppliedSecret, Map<String, Object> body) {
    if (!secureEquals(clientSecret, suppliedSecret)) {
      throw new BusinessException(403, "车险服务认证失败");
    }
    Long userId = number(body == null ? null : body.get("userId"), "用户标识");
    Long enterpriseId = number(body == null ? null : body.get("enterpriseId"), "企业标识");
    Map<String, Object> member = PortalMaps.camel(enterpriseMapper.findMemberByUser(enterpriseId, userId));
    if (member == null || ((Number) member.get("status")).intValue() != 1) {
      throw new BusinessException(403, "用户不属于当前企业或成员状态不可用");
    }
    Map<String, Object> enterprise = PortalMaps.camel(enterpriseMapper.findEnterprise(enterpriseId));
    if (enterprise == null || ((Number) enterprise.get("status")).intValue() != 1) {
      throw new BusinessException(403, "当前企业不可用");
    }

    String code = java.util.UUID.randomUUID().toString().replace("-", "")
        + java.util.UUID.randomUUID().toString().replace("-", "");
    long issuedAt = Instant.now().toEpochMilli();
    Map<String, Object> ticket = new LinkedHashMap<>();
    ticket.put("userId", userId);
    ticket.put("enterpriseId", enterpriseId);
    ticket.put("target", "PORTAL");
    ticket.put("issuedAt", issuedAt);
    ticket.put("expiresAt", issuedAt + codeTtlSeconds * 1000L);
    redisCache.setCacheObject(PORTAL_CODE_NAMESPACE + code, ticket, codeTtlSeconds, TimeUnit.SECONDS);

    String redirectUrl = UriComponentsBuilder.fromHttpUrl(portalFrontendUrl)
        .path("/sso/callback")
        .queryParam("code", code)
        .build(true)
        .toUriString();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("redirectUrl", redirectUrl);
    result.put("expiresIn", codeTtlSeconds);
    return result;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> exchangePortal(Map<String, Object> body) {
    String code = body == null ? "" : String.valueOf(body.getOrDefault("code", "")).trim();
    if (code.isEmpty()) throw new BusinessException(400, "授权码不能为空");
    Map<String, Object> ticket = redisCache.getAndDeleteOnce(
        PORTAL_CODE_NAMESPACE + code, codeTtlSeconds, TimeUnit.SECONDS);
    if (ticket == null) throw new BusinessException(401, "授权码无效、已使用或已过期");
    if (!"PORTAL".equals(ticket.get("target"))) {
      throw new BusinessException(401, "授权目标不正确");
    }
    return portalAuthService.ssoLogin(
        number(ticket.get("userId"), "用户标识"),
        number(ticket.get("enterpriseId"), "企业标识"));
  }

  private Long number(Object value, String label) {
    if (value instanceof Number) return ((Number) value).longValue();
    try {
      return Long.valueOf(String.valueOf(value));
    } catch (Exception exception) {
      throw new BusinessException(400, label + "不能为空");
    }
  }

  private boolean secureEquals(String expected, String supplied) {
    if (supplied == null) return false;
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
  }

  /** 只有状态正常、已关联套餐且结束时间晚于当前时刻的订阅可以签发车险 SSO 授权码。 */
  private boolean hasActiveSubscription(Map<String, Object> subscription) {
    if (subscription == null
        || subscription.get("planId") == null
        || ((Number) subscription.get("status")).intValue() != 1) return false;
    Object endAt = subscription.get("endAt");
    return endAt instanceof java.time.LocalDateTime
        && ((java.time.LocalDateTime) endAt).isAfter(java.time.LocalDateTime.now());
  }

  private String trimTrailingSlash(String value) {
    return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
