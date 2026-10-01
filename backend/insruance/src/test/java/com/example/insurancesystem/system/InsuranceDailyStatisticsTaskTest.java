package com.example.insurancesystem.system;

import com.example.insurancesystem.maintenance.MaintenanceTaskContext;
import com.example.insurancesystem.statistics.EnterpriseDailyStatisticsService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

/** 验证终算直接使用维护业务日期，不重复减一天；使用替身避免统计写入数据库。 */
class InsuranceDailyStatisticsTaskTest {
  /** 9月29日维护传入9月28日时，统计目标必须保持9月28日。 */
  @Test
  void forwardsBusinessDateWithoutSubtractingAnotherDay() throws Exception {
    EnterpriseDailyStatisticsService service = mock(EnterpriseDailyStatisticsService.class);
    LocalDate date = LocalDate.of(2026, 9, 28);
    new InsuranceMaintenanceTaskConfiguration().enterpriseDailyStatisticsTask(service).execute(
        new MaintenanceTaskContext("test", "insurance-enterprise-daily-statistics", date,
            Instant.now().plusSeconds(60), new AtomicBoolean(false)));
    verify(service).finalizeDay(date);
    verifyNoMoreInteractions(service);
  }
}
