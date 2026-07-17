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

  private List<String> archiveSources() {
    return jdbcTemplate.queryForList(
        "SELECT DISTINCT source.table_name FROM information_schema.columns source "
            + "JOIN information_schema.tables archive_table ON archive_table.table_schema=source.table_schema "
            + "AND archive_table.table_name=CONCAT(source.table_name,'_archive') "
            + "WHERE source.table_schema=DATABASE() AND source.column_name='deleted' "
            + "AND source.table_name NOT LIKE '%\\_archive' ESCAPE '\\' ORDER BY source.table_name",
        String.class);
  }

  private int archiveTable(String sourceTable) {
    String archiveTable = sourceTable + "_archive";
    List<String> columns =
        jdbcTemplate.queryForList(
            "SELECT source.column_name FROM information_schema.columns source "
                + "JOIN information_schema.columns archive_column ON archive_column.table_schema=source.table_schema "
                + "AND archive_column.table_name=? AND archive_column.column_name=source.column_name "
                + "WHERE source.table_schema=DATABASE() AND source.table_name=? ORDER BY source.ordinal_position",
            String.class,
            archiveTable,
            sourceTable);
    if (columns.isEmpty()) return 0;
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
