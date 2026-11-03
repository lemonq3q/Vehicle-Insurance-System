package com.example.insurancesystem.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 商业险和交强险共用的日历续保规则，以中国日期和各自起期为锚点。
 * 每个年度独立从原始起期推算，避免闰年与月末截断造成累计偏移；不维护状态或访问数据库。
 */
public final class RenewalCalendar {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private RenewalCalendar() { }

    /**
     * 将后端 Unix 秒起期转换为中国业务日期，无起期时不猜测创建日。
     * @param seconds 起期秒数，可为空。
     * @return 日期或 null。
     */
    public static LocalDate startDate(Long seconds) {
        return seconds == null ? null : Instant.ofEpochSecond(seconds).atZone(BUSINESS_ZONE).toLocalDate();
    }

    /**
     * 从原起期一次平移 cycle*12-1 个月，月末只在目标月截断。
     * 例如闰日起期2024-02-29首轮提醒是2025-01-29，避免先加年导致日期提前截为28日。
     * @param start 保险起期。
     * @param cycle 从 1 开始的年度周期。
     * @return 提醒日，例如 2026-03-31 的第 1 次提醒为 2027-02-28。
     */
    public static LocalDate reminderDate(LocalDate start, int cycle) {
        return start.plusMonths(12L * cycle - 1);
    }

    /**
     * 找出截至今天最近已开始的提醒周期，未到首个提醒日返回 0。
     * @param start 起期，允许为空。
     * @param today 中国业务日期。
     * @return 周期序号，供列表状态和已完成标记比较。
     */
    public static int activeCycle(LocalDate start, LocalDate today) {
        if (start == null) return 0;
        int cycle = Math.max(1, today.getYear() - start.getYear() + 1);
        while (cycle > 0 && reminderDate(start, cycle).isAfter(today)) cycle--;
        return cycle;
    }

    /**
     * 全部完成时，也允许提前处理另一险种本轮尚未开始的提醒。
     * 以尚未过去的最近周年确定待续保轮次；已到提醒日且周年未过时仍属于同一轮。
     * @param start 该险种起期，无起期不生成周期。
     * @param today 当前中国日期。
     * @return 本轮或即将开始的周期，不关闭下一年度永久提醒。
     */
    public static int upcomingCycle(LocalDate start, LocalDate today) {
        if (start == null) return 0;
        int cycle = Math.max(1, today.getYear() - start.getYear());
        if (start.plusYears(cycle).isBefore(today)) cycle++;
        return cycle;
    }
}
