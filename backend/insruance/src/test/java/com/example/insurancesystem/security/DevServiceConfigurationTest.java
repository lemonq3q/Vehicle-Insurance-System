package com.example.insurancesystem.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证测试服务启动参数能覆盖项目实际 YAML 的正式库默认值，并隔离内部调用与 Redis。
 * 仅加载 Spring 属性源，不创建数据库、Redis 或外部服务连接，不启动任何业务 Bean。
 */
class DevServiceConfigurationTest {
    private ch.qos.logback.classic.Level previousLogLevel;

    /**
     * 加载真实配置时关闭属性源调试日志，避免测试报告打印已有配置中的凭据。
     */
    @org.junit.jupiter.api.BeforeEach
    void silenceConfigurationValues() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        previousLogLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.WARN);
    }

    /**
     * 还原日志级别，不改变同进程内其他测试的日志行为。
     */
    @org.junit.jupiter.api.AfterEach
    void restoreLogging() {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(previousLogLevel);
    }

    /**
     * 模拟 systemd 环境文件与 Java 命令行，核对三套数据库服务最终选择测试库及测试凭据。
     * 参数解析使用新增模板的实际文本，避免只对另一份测试常量断言而漏掉部署文件错误。
     */
    @Test
    void commandLineSelectsDevDatabaseForAllDatabaseApplications() throws Exception {
        Path root=Path.of(System.getProperty("basedir"),"../..").normalize();
        String service=Files.readString(root.resolve("docs/service/idatag_dev@.service"));
        Map<String,Object> values=new HashMap<>();
        for (String line:Files.readAllLines(root.resolve("docs/service/idatag_dev.env.example"))) {
            if (!line.isBlank() && !line.startsWith("#")) {
                String[] entry=line.split("=",2);
                values.put(entry[0],entry.length==2?entry[1]:"");
            }
        }
        ArrayList<String> arguments=new ArrayList<>();
        var matcher=Pattern.compile("\"(--spring[^\"]+)\"").matcher(service);
        while(matcher.find()) {
            String argument=matcher.group(1);
            for (var entry:values.entrySet()) argument=argument.replace("${"+entry.getKey()+"}",entry.getValue().toString());
            assertFalse(argument.contains("${"));
            arguments.add(argument);
        }
        assertEquals(3,arguments.size());
        for(String module:new String[]{"insruance","saas","monitor"}) {
            StandardEnvironment env=new StandardEnvironment();
            env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            YamlPropertySourceLoader loader=new YamlPropertySourceLoader();
            Path resource=root.resolve("backend/"+module+"/src/main/resources");
            for(var source:loader.load("base",new FileSystemResource(resource.resolve("application.yml")))) env.getPropertySources().addLast(source);
            if(Files.exists(resource.resolve("application-prod.yml"))) {
                for(var source:loader.load("prod",new FileSystemResource(resource.resolve("application-prod.yml")))) env.getPropertySources().addFirst(source);
            }
            env.getPropertySources().addFirst(new SystemEnvironmentPropertySource("devEnvironment",values));
            env.getPropertySources().addFirst(new SimpleCommandLinePropertySource(arguments.toArray(String[]::new)));
            String expected="jdbc:mysql://127.0.0.1:3306/insurance_saas_dev?characterEncoding=utf-8&serverTimezone=Asia/Shanghai";
            assertEquals(expected,env.getProperty("spring.datasource.url"));
            assertEquals(expected,env.getProperty("spring.datasource.druid.url"));
            assertEquals("your_dev_database_user",env.getProperty("spring.datasource.druid.username"));
            assertEquals("replace_with_dev_database_password",env.getProperty("spring.datasource.druid.password"));
            assertEquals("1",env.getProperty("spring.redis.database"));
            if(module.equals("insruance")) {
                assertEquals("http://127.0.0.1:8081/internal/sso/exchange",env.getProperty("insurance.sso.saas-exchange-url"));
                assertEquals("insurance:",env.getProperty("spring.redis.key-prefix"));
            }
            if(module.equals("saas")) {
                assertEquals("https://lmy.idatag.com/saas",env.getProperty("portal.sso.portal-frontend-url"));
                assertEquals("http://127.0.0.1:8080/internal/maintenance/enterprise-data/purge",env.getProperty("saas.data-retention.insurance-purge-url"));
                assertEquals("portal:",env.getProperty("spring.redis.key-prefix"));
            }
        }
    }
}

