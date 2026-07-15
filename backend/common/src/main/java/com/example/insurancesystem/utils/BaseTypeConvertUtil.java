package com.example.insurancesystem.utils;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaseTypeConvertUtil {

    // 预编译正则表达式（提升性能，避免重复编译）
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    public static Integer safeParseInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof String) {
                return Integer.parseInt((String) value);
            } else if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (NumberFormatException e) {
            // 转换失败时返回null
            return null;
        }
        return null;
    }

    /**
     * 安全转换为金额类型（BigDecimal）
     * 兼容：null、字符串（带千分位/人民币符号）、数字类型（Integer/Long/Double等）
     * 转换失败返回null，避免抛出异常
     * @param value 待转换的输入值
     * @return 金额BigDecimal，失败返回null
     */
    public static BigDecimal safeParseAmount(Object value) {
        // 1. 处理null值
        if (value == null) {
            return null;
        }

        try {
            // 2. 处理字符串类型（核心场景）
            if (value instanceof String) {
                String str = ((String) value).trim();
                // 空字符串直接返回null
                if (str.isEmpty()) {
                    return null;
                }
                // 清理常见的金额格式干扰符（人民币符号、千分位逗号）
                String cleanStr = str.replaceAll("¥", "")  // 移除人民币符号
                        .replaceAll(",", "")  // 移除千分位逗号
                        .replaceAll(" ", ""); // 移除空格
                // 转换为BigDecimal（严格数字格式）
                return new BigDecimal(cleanStr);
            }
            // 3. 处理数字类型（Integer/Long/Double/Float等）
            else if (value instanceof Number) {
                // 优先用String中转，避免Double转BigDecimal的精度问题（如0.1d转BigDecimal会失真）
                return new BigDecimal(value.toString());
            }
        } catch (NumberFormatException e) {
            // 数字格式错误（如"abc123"、"123.45.67"），返回null
            return null;
        }

        // 4. 非字符串/数字类型（如Boolean/Object等），返回null
        return null;
    }

    /**
     * 提取字符串中首个连续的数字字符串
     * @param inputStr 输入的任意字符串
     * @return 首个连续数字字符串；无数字/输入为空时返回null
     */
    public static String extractFirstNumberString(String inputStr) {
        // 1. 处理空值和空字符串
        if (inputStr == null || inputStr.trim().isEmpty()) {
            return null;
        }

        // 2. 匹配首个连续数字序列
        Matcher matcher = NUMBER_PATTERN.matcher(inputStr);
        if (matcher.find()) {
            // 返回第一个匹配到的数字字符串
            return matcher.group();
        }
        // 3. 无匹配的数字时返回null
        return null;
    }

    public static void main(String[] args) {
        System.out.println(extractFirstNumberString("123abc456"));
    }
}
