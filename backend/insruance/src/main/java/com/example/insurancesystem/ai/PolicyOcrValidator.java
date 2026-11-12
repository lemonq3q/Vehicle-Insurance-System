package com.example.insurancesystem.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 对模型返回的三险资料执行完整结构和业务校验，并仅输出可安全回填的字段。
 * 每次遍历全部组和字段收集问题，整理模型一次收到完整错误清单；日期按中国时区转为现有 Unix 秒。
 */
@Component
public class PolicyOcrValidator {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
        .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
        .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern MONEY = Pattern.compile("\\d{1,10}(?:\\.\\d{1,2})?");
    private static final List<String> GROUPS = Arrays.asList("commercial", "compulsory", "nonMotor");
    private static final List<String> STANDARD_FIELDS = Arrays.asList("insurerName", "policyNo", "startTime", "endTime", "premiumAmount");
    private final ObjectMapper mapper;

    public PolicyOcrValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 解析模型文本并收集所有可判定的结构、格式和跨字段错误。
     * JSON 无法解析时保留完整语法错误供整理；可解析时遍历全部三组，不因首个错误提前返回。
     *
     * @param raw OCR 或整理模型的原始文本
     * @return 可安全回填的扁平字段、需人工关注的问题及整理所用的完整错误列表
     */
    public ValidationResult validate(String raw) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> values = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            errors.add("响应为空");
            return new ValidationResult(values, errors, warnings);
        }
        JsonNode root;
        try {
            String trimmed = raw.trim();
            if (trimmed.startsWith("```json") && trimmed.endsWith("```")) {
                trimmed = trimmed.substring(7, trimmed.length() - 3).trim();
            }
            root = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(trimmed);
        } catch (Exception ex) {
            errors.add("响应不是可解析的单个 JSON 对象：" + ex.getMessage());
            return new ValidationResult(values, errors, warnings);
        }
        if (!root.isObject()) {
            errors.add("顶层必须是 JSON 对象");
            return new ValidationResult(values, errors, warnings);
        }
        root.fieldNames().forEachRemaining(name -> {
            if (!GROUPS.contains(name)) errors.add("顶层存在未定义字段：" + name);
        });
        for (String group : GROUPS) {
            JsonNode node = root.get(group);
            if (node == null || !node.isObject()) {
                errors.add(group + " 必须是对象，缺失字段应为 null");
                continue;
            }
            validateGroup(group, node, values, errors, warnings);
        }
        if (values.isEmpty() && errors.isEmpty()) errors.add("三类保单均未识别到可回填字段");
        return new ValidationResult(values, errors, warnings);
    }

    /**
     * 检查某一险种的所有约定字段及起终期关系，并将通过校验的值映射到当前工单字段。
     * 日期只有年月日时按北京时间零点转换；每个字段独立校验，其他有效字段仍可返回。
     *
     * @param group 险种前缀
     * @param node 当前组 JSON 对象
     * @param values 输出的工单字段
     * @param errors 需要整理模型修正的完整问题列表
     * @param warnings 不可自动填入但可提示用户的信息
     */
    private void validateGroup(String group, JsonNode node, Map<String, Object> values,
                               List<String> errors, List<String> warnings) {
        List<String> expected = new ArrayList<>(STANDARD_FIELDS);
        if ("compulsory".equals(group)) expected.add("vehicleAndTaxAmount");
        if ("nonMotor".equals(group)) {
            expected.add("insuranceName");
            expected.add("coverageAmount");
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!expected.contains(name)) errors.add(group + "." + name + " 为未定义字段");
        }
        for (String field : expected) {
            JsonNode value = node.get(field);
            if (value == null) {
                errors.add(group + "." + field + " 缺失，应显式填 null");
                continue;
            }
            if (value.isNull()) continue;
            boolean amount = field.endsWith("Amount");
            if (!value.isTextual() && !(amount && value.isNumber())) {
                errors.add(group + "." + field + " 必须是字符串或 null");
                continue;
            }
            String text = value.asText().trim();
            if (amount && value.isNumber()) {
                errors.add(group + "." + field + " 应输出无单位的十进制字符串");
            }
            if (text.isEmpty()) {
                errors.add(group + "." + field + " 空字符串应使用 null");
                continue;
            }
            String target = targetName(group, field);
            if (field.endsWith("Time")) {
                validateTime(group, field, target, text, values, errors);
            } else if (amount) {
                String normalized = normalizeMoney(text);
                if (!normalized.equals(text)) errors.add(group + "." + field + " 应输出无单位、无千分位的金额字符串");
                text = normalized;
                if (!MONEY.matcher(text).matches() || new BigDecimal(text).compareTo(new BigDecimal("9999999999.99")) > 0) {
                    errors.add(group + "." + field + " 金额须为 0 至 9999999999.99 的十进制字符串，最多两位小数");
                } else {
                    values.put(target, new BigDecimal(text).setScale(2).toPlainString());
                }
            } else {
                int limit = "insurerName".equals(field) ? 200 : "policyNo".equals(field) ? 100 : 100;
                if (text.length() > limit) errors.add(group + "." + field + " 超过 " + limit + " 字符");
                else values.put(target, text);
            }
        }
        Object start = values.get(group + "InsuranceStartTime");
        Object end = values.get(group + "InsuranceEndTime");
        if (start instanceof Long && end instanceof Long && (Long) end <= (Long) start) {
            errors.add(group + " 保险终期必须晚于起期");
            values.remove(group + "InsuranceEndTime");
        }
    }

    /**
     * 将保单日期转换为承保表单使用的 Unix 秒；只有年月日时按北京时间当天零点存储，
     * 与日期选择器一致，不视为字段缺失；已有时分秒则保留原时间，不改为日末或增加一天。
     * 严格解析拒绝不存在的日历日期，无效值不进入回填结果，供上层整理重试。
     * @param group 保单所属险种，用于定位错误字段
     * @param field 模型返回的起期或终期字段名
     * @param target 前端承保表单的扁平字段名
     * @param text 经过空白清理的识别日期文本
     * @param values 收集可回填时间戳的结果映射
     * @param errors 收集非法日期信息，不中断其他字段校验
     */
    private void validateTime(String group, String field, String target, String text,
                              Map<String, Object> values, List<String> errors) {
        String normalized = text.replace('年', '-').replace('月', '-').replace("日", "")
            .replace('/', '-').replace('.', '-').replace('T', ' ').trim();
        String[] patterns = {"uuuu-M-d", "uuuu-M-d HH:mm:ss", "uuuu-M-d HH:mm"};
        for (String pattern : patterns) {
            try {
                DateTimeFormatter parser = DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
                LocalDateTime dateTime = pattern.equals("uuuu-M-d")
                    ? LocalDate.parse(normalized, parser).atStartOfDay() : LocalDateTime.parse(normalized, parser);
                values.put(target, dateTime.atZone(ZONE).toEpochSecond());
                if (!text.equals(dateTime.format(DATE)) && !text.equals(dateTime.format(DATE_TIME))) {
                    errors.add(group + "." + field + " 应输出 YYYY-MM-DD 或 YYYY-MM-DD HH:mm:ss 标准格式");
                }
                return;
            } catch (DateTimeException ignored) {
                // 尝试下一种明确日期模板，不进行宽松日期滚动或猜测时区。
            }
        }
        errors.add(group + "." + field + " 日期必须为有效的 YYYY-MM-DD 或 YYYY-MM-DD HH:mm:ss");
    }

    /**
     * 兼容明确的人民币金额展示格式，保留原小数精度交给后续范围校验。
     * 仅移除常见货币符号和单位；千分位须完整分组，拒绝错误逗号、负数和含糊文本。
     * @param text OCR 金额字符串
     * @return 可解析的十进制文本，无法安全归一化时原样返回以触发整理
     */
    private String normalizeMoney(String text) {
        String result = text.replaceFirst("^(?:人民币|RMB|CNY|[¥￥])\\s*", "")
            .replaceFirst("\\s*(?:元|圆)$", "").trim();
        if (result.matches("\\d{1,3}(?:[,，]\\d{3})+(?:[.]\\d+)?")) {
            result = result.replace(",", "").replace("，", "");
        }
        return result;
    }

    /**
     * 把模型的分组字段转换为当前工单 DTO 的扁平字段名。
     * 交强险车船税和非车险补充资料沿用已有独立字段，避免新增数据库字段。
     */
    private String targetName(String group, String field) {
        if ("vehicleAndTaxAmount".equals(field) || "insuranceName".equals(field) || "coverageAmount".equals(field)) {
            return "vehicleAndTaxAmount".equals(field) ? field : "nonMotor" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
        }
        if ("startTime".equals(field)) return group + "InsuranceStartTime";
        if ("endTime".equals(field)) return group + "InsuranceEndTime";
        if ("premiumAmount".equals(field)) return group + "Amount";
        return group + Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }

    /**
     * 保留所有本轮错误、可用字段及人工复核提示，供整理重试和最终回填使用。
     */
    public record ValidationResult(Map<String, Object> values, List<String> errors, List<String> warnings) {
        public boolean valid() { return errors.isEmpty(); }
    }
}
