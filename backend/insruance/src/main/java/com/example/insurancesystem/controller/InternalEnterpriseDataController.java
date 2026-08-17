package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.maintenance.MaintenanceParticipantProperties;
import com.example.insurancesystem.service.EnterpriseBusinessDataPurgeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 提供仅供系统维护调用的企业车险资料清理接口。
 * 路径位于 /internal/maintenance 下，因此车险服务进入维护模式后仍可处理；接口不接受用户 JWT 授权，
 * 而是校验协调维护共享密钥。外部调用者即使知道地址，也不能在缺少系统密钥时触发不可逆数据删除。
 */
@RestController
@RequestMapping("/internal/maintenance/enterprise-data")
public class InternalEnterpriseDataController {
    private final EnterpriseBusinessDataPurgeService purgeService;
    private final MaintenanceParticipantProperties properties;

    /**
     * @param purgeService 车险域物理清理服务
     * @param properties 当前车险参与端的维护共享密钥配置
     */
    public InternalEnterpriseDataController(
            EnterpriseBusinessDataPurgeService purgeService,
            MaintenanceParticipantProperties properties) {
        this.purgeService = purgeService;
        this.properties = properties;
    }

    /**
     * 校验系统维护密钥后清除指定企业的车险业务资料。请求以 enterpriseId 作为唯一幂等业务标识，
     * 已清空企业再次调用不会重建或影响 SaaS 域数据，只会返回零删除统计。
     *
     * @param suppliedSecret SaaS 维护客户端携带的共享密钥
     * @param body 必须包含正数 enterpriseId
     * @return 各车险业务表及 OSS 对象的删除数量
     */
    @PostMapping("/purge")
    public ResponseResult<Map<String, Integer>> purge(
            @RequestHeader(value = "X-Maintenance-Secret", required = false) String suppliedSecret,
            @RequestBody Map<String, Object> body) {
        requireSystemClient(suppliedSecret);
        Object value = body == null ? null : body.get("enterpriseId");
        if (!(value instanceof Number) || ((Number) value).longValue() <= 0) {
            throw new BusinessException(400, "enterpriseId 参数不正确");
        }
        Map<String, Integer> result = purgeService.purge(((Number) value).longValue());
        return new ResponseResult<>(200, "企业车险业务资料已清除", result);
    }

    /**
     * 使用常量时间字节比较验证系统密钥，降低普通字符串比较暴露的时序差异。
     */
    private void requireSystemClient(String suppliedSecret) {
        byte[] expected = properties.getInternalSecret().getBytes(StandardCharsets.UTF_8);
        byte[] actual = (suppliedSecret == null ? "" : suppliedSecret).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException(403, "系统维护服务认证失败");
        }
    }
}
