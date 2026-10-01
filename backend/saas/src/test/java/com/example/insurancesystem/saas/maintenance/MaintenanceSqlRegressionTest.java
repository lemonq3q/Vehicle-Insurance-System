package com.example.insurancesystem.saas.maintenance;

import com.example.insurancesystem.saas.mapper.InviteMaintenanceMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证维护 SQL 送往数据库前的真实文本，防止 XML 实体或反斜杠转义再次破坏任务。
 * 使用 MyBatis 解析和 JDBC 替身，不执行邀请码更新、数据搬移或删除。
 */
class MaintenanceSqlRegressionTest {
  /** 普通注解必须产生实际比较符号，而不是将 XML 实体原样发送给 MySQL。 */
  @Test
  void inviteSqlContainsRealOperators() {
    Configuration configuration = new Configuration();
    configuration.addMapper(InviteMaintenanceMapper.class);
    String sql = configuration.getMappedStatement(InviteMaintenanceMapper.class.getName()
        + ".deleteExpiredOrExhaustedInvites").getBoundSql(null).getSql();
    assertFalse(sql.contains("&lt;"));
    assertFalse(sql.contains("&gt;"));
    assertTrue(sql.contains("created_at<DATE_SUB"));
    assertTrue(sql.contains("used_count>=max_use_count"));
  }

  /** 元数据查询使用固定后缀排除归档表，空候选时不执行任何数据写操作。 */
  @Test
  void archiveDiscoveryAvoidsEscapeClause() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
    assertEquals(0, new SaasDataArchiveService(jdbc).archiveDeletedData());
    org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(jdbc).queryForList(sql.capture(), eq(String.class));
    assertTrue(sql.getValue().contains("RIGHT(source.table_name,8)<>'_archive'"));
    assertFalse(sql.getValue().contains("ESCAPE"));
    verifyNoMoreInteractions(jdbc);
  }
}
