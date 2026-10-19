package com.example.insurancesystem.security.wechat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 两端共享微信网站应用配置；AppID 一致，前端回调地址按部署端分别设置。
 * 密钥只从后端环境读取，不暴露给前端；消息推送参数只供 SaaS 安全回调使用。
 * 内部使用 callback-uri 接收各端 YAML 的地址，避免共享环境中的
 * INSURANCE_WECHAT_REDIRECT_URI 直接覆盖门户配置；外部接口仍返回 redirectUri。
 */
@Data
@Component
@ConfigurationProperties(prefix = "insurance.wechat")
public class WechatProperties {
    private boolean enabled;
    private String appId = "";
    private String appSecret = "";
    private String callbackUri = "";
    private String messageToken = "";
    private String encodingAesKey = "";
}
