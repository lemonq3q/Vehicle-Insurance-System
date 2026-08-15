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
/**
 * 组合 MyBatis-Plus 批量写入、归档表路由和可配置的车险租户隔离插件。
 * 插件顺序先替换归档表名，再按当前登录企业注入 enterprise_id 条件，使在线表与归档表使用同一租户边界。
 */
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
     * 注册自定义批量插入 SQL 注入器，为继承 BatchBaseMapper 的 Mapper 提供 insertBatchSomeColumn。
     */
    @Bean
    public InsertBatchSqlInjector insertBatchSqlInjector() {
        return new InsertBatchSqlInjector();
    }

    /**
     * 注册 MyBatis-Plus 内部插件。ArchiveContext 开启时把业务表透明改写为同名 _archive 表；
     * 租户隔离配置启用时，再为白名单业务表的查询和写入注入当前企业条件。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor = new DynamicTableNameInnerInterceptor();

        /*
         * 归档上下文由维护任务在当前线程显式开启；常规请求保持原表名，归档读取或写入才路由到 _archive 表。
         */
        TableNameHandler tableNameHandler = (sql, tableName) -> {
            if (ArchiveContext.isArchive()) {
                return tableName + "_archive";
            }
            return tableName;
        };

        dynamicTableNameInnerInterceptor.setTableNameHandler(tableNameHandler);
        interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);

        if (tenantIsolationEnabled) {
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new InsuranceTenantLineHandler()));
        }

        return interceptor;
    }

    /**
     * 车险业务租户处理器仅作用于明确列出的企业业务表。未建立企业认证上下文的内部任务忽略租户插件，
     * 因而这类任务必须自行按企业范围处理数据；普通登录请求则统一使用 enterprise_id 约束。
     */
    private static class InsuranceTenantLineHandler implements com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler {

        @Override
        /**
         * 将当前认证企业 ID 构造成 SQL 数字表达式；缺少企业上下文时由 requireEnterpriseId 阻止执行。
         */
        public LongValue getTenantId() {
            return new LongValue(EnterpriseContextHolder.requireEnterpriseId());
        }

        @Override
        /**
         * 指定所有受隔离车险表共用的租户列名 enterprise_id。
         */
        public String getTenantIdColumn() {
            return "enterprise_id";
        }

        @Override
        /**
         * 判断表是否跳过自动租户条件。内部无企业上下文时全部跳过；有上下文时移除归档后缀再与白名单比较，
         * 因此在线表和对应归档表都能获得相同隔离条件，而系统公共表不会被错误添加不存在的列。
         */
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
