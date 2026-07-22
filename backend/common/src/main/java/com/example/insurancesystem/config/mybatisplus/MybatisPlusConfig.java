package com.example.insurancesystem.config.mybatisplus;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TableNameHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.example.insurancesystem.system.ArchiveContext;
import com.example.insurancesystem.security.EnterpriseContextHolder;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;
import java.util.Set;

@Configuration
public class MybatisPlusConfig {

    private static final Set<String> INSURANCE_TENANT_TABLES = Set.of(
            "tenant_member",
            "biz_insurance_product",
            "biz_merchant",
            "biz_merchant_area",
            "biz_merchant_staff",
            "biz_merchant_staff_role",
            "biz_ocr_record",
            "biz_vehicle_certificate",
            "biz_vehicle_invoice",
            "biz_vehicle_license",
            "biz_workorder",
            "biz_workorder_commission",
            "biz_workorder_file",
            "biz_workorder_insurance",
            "biz_workorder_logistics",
            "biz_workorder_payment",
            "biz_workorder_quote",
            "biz_workorder_underwriting",
            "sys_file"
    );

    @Value("${insurance.tenant-isolation.enabled:false}")
    private boolean tenantIsolationEnabled;

    /**
     * 自定义批量插入 SQL 注入器
     */
    @Bean
    public InsertBatchSqlInjector insertBatchSqlInjector() {
        return new InsertBatchSqlInjector();
    }

    /**
     * 注册 MyBatis-Plus 插件（包含动态表名）
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 动态表名插件
        DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor = new DynamicTableNameInnerInterceptor();

        // 表名处理器：你写的逻辑
        TableNameHandler tableNameHandler = (sql, tableName) -> {
            if (ArchiveContext.isArchive()) {
                return tableName + "_archive";
            }
            return tableName;
        };

        // 设置处理器
        dynamicTableNameInnerInterceptor.setTableNameHandler(tableNameHandler);
        interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);

        if (tenantIsolationEnabled) {
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new InsuranceTenantLineHandler()));
        }

        return interceptor;
    }

    private static class InsuranceTenantLineHandler implements com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler {

        @Override
        public LongValue getTenantId() {
            return new LongValue(EnterpriseContextHolder.requireEnterpriseId());
        }

        @Override
        public String getTenantIdColumn() {
            return "enterprise_id";
        }

        @Override
        public boolean ignoreTable(String tableName) {
            if (EnterpriseContextHolder.getEnterpriseId() == null) {
                return true;
            }
            String normalizedTableName = tableName.toLowerCase(Locale.ROOT);
            if (normalizedTableName.endsWith("_archive")) {
                normalizedTableName = normalizedTableName.substring(
                        0, normalizedTableName.length() - "_archive".length());
            }
            return !INSURANCE_TENANT_TABLES.contains(normalizedTableName);
        }
    }
}
