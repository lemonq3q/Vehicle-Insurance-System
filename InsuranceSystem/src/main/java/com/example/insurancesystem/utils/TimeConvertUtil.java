package com.example.insurancesystem.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class TimeConvertUtil {

    private static final List<DateTimeFormatter> COMMON_FORMATTERS;
    static {
        COMMON_FORMATTERS = new ArrayList<>();
        // 基础日期格式
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 日期+时间（英文分隔，带秒/不带秒）
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分"));
        COMMON_FORMATTERS.add(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH时mm分ss秒"));

        // 带时区的ISO格式
        COMMON_FORMATTERS.add(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        COMMON_FORMATTERS.add(DateTimeFormatter.ISO_LOCAL_DATE);

    }

    /**
     * 自动识别格式并转换为秒级时间戳
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
            zoneId = "Asia/Shanghai"; // 默认东八区
        }
        ZoneId zone = ZoneId.of(zoneId);

        // 遍历所有格式，逐一尝试解析
        for (DateTimeFormatter formatter : COMMON_FORMATTERS) {
            try {
                // 先尝试解析为LocalDateTime（带时间的格式）
                LocalDateTime localDateTime = LocalDateTime.parse(trimDate, formatter);
                Instant instant = localDateTime.atZone(zone).toInstant();
                return instant.getEpochSecond();
            } catch (DateTimeParseException e1) {
                try {
                    // 若解析LocalDateTime失败，尝试解析为LocalDate（纯日期），并补充默认时间（00:00:00）
                    LocalDate localDate = LocalDate.parse(trimDate, formatter);
                    LocalDateTime localDateTime = LocalDateTime.of(localDate, LocalTime.MIDNIGHT);
                    Instant instant = localDateTime.atZone(zone).toInstant();
                    return instant.getEpochSecond();
                } catch (DateTimeParseException e2) {
                    // 该格式不匹配，继续下一个
                    continue;
                }
            }
        }
        return null;
    }

    // 重载方法：使用默认时区（Asia/Shanghai）
    public static Long autoParseToTimestamp(String dateStr) {
        return autoParseToTimestamp(dateStr, null);
    }


    public static String timestampConvert(long timestamp) {
        return timestampConvert(timestamp, "yyyy-MM-dd HH:mm:ss");
    }

    public static String timestampConvert(long timestamp, String format) {
        Instant instant = Instant.ofEpochSecond(timestamp);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format)
                .withZone(ZoneId.of("Asia/Shanghai"));
        return formatter.format(instant);
    }

    public static void main(String[] args) {
        String dateStr = "2021-01-01";
        System.out.println(TimeConvertUtil.autoParseToTimestamp(dateStr));
    }
}
