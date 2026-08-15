package com.example.insurancesystem.system;

import com.example.insurancesystem.utils.OSSUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
/**
 * 执行车险业务的物理归档与无主附件清理。
 * 已逻辑删除的数据先复制到结构匹配的归档表，再从在线表移除，以控制业务表体量并保留历史记录。
 */
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

    /**
     * 使用 JDBC 执行跨多张业务表的通用归档 SQL。
     */
    public DataArchive(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    /**
     * 先清理超过保留期且未关联业务的 OSS 文件，再依次归档所有配置表中的逻辑删除记录。
     */
    public void archive() {
        expiredFileClean();
        ARCHIVE_TABLES.forEach(this::archiveTable);
    }

    /**
     * 删除三天前上传但始终未关联业务的文件。只有 OSS 删除成功后才把数据库记录标记删除，
     * 单个文件失败不会阻断其他文件及后续归档任务。
     */
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

    /**
     * 根据源表和归档表共有列动态生成迁移语句，兼容两张表在演进过程中存在字段差异。
     */
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
