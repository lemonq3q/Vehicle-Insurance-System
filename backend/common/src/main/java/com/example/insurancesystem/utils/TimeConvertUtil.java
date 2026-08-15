package com.example.insurancesystem.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 在 OCR 常见中文、分隔符和 ISO 日期文本与系统统一 Unix 秒级时间戳之间转换，默认业务时区为上海。
 */
public class TimeConvertUtil {

    private static final List<DateTimeFormatter> COMMON_FORMATTERS;
    static {
        COMMON_FORMATTERS = new ArrayList<>();
        /*
         * 格式集合同时覆盖纯日期、带分钟/秒的日期时间和 ISO 文本，按常见程度依次尝试。
         */
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyyMMdd"));

        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分ss秒"));

        COMMON_FORMATTERS.add(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        COMMON_FORMATTERS.add(DateTimeFormatter.ISO_LOCAL_DATE);

    }

    /**
     * 依次尝试所有已支持格式并转换为指定时区下的 Unix 秒。先按 LocalDateTime 解析，失败再按 LocalDate 解析并补零点；
     * 空文本或全部格式不匹配返回 null，自定义 zoneId 为空时使用 Asia/Shanghai。
     * @param dateStr 任意格式的日期字符串（含中文格式）
     * @param zoneId 时区（默认Asia/Shanghai）
     * @return 秒级时间戳，解析失败返回null
     */
    public static Long autoParseToTimestamp(String dateStr, String zoneId) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        String trimDate = dateStr.trim();
        if (zoneId == null || zoneId.trim().isEmpty()) {
            zoneId = "Asia/Shanghai";
        }
        ZoneId zone = ZoneId.of(zoneId);

        for (DateTimeFormatter formatter : COMMON_FORMATTERS) {
            try {
                LocalDateTime localDateTime = LocalDateTime.parse(trimDate, formatter);
                Instant instant = localDateTime.atZone(zone).toInstant();
                return instant.getEpochSecond();
            } catch (DateTimeParseException e1) {
                try {
                    LocalDate localDate = LocalDate.parse(trimDate, formatter);
                    LocalDateTime localDateTime = LocalDateTime.of(localDate, LocalTime.MIDNIGHT);
                    Instant instant = localDateTime.atZone(zone).toInstant();
                    return instant.getEpochSecond();
                } catch (DateTimeParseException e2) {
                    continue;
                }
            }
        }
        return null;
    }

    /**
     * 使用默认上海时区自动解析日期文本的便捷入口。
     */
    public static Long autoParseToTimestamp(String dateStr) {
        return autoParseToTimestamp(dateStr, null);
    }


    /**
     * 将秒级时间戳按默认 yyyy-MM-dd HH:mm:ss 格式和上海时区输出。
     */
    public static String timestampConvert(long timestamp) {
        return timestampConvert(timestamp, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 将秒级时间戳按调用方格式和上海业务时区输出可读文本。
     */
    public static String timestampConvert(long timestamp, String format) {
        Instant instant = Instant.ofEpochSecond(timestamp);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format)
                .withZone(ZoneId.of("Asia/Shanghai"));
        return formatter.format(instant);
    }

    /**
     * 本地调试日期自动识别的独立入口，不参与应用运行。
     */
    public static void main(String[] args) {
        String dateStr = "2021-01-01";
        System.out.println(TimeConvertUtil.autoParseToTimestamp(dateStr));
    }
}
