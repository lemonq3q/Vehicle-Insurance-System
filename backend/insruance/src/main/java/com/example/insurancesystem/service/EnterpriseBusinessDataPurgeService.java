package com.example.insurancesystem.service;

import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.utils.OSSUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 清空指定企业在车险在线库和对象存储中产生的全部业务资料。
 * 清理范围仅包括企业私有的上下游商户、商户人员、工单及阶段明细、车辆资料、OCR 记录、
 * 文件元数据和 OSS 对象。企业险种目录 biz_insurance_product 属于可重复录单的基础配置，
 * 清理后仍须保留；工单险种明细 biz_workorder_insurance 则随所属工单一同归档，避免孤儿记录。
 * 租户成员、用户、订阅、余额、订单等 SaaS 域数据不在本服务的表清单中。
 *
 * <p>该能力只能在分布式维护窗口内由内部接口调用。OSS 无法参与数据库事务，因此先逐个删除对象；
 * 只有全部对象删除成功后，才在单个数据库事务中把在线数据完整迁移到对应归档表并从在线表移除；
 * 归档表本身永不清空。重复调用不会重复迁移在线数据，从而覆盖内部请求响应丢失后的幂等重试。</p>
 */
@Service
public class EnterpriseBusinessDataPurgeService {
    /**
     * 数据迁移采用从工单关联明细到主体、从商户人员到商户、最后到文件元数据的固定顺序。
     * 每个在线表都必须存在同名 _archive 表；当前表没有声明级联外键，但该顺序可兼容后续增加约束。
     */
    private static final List<String> ARCHIVE_TABLES = List.of(
            "biz_workorder_file", "biz_workorder_insurance",
            "biz_vehicle_license", "biz_vehicle_invoice", "biz_vehicle_certificate",
            "biz_workorder_quote", "biz_workorder_commission", "biz_workorder_payment",
            "biz_workorder_underwriting", "biz_workorder_logistics", "biz_workorder",
            "biz_merchant_staff_role", "biz_merchant_area", "biz_merchant_staff", "biz_merchant",
            "biz_ocr_record", "sys_file"
    );

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * @param jdbcTemplate 查询企业 OSS 对象并迁移限定 enterprise_id 的在线业务数据
     * @param transactionTemplate 将全部归档迁移纳入同一事务，任一 SQL 失败时整体回滚
     */
    public EnterpriseBusinessDataPurgeService(
            JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 清除企业车险资料。企业主键必须为正数；首先合并在线及归档文件表中的对象键并删除 OSS 对象，
     * 任一对象存储操作失败都会终止数据库归档，以便下次维护安全重试。全部对象处理成功后，按照固定表顺序
     * 在事务内将在线数据迁移到归档表，并返回各表实际迁移数量及 OSS 对象数量。
     *
     * @param enterpriseId SaaS 维护任务判定已超过保留期的企业主键
     * @return 清理统计；重复调用时在线表迁移数量可为零
     * @throws BusinessException 企业主键非法或 OSS 对象未能安全删除时抛出
     */
    public Map<String, Integer> purge(Long enterpriseId) {
        if (enterpriseId == null || enterpriseId <= 0) {
            throw new BusinessException(400, "enterpriseId 参数不正确");
        }

        /*
         * 文件对象必须依次调用阿里云 OSS 接口真实删除。查询同时覆盖在线表和既有归档表，确保企业更早
         * 归档的附件也被清除；UNION 对路径去重，空路径不调用对象存储但元数据仍会被保留或归档。
         */
        List<String> objectNames = jdbcTemplate.queryForList(
                "SELECT path FROM sys_file WHERE enterprise_id=? AND path IS NOT NULL AND path<>'' "
                        + "UNION SELECT path FROM sys_file_archive WHERE enterprise_id=? AND path IS NOT NULL AND path<>''",
                String.class, enterpriseId, enterpriseId);
        for (String objectName : objectNames) {
            if (!OSSUtil.deleteFile(objectName)) {
                throw new BusinessException(502, "企业文件删除失败，数据库资料未清除，可在下次维护重试");
            }
        }

        /*
         * 所有表名来自固定白名单，企业主键始终作为参数绑定。每张表先复制到归档表，再从在线表删除；
         * 归档表中原有数据不参与 DELETE。若中途发生异常，TransactionTemplate 会回滚本轮全部数据库变更。
         */
        Map<String, Integer> archivedCounts = transactionTemplate.execute(status -> {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String table : ARCHIVE_TABLES) {
                counts.put(table, archiveEnterpriseRows(table, enterpriseId));
            }
            return counts;
        });
        Map<String, Integer> result = archivedCounts == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(archivedCounts);
        result.put("ossObjects", objectNames.size());
        return result;
    }

    /**
     * 将单张在线表中属于企业的全部记录复制到对应归档表，然后从在线表移除。
     * 字段清单从 information_schema 读取两表共有的普通列，排除归档表扩展字段和 MySQL 生成列，
     * 使商户角色等包含生成索引键、归档批次字段的表也能安全迁移。插入或删除任一步失败都会抛出异常，
     * 由外层事务回滚所有已经处理的表。
     *
     * @param sourceTable 固定白名单中的车险在线表名
     * @param enterpriseId 要迁移的企业主键
     * @return 从在线表迁移到归档表的记录数量
     */
    private int archiveEnterpriseRows(String sourceTable, Long enterpriseId) {
        String archiveTable = sourceTable + "_archive";
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT s.column_name FROM information_schema.columns s "
                        + "JOIN information_schema.columns a ON a.table_schema=s.table_schema "
                        + "AND a.table_name=? AND a.column_name=s.column_name "
                        + "WHERE s.table_schema=DATABASE() AND s.table_name=? "
                        + "AND s.extra NOT LIKE '%GENERATED%' AND a.extra NOT LIKE '%GENERATED%' "
                        + "ORDER BY s.ordinal_position",
                String.class, archiveTable, sourceTable);
        if (columns.isEmpty()) {
            throw new IllegalStateException("归档表不存在或与在线表无共有字段: " + sourceTable);
        }
        String columnSql = columns.stream()
                .map(column -> "`" + column + "`")
                .collect(Collectors.joining(","));
        int archived = jdbcTemplate.update(
                "INSERT INTO `" + archiveTable + "`(" + columnSql + ") SELECT " + columnSql
                        + " FROM `" + sourceTable + "` WHERE enterprise_id=?", enterpriseId);
        int removed = jdbcTemplate.update(
                "DELETE FROM `" + sourceTable + "` WHERE enterprise_id=?", enterpriseId);
        if (archived != removed) {
            throw new IllegalStateException("企业资料归档数量不一致: " + sourceTable);
        }
        return archived;
    }
}
