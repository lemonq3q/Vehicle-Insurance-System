package com.example.insurancesystem.coordinator.config;

import com.example.insurancesystem.utils.SystemCommonUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 在配置绑定和定时任务注册前，将现有.env中的备份部署变量映射到Spring属性。
 * 只处理备份配置，不导入其他凭证或改变业务模块；没有配置的项继续使用原有默认值。
 * 沿用公共工具的文件查找规则及.env优先语义，文件配置同时作用于扫描间隔占位符。
 */
public class BackupEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    /**
     * 启动期读取公共.env配置，不连接数据库或OSS；该环境随后用于创建备份Bean和定时任务。
     * @param environment 当前启动环境
     * @param application 当前Spring应用
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        apply(environment, SystemCommonUtil::getOptionalConfig);
    }

    /**
     * 为每项备份配置同时接受易读的下划线变量名和Spring紧凑变量名，前者优先。
     * 只加入已配置值，避免缺失变量覆盖默认值；类型非法交由既有Spring绑定明确报错。
     * @param environment 待补充的环境
     * @param lookup 沿用公共配置读取器，测试可替换为临时文件中的属性
     */
    void apply(ConfigurableEnvironment environment, Function<String,String> lookup) {
        Map<String,Object> values = new LinkedHashMap<>();
        for (String property : new String[]{"enabled", "root", "database", "instance", "bucket", "prefix",
                "defaults-file", "mysql", "mysqldump", "mysqlbinlog", "source-data-option", "monitor-url",
                "timeout-seconds", "retention-days", "binlog-interval-ms"}) {
            String readable = "MAINTENANCE_BACKUP_" + property.toUpperCase(java.util.Locale.ROOT).replace('-', '_');
            String compact = readable.substring(0, "MAINTENANCE_BACKUP_".length())
                + property.toUpperCase(java.util.Locale.ROOT).replace("-", "");
            String value = lookup.apply(readable);
            if (value == null || value.isBlank()) value = lookup.apply(compact);
            if (value != null && !value.isBlank()) values.put("maintenance.backup." + property, value.trim());
        }
        if (!values.isEmpty()) environment.getPropertySources().addFirst(new MapPropertySource("backupDotEnv", values));
    }

    /**
     * 在标准配置文件处理完成后应用.env覆盖，确保绑定和扫描间隔使用相同的最终配置。
     * @return 靠后的环境处理优先级
     */
    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
