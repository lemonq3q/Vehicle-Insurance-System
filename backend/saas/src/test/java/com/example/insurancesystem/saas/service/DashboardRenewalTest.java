package com.example.insurancesystem.saas.service;

import com.example.insurancesystem.utils.RenewalCalendar;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 门户续保提醒与工单真实起期、独立跟进周期适配的回归。
 * 不使用创建日，不连接数据库；非车险按本次约定不参与提醒。
 */
class DashboardRenewalTest {
    /**
     * 将中国业务日期转成主表使用的 Unix 秒，避免测试依赖运行机时区。
     */
    private long seconds(LocalDate date) { return date.atStartOfDay(RenewalCalendar.BUSINESS_ZONE).toEpochSecond(); }

    /**

     * 年度窗口按自然日期计算，已处理本周期不重复计入，下年度仍可提醒。

     */
    @Test
    void usesPolicyStartAndIndependentHandledCycle() {
        long start=seconds(LocalDate.of(2025,11,15));
        LocalDate today=LocalDate.of(2026,11,8);
        assertTrue(DashboardStatisticsService.needsRenewal(start,0,0,today,7));
        assertFalse(DashboardStatisticsService.needsRenewal(start,1,1,today,30));
        assertTrue(DashboardStatisticsService.needsRenewal(start,1,1,today.plusYears(1),30));
        assertFalse(DashboardStatisticsService.needsRenewal(null,0,0,today,30));
    }

    /**

     * 闰日起期在非闰年周年落于2月末，7天边界日包含，不使用固定365天。

     */
    @Test
    void respectsLeapYearAndWindowBoundaries() {
        long start=seconds(LocalDate.of(2024,2,29));
        assertTrue(DashboardStatisticsService.needsRenewal(start,0,0,LocalDate.of(2025,2,21),7));
        assertFalse(DashboardStatisticsService.needsRenewal(start,0,0,LocalDate.of(2025,2,20),7));
    }

    /** 缩短测试周期后，门户按同一月数计算下一轮到期日和七天窗口。 */
    @Test
    void monthlyConfigurationChangesPortalWindow() {
        RenewalCalendar.configureCycleMonths(1);
        try {
            long start = seconds(LocalDate.of(2026, 8, 20));
            LocalDate today = LocalDate.of(2026, 9, 15);
            assertTrue(DashboardStatisticsService.needsRenewal(start, 0, 0, today, 7));
            assertFalse(DashboardStatisticsService.needsRenewal(start, 1, 1, today, 7));
        } finally {
            RenewalCalendar.configureCycleMonths(12);
        }
    }
}

