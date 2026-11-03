package com.example.insurancesystem;

import com.example.insurancesystem.utils.RenewalCalendar;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证双险种共用日历规则的月末、闰年、首轮边界及提前处理另一险种行为，不依赖数据库时区。
 */
class RenewalCalendarTest {
    /** 年度起期提前一个日历月，严格覆盖需求示例与二月截断。 */
    @Test
    void reminderDatesFollowCalendarMonths() {
        assertEquals(LocalDate.of(2025, 1, 29), RenewalCalendar.reminderDate(LocalDate.of(2024, 2, 29), 1));
        assertEquals(LocalDate.of(2027, 9, 29), RenewalCalendar.reminderDate(LocalDate.of(2026, 10, 29), 1));
        assertEquals(LocalDate.of(2027, 2, 28), RenewalCalendar.reminderDate(LocalDate.of(2026, 3, 31), 1));
        assertEquals(LocalDate.of(2028, 2, 29), RenewalCalendar.reminderDate(LocalDate.of(2027, 3, 31), 1));
        assertEquals(LocalDate.of(2028, 2, 29), RenewalCalendar.reminderDate(LocalDate.of(2026, 3, 31), 2));
    }

    /** 提醒前一天无任务，提醒当天开始，逾期持续，下一年度自然进入新周期。 */
    @Test
    void activeCycleIsInclusiveAndRepeatsAnnually() {
        LocalDate start = LocalDate.of(2026, 10, 29);
        assertEquals(0, RenewalCalendar.activeCycle(start, LocalDate.of(2027, 9, 28)));
        assertEquals(1, RenewalCalendar.activeCycle(start, LocalDate.of(2027, 9, 29)));
        assertEquals(1, RenewalCalendar.activeCycle(start, LocalDate.of(2028, 9, 28)));
        assertEquals(2, RenewalCalendar.activeCycle(start, LocalDate.of(2028, 9, 29)));
        assertEquals(0, RenewalCalendar.activeCycle(null, LocalDate.of(2027, 9, 29)));
    }

    /** 商业先提醒而交强未提醒时，全部完成仍应标记交强首轮，不能等交强到期再重复提醒。 */
    @Test
    void allCompletionIncludesNotYetStartedCounterpart() {
        LocalDate today = LocalDate.of(2027, 9, 29);
        LocalDate compulsory = LocalDate.of(2026, 12, 1);
        assertEquals(0, RenewalCalendar.activeCycle(compulsory, today));
        assertEquals(1, RenewalCalendar.upcomingCycle(compulsory, today));
        assertEquals(1, RenewalCalendar.activeCycle(compulsory, LocalDate.of(2027, 11, 1)));
        assertEquals(2, RenewalCalendar.activeCycle(compulsory, LocalDate.of(2028, 11, 1)));
    }

    /** 一月起期的提醒落在上一年十二月，不能仅按当前年份判定周期。 */
    @Test
    void januaryAnniversaryRemindsInPreviousDecember() {
        LocalDate start = LocalDate.of(2026, 1, 31);
        assertEquals(0, RenewalCalendar.activeCycle(start, LocalDate.of(2026, 12, 30)));
        assertEquals(1, RenewalCalendar.activeCycle(start, LocalDate.of(2026, 12, 31)));
    }
}
