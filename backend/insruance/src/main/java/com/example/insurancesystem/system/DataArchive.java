package com.example.insurancesystem.system;

import com.example.insurancesystem.utils.OSSUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DataArchive {
    private static final List<String> ARCHIVE_TABLES = List.of(
            "tenant_user", "tenant_enterprise", "tenant_member",
            "auth_role", "auth_permission", "auth_role_permission",
            "biz_merchant", "biz_merchant_area", "biz_merchant_staff", "biz_merchant_staff_role",
            "biz_insurance_product", "sys_file", "biz_workorder", "biz_workorder_quote",
            "biz_workorder_commission", "biz_workorder_payment", "biz_workorder_underwriting",
            "biz_workorder_logistics", "biz_vehicle_license", "biz_vehicle_invoice",
            "biz_vehicle_certificate", "biz_workorder_insurance", "biz_workorder_file"
    );

    private final JdbcTemplate jdbcTemplate;

    public DataArchive(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void archive() {
        expiredFileClean();
        ARCHIVE_TABLES.forEach(this::archiveTable);
    }

    public void expiredFileClean() {
        List<Map<String, Object>> files = jdbcTemplate.queryForList(
                "SELECT id,path FROM sys_file WHERE deleted=0 AND is_linked=0 AND updated_at<DATE_SUB(NOW(),INTERVAL 3 DAY)");
        for (Map<String, Object> file : files) {
            try {
                OSSUtil.deleteFile(String.valueOf(file.get("path")));
                jdbcTemplate.update("UPDATE sys_file SET deleted=1 WHERE id=? AND deleted=0", file.get("id"));
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    private void archiveTable(String sourceTable) {
        String archiveTable = sourceTable + "_archive";
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT s.column_name FROM information_schema.columns s " +
                        "JOIN information_schema.columns a ON a.table_schema=s.table_schema " +
                        "AND a.table_name=? AND a.column_name=s.column_name " +
                        "WHERE s.table_schema=DATABASE() AND s.table_name=? ORDER BY s.ordinal_position",
                String.class, archiveTable, sourceTable);
        if (columns.isEmpty()) return;
        String columnSql = columns.stream().map(column -> "`" + column + "`").collect(Collectors.joining(","));
        jdbcTemplate.update("INSERT INTO `" + archiveTable + "`(" + columnSql + ") SELECT " + columnSql +
                " FROM `" + sourceTable + "` WHERE deleted=1");
        jdbcTemplate.update("DELETE FROM `" + sourceTable + "` WHERE deleted=1");
    }
}
