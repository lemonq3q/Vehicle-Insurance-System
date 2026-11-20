package com.example.insurancesystem.saas.maintenance;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaasDataArchiveService {
  private final JdbcTemplate jdbcTemplate;

  public SaasDataArchiveService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public int archiveDeletedData() {
    int archived = 0;
    for (String sourceTable : archiveSources()) {
      archived += archiveTable(sourceTable);
    }
    return archived;
  }

  /**
   * 查找当前库中含逻辑删除字段且存在对应归档表的源表，不扫描归档表本身。
   * 使用字面后缀判断，避免 Java/MySQL 双重反斜杠转义及 SQL 模式影响查询。
   * @return 按表名排序的待归档源表名称；本步骤仅查询元数据，不搬移数据
   */
  private List<String> archiveSources() {
    return jdbcTemplate.queryForList(
        "SELECT DISTINCT source.table_name FROM information_schema.columns source "
            + "JOIN information_schema.tables archive_table ON archive_table.table_schema=source.table_schema "
            + "AND archive_table.table_name=CONCAT(source.table_name,'_archive') "
            + "WHERE source.table_schema=DATABASE() AND source.column_name='deleted' "
            + "AND RIGHT(source.table_name,8)<>'_archive' ORDER BY source.table_name",
        String.class);
  }

  /**
   * 将源表逻辑删除记录完整复制后移除；生成列由归档表维护，不显式写入。
   * @param sourceTable 从当前库元数据发现的源表名称
   * @return 实际复制的记录数；普通字段无法完整写入时抛出异常，由入口事务回滚
   */
  private int archiveTable(String sourceTable) {
    String archiveTable = sourceTable + "_archive";
    List<String> columns = jdbcTemplate.queryForList(
        "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name=? AND COALESCE(generation_expression,'')='' ORDER BY ordinal_position",
        String.class, sourceTable);
    List<String> writableColumns = jdbcTemplate.queryForList(
        "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name=? AND COALESCE(generation_expression,'')='' ORDER BY ordinal_position",
        String.class, archiveTable);
    List<String> missingColumns = columns.stream()
        .filter(column -> !writableColumns.contains(column)).collect(Collectors.toList());
    /*
     * 普通字段必须完整写入，目标同名生成列无法承接原值也视为不兼容。
     * 在复制和删除前报错，交由入口事务回滚，避免静默丢失历史字段。
     */
    if (columns.isEmpty() || !missingColumns.isEmpty()) {
      throw new IllegalStateException("归档表结构不兼容：" + sourceTable + " -> " + archiveTable
          + "，缺少可写字段：" + missingColumns);
    }
    String columnSql =
        columns.stream().map(column -> "`" + column + "`").collect(Collectors.joining(","));
    int inserted =
        jdbcTemplate.update(
            "INSERT INTO `"
                + archiveTable
                + "` ("
                + columnSql
                + ") SELECT "
                + columnSql
                + " FROM `"
                + sourceTable
                + "` WHERE deleted=1");
    jdbcTemplate.update("DELETE FROM `" + sourceTable + "` WHERE deleted=1");
    return inserted;
  }
}
