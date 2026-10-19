package com.example.insurancesystem.security;

import com.example.insurancesystem.security.wechat.WechatProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用两端实际 YAML 与 Spring 环境变量绑定器验证共享 .env 下的回调隔离。
 * 不启动完整应用、不连接数据库或 Redis，避免复制配置片段而遗漏资源文件回归。
 */
class WechatPropertiesTest {
    /** 门户必须从门户变量取值，不能被同一环境文件中的车险回调覆盖。 */
    @Test
    void sharedEnvironmentKeepsPortalCallback() {
        runner("saas", sharedEnvironment()).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals("https://example.com/saas/wechat/callback", context.getBean(WechatProperties.class).getCallbackUri());
        });
    }

    /** 车险仍从原有车险变量读取地址，门户变量不影响该端现有行为。 */
    @Test
    void sharedEnvironmentKeepsInsuranceCallback() {
        runner("insruance", sharedEnvironment()).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals("https://example.com/insurance/wechat/callback", context.getBean(WechatProperties.class).getCallbackUri());
        });
    }

    /** 门户变量缺失时使用本地默认值，不能采用另一系统的回调地址。 */
    @Test
    void missingPortalVariableDoesNotFallBackToInsurance() {
        Map<String, Object> environment = sharedEnvironment();
        environment.remove("PORTAL_WECHAT_REDIRECT_URI");
        runner("saas", environment).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals("http://localhost:8887/wechat/callback", context.getBean(WechatProperties.class).getCallbackUri());
        });
    }

    /** 门户变量不应替代车险变量；车险变量缺失时保持车险本地默认地址。 */
    @Test
    void missingInsuranceVariableDoesNotFallBackToPortal() {
        Map<String, Object> environment = sharedEnvironment();
        environment.remove("INSURANCE_WECHAT_REDIRECT_URI");
        runner("insruance", environment).run(context -> {
            assertNull(context.getStartupFailure());
            assertEquals("http://localhost:8888/wechat/callback", context.getBean(WechatProperties.class).getCallbackUri());
        });
    }

    /**
     * 模拟 systemd EnvironmentFile 注入环境，同时读取指定模块实际 YAML。
     * 只注册微信配置对象，其他 YAML 属性不触发业务 Bean，也不输出配置内容。
     */
    private ApplicationContextRunner runner(String module, Map<String, Object> environment) {
        Path yaml = Path.of(System.getProperty("basedir"), "..", module, "src/main/resources/application.yml");
        return new ApplicationContextRunner().withUserConfiguration(BindingConfig.class)
                .withInitializer(context -> {
                    context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource("sharedEnv", environment));
                    try {
                        new YamlPropertySourceLoader().load("applicationYaml", new FileSystemResource(yaml.toFile()))
                                .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                    } catch (IOException exception) {
                        throw new IllegalStateException("无法加载回调配置回归测试所需的 YAML", exception);
                    }
                });
    }

    /** 返回每例独立的共享环境副本，只含用于回调隔离验证的公开测试地址。 */
    private Map<String, Object> sharedEnvironment() {
        return new HashMap<>(Map.of(
                "PORTAL_WECHAT_REDIRECT_URI", "https://example.com/saas/wechat/callback",
                "INSURANCE_WECHAT_REDIRECT_URI", "https://example.com/insurance/wechat/callback"));
    }

    /** 仅注册真实配置绑定器及微信属性，避免应用扫描触发数据库、Redis 或账号服务。 */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WechatProperties.class)
    static class BindingConfig { }
}
