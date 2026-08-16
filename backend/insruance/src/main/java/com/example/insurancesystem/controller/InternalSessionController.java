package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.service.InsuranceSessionInvalidationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * 暴露给 SaaS 后端的车险会话强制失效接口。
 * 接口不依赖终端用户 JWT，而使用与 SSO 相同的服务端共享密钥鉴权；仅接受明确的企业或用户主键，
 * 不提供任意 Redis 键操作能力，适用于套餐暂停和成员关系删除后的即时下线。
 */
@RestController
@RequestMapping("/internal/session")
public class InternalSessionController {
    private final InsuranceSessionInvalidationService service;
    private final String clientSecret;

    public InternalSessionController(
            InsuranceSessionInvalidationService service,
            @Value("${insurance.sso.client-secret:change-me-in-production}") String clientSecret) {
        this.service = service;
        this.clientSecret = clientSecret;
    }

    /** 按企业清除全部成员当前车险会话。 */
    @PostMapping("/logout-enterprise")
    public ResponseResult<?> logoutEnterprise(
            @RequestHeader(value = "X-Insurance-Client-Secret", required = false) String suppliedSecret,
            @RequestBody Map<String, Object> body) {
        requireTrustedClient(suppliedSecret);
        Long enterpriseId = requiredId(body, "enterpriseId");
        return new ResponseResult<>(200, "企业车险会话已失效", service.logoutEnterprise(enterpriseId));
    }

    /** 按用户清除其当前车险会话。 */
    @PostMapping("/logout-user")
    public ResponseResult<?> logoutUser(
            @RequestHeader(value = "X-Insurance-Client-Secret", required = false) String suppliedSecret,
            @RequestBody Map<String, Object> body) {
        requireTrustedClient(suppliedSecret);
        Long userId = requiredId(body, "userId");
        return new ResponseResult<>(200, "用户车险会话已失效", service.logoutUser(userId));
    }

    private void requireTrustedClient(String suppliedSecret) {
        byte[] expected = clientSecret.getBytes(StandardCharsets.UTF_8);
        byte[] actual = (suppliedSecret == null ? "" : suppliedSecret).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual))
            throw new BusinessException(403, "SaaS 服务认证失败");
    }

    private Long requiredId(Map<String, Object> body, String field) {
        Object value = body == null ? null : body.get(field);
        if (!(value instanceof Number) || ((Number) value).longValue() <= 0)
            throw new BusinessException(400, field + " 参数不正确");
        return ((Number) value).longValue();
    }
}
