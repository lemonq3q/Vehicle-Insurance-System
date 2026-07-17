package com.example.insurancesystem.saas.integration.sms;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.utils.RedisCache;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class MockSmsVerificationService implements SmsVerificationService {
  private static final Set<String> SCENES = Set.of("REGISTER", "RESET_PASSWORD");
  private static final String MOCK_CODE = "123456";
  private final RedisCache redisCache;

  public MockSmsVerificationService(RedisCache redisCache) {
    this.redisCache = redisCache;
  }

  public Map<String, Object> send(String phone, String scene) {
    validate(phone, scene);
    String cooldownKey = "portal:sms:cooldown:" + scene + ":" + phone;
    if (redisCache.getCacheObject(cooldownKey) != null)
      throw new BusinessException(429, "验证码发送过于频繁");
    redisCache.setCacheObject("portal:sms:" + scene + ":" + phone, MOCK_CODE, 5, TimeUnit.MINUTES);
    redisCache.setCacheObject(cooldownKey, "1", 60, TimeUnit.SECONDS);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("expiresInSeconds", 300);
    result.put("retryAfterSeconds", 60);
    result.put("mockCode", MOCK_CODE);
    return result;
  }

  public void verify(String phone, String scene, String code) {
    validate(phone, scene);
    String key = "portal:sms:" + scene + ":" + phone;
    String expected = redisCache.getCacheObject(key);
    if (expected == null || !expected.equals(code)) throw new BusinessException(400, "验证码错误或已失效");
    redisCache.deleteObject(key);
  }

  private void validate(String phone, String scene) {
    if (phone == null || !phone.matches("^1\\d{10}$")) throw new BusinessException(400, "手机号格式错误");
    if (!SCENES.contains(scene)) throw new BusinessException(400, "验证码场景不支持");
  }
}
