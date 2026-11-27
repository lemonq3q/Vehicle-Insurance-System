package com.example.insurancesystem.coordinator.config;

import com.example.insurancesystem.coordinator.backup.BackupProperties;
import java.nio.file.*;
import java.io.Reader;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.boot.env.EnvironmentPostProcessor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 从临时.env加载真实文本并验证Spring绑定与定时间隔，不接触服务器配置、MySQL或OSS。
 */
class BackupEnvironmentPostProcessorTest {
    @TempDir Path directory;

    /**
     * 验证.env的开关、路径及扫描间隔在绑定和定时任务占位符解析时已可直接使用。
     */
    @Test void bindsFileAndInterval()throws Exception {
        Path file=directory.resolve(".env");Files.writeString(file,"MAINTENANCE_BACKUP_ENABLED=true\nMAINTENANCE_BACKUP_DEFAULTS_FILE=/etc/test.cnf\nMAINTENANCE_BACKUP_BINLOG_INTERVAL_MS=12345\nMAINTENANCE_BACKUP_RETENTION_DAYS=25\n");
        Properties properties=new Properties();try(Reader reader=Files.newBufferedReader(file)){properties.load(reader);}
        StandardEnvironment environment=new StandardEnvironment();new BackupEnvironmentPostProcessor().apply(environment,properties::getProperty);
        BackupProperties bound=Binder.get(environment).bind("maintenance.backup",BackupProperties.class).get();
        assertTrue(bound.isEnabled());assertEquals("/etc/test.cnf",bound.getDefaultsFile());assertEquals(25,bound.getRetentionDays());
        assertEquals("12345",environment.resolveRequiredPlaceholders("${maintenance.backup.binlog-interval-ms:300000}"));
        assertEquals("insurance_saas",bound.getDatabase());
    }

    /**
     * 兼容已给出的紧凑变量名，易读名称优先；缺失值不创建覆盖源或开启备份。
     */
    @Test void aliasesAndMissingValues() {
        var environment=new StandardEnvironment();var processor=new BackupEnvironmentPostProcessor();
        var values=java.util.Map.of("MAINTENANCE_BACKUP_DEFAULTSFILE","/old.cnf","MAINTENANCE_BACKUP_MONITORURL","http://localhost:7777");
        processor.apply(environment,values::get);
        assertEquals("/old.cnf",environment.getProperty("maintenance.backup.defaults-file"));
        assertEquals("http://localhost:7777",environment.getProperty("maintenance.backup.monitor-url"));
        processor.apply(environment,key->"MAINTENANCE_BACKUP_DEFAULTS_FILE".equals(key)?"/new.cnf":values.get(key));
        assertEquals("/new.cnf",environment.getProperty("maintenance.backup.defaults-file"));
        var missing=new StandardEnvironment();processor.apply(missing,key->null);
        assertNull(missing.getPropertySources().get("backupDotEnv"));assertFalse(new BackupProperties().isEnabled());
    }

    /**
     * 检查正常启动注册资源，避免处理器未被Spring Boot发现而只在测试中生效。
     */
    @Test void registeredAtBoot() {
        assertTrue(SpringFactoriesLoader.loadFactoryNames(EnvironmentPostProcessor.class,getClass().getClassLoader()).contains(BackupEnvironmentPostProcessor.class.getName()));
    }
}
