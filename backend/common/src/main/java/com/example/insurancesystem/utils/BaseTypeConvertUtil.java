package com.example.insurancesystem.utils;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对 OCR 和通用 Map 中类型不稳定的值执行容错基础类型转换，失败以 null 表示而不打断证件识别流程。
 */
public class BaseTypeConvertUtil {

    // 预编译正则表达式（提升性能，避免重复编译）
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    /**
     * 将整数文本或任意 Number 转为 Integer；空值、格式错误和不支持类型返回 null。
     */
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
            return null;
        }
        return null;
    }

    /**
     * 将带人民币符号、千分位或空格的字符串以及常见 Number 转换为 BigDecimal。
     * Number 先经字符串转换以避免直接从 double 构造产生二进制精度尾差；非法金额返回 null。
     * @param value 待转换的输入值
     * @return 金额BigDecimal，失败返回null
     */
    public static BigDecimal safeParseAmount(Object value) {
        if (value == null) {
            return null;
        }

        try {
            if (value instanceof String) {
                String str = ((String) value).trim();
                if (str.isEmpty()) {
                    return null;
                }
                String cleanStr = str.replaceAll("¥", "")
                        .replaceAll(",", "")
                        .replaceAll(" ", "");
                return new BigDecimal(cleanStr);
            }
            else if (value instanceof Number) {
                return new BigDecimal(value.toString());
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    /**
     * 使用预编译正则从混合文本中提取第一段连续数字，例如 OCR 的“核定载客5人”返回“5”。
     * @param inputStr 输入的任意字符串
     * @return 首个连续数字字符串；无数字/输入为空时返回null
     */
    public static String extractFirstNumberString(String inputStr) {
        if (inputStr == null || inputStr.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = NUMBER_PATTERN.matcher(inputStr);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    /**
     * 本地调试数字提取规则的独立入口，不参与应用业务链路。
     */
    public static void main(String[] args) {
        System.out.println(extractFirstNumberString("123abc456"));
    }
}
