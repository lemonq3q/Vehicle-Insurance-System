package com.example.insurancesystem.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.apache.commons.lang3.StringUtils;


import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 通过反射把非空查询 DTO 或实体字段转换为 MyBatis-Plus QueryWrapper/UpdateWrapper，
 * 统一驼峰到下划线映射、精确与模糊条件分组，并对更新和删除执行空条件保护。
 */
public class WrapperUtil {
    /**
     * 递归读取实体及父类字段，排除序列化常量、null 和空字符串，并转换为数据库列名和值。
     * 无法访问字段时抛出带字段名的异常，避免静默遗漏查询条件。
     * @param entity 实体对象
     * @param <T> 泛型类型
     * @return 非空字段列表（字段名+字段值）
     */
    private static <T> List<FieldValuePair<T>> getValidFields(T entity) {
        List<FieldValuePair<T>> validFields = new ArrayList<>();
        if (entity == null) {
            return validFields;
        }

        Class<T> clazz = (Class<T>) entity.getClass();
        List<Field> allFields = getAllFields(clazz, new ArrayList<>());

        for (Field field : allFields) {
            try {
                if (isExcludeField(field.getName())) {
                    continue;
                }

                field.setAccessible(true);
                Object fieldValue = field.get(entity);

                if (isNullOrEmptyValue(fieldValue)) {
                    continue;
                }

                validFields.add(new FieldValuePair<>(field.getName(), fieldValue));
            } catch (IllegalAccessException e) {
                throw new RuntimeException("反射获取字段值失败：字段名=" + field.getName(), e);
            }
        }
        return validFields;
    }

    /**
     * 将多个模糊查询字段组扁平化并去重，结果用于从精确 eq 条件中排除所有将参与 LIKE 的字段。
     */
    private static <T> List<String> getAllBlurFields(List<List<String>> blurField) {
        if (blurField == null || blurField.isEmpty()) {
            return List.of();
        }
        return blurField.stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }


    /**
     * 将调用方声明的模糊字段名分组映射为实际存在且有值的字段对，忽略空组、未知字段和空值。
     * 每个返回内层列表随后生成一组括号包围的 OR LIKE 条件，不同组之间由 AND 连接。
     */
    private static <T> List<List<FieldValuePair<T>>> getBlurFieldValuePairByString(List<FieldValuePair<T>> validFields, List<List<String>> blurField){
        if (validFields == null || validFields.isEmpty() || blurField == null || blurField.isEmpty()) {
            return List.of();
        }

        return blurField.stream()
                .filter(Objects::nonNull)
                .map(innerFieldList -> {
                    if (innerFieldList == null || innerFieldList.isEmpty()) {
                        return List.<FieldValuePair<T>>of();
                    }
                    return innerFieldList.stream()
                            .filter(Objects::nonNull)
                            .map(fieldName -> validFields.stream()
                                    .filter(pair -> fieldName.equals((pair.getFieldName())))
                                    .findFirst()
                                    .orElse(null))
                            .filter(Objects::nonNull)
                            .collect(Collectors.<FieldValuePair<T>>toList());
                })
                .filter(innerPairList -> !innerPairList.isEmpty())
                .collect(Collectors.<List<FieldValuePair<T>>>toList());
    }

    /**
     * 将实体全部有效字段构造成 AND 连接的精确 eq 查询条件；空实体返回无条件 Wrapper。
     * @param entity 查询参数实体
     * @param <T> 泛型类型
     * @return QueryWrapper
     */
    public static <T> QueryWrapper<T> buildQueryWrapper(T entity) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        List<FieldValuePair<T>> validFields = getValidFields(entity);
        validFields.forEach(pair -> wrapper.eq(true, pair.getFieldName(), pair.getFieldValue()));
        return wrapper;
    }

    /**
     * 构建一组模糊字段的查询条件，内部转为多组入口，使该组字段以 OR LIKE 连接并与其他精确条件 AND 连接。
     * @param entity 查询参数实体
     * @param blurField 使用and拼接的模糊查询字段
     * @return QueryWrapper
     * @param <T> 泛型类型
     */
    public static <T> QueryWrapper<T> buildQueryWrapperWithSingleBlurField(T entity, List<String> blurField) {
        return buildQueryWrapperWithMultiBlurFields(entity, Collections.singletonList(blurField));
    }

    /**
     * 将实体有效字段拆分为精确条件和模糊条件：未列入 blurFields 的字段使用 eq；
     * 每个内层字段组使用括号内 OR LIKE，不同内层组以及精确条件之间使用 AND。
     * @param entity 查询参数实体
     * @param blurFields 使用and拼接的模糊查询字段列表，列表内的条件使用or拼接
     * @return QueryWrapper
     * @param <T> 泛型类型
     */
    public static <T> QueryWrapper<T> buildQueryWrapperWithMultiBlurFields(T entity, List<List<String>> blurFields) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        List<FieldValuePair<T>> validFields = getValidFields(entity);

        List<List<FieldValuePair<T>>> blurValidFields = getBlurFieldValuePairByString(validFields, blurFields);

        List<String> allBlurFields = getAllBlurFields(blurFields);

        validFields = validFields.stream()
                .filter(field -> !allBlurFields.contains(field.fieldName))
                .collect(Collectors.toList());
        validFields.forEach(pair -> wrapper.eq(true, pair.getFieldName(), pair.getFieldValue()));


        blurValidFields.forEach(innerFieldList -> {
            if (innerFieldList == null || innerFieldList.isEmpty()) {
                return;
            }

            wrapper.and(andWrapper -> {
                for (int i = 0; i < innerFieldList.size(); i++) {
                    String fieldName = innerFieldList.get(i).getFieldName();
                    Object fieldValue = innerFieldList.get(i).getFieldValue();
                    if (fieldValue == null) {
                        continue;
                    }
                    if (i == 0) {
                        andWrapper.like(true, fieldName, fieldValue);
                    } else {
                        andWrapper.or().like(true, fieldName, fieldValue);
                    }
                }
            });
        });

        return wrapper;
    }

    /**
     * 使用 id 作为默认 WHERE 字段构造更新 Wrapper，其余非空字段全部作为 SET 值。
     */
    public static <T> UpdateWrapper<T> buildUpdateWrapper(T entity) {
        return buildUpdateWrapper(entity, null);
    }

    /**
     * 按指定字段构造 WHERE，其他有效字段构造 SET；未指定时默认使用 id。
     * 若实体中没有任何有效 WHERE 字段立即拒绝，防止反射式更新意外影响全表。
     * @param entity 更新参数实体（需包含主键字段，如id，作为where条件）
     * @param whereFieldNames 指定作为where条件的字段（null则默认主键字段：id）
     * @param <T> 泛型类型
     * @return UpdateWrapper
     */
    public static <T> UpdateWrapper<T> buildUpdateWrapper(T entity, List<String> whereFieldNames) {
        UpdateWrapper<T> wrapper = new UpdateWrapper<>();
        List<FieldValuePair<T>> validFields = getValidFields(entity);

        List<String> finalWhereFields = (whereFieldNames == null || whereFieldNames.isEmpty())
                ? List.of("id")
                : whereFieldNames;
        validFields.stream()
                .filter(pair -> finalWhereFields.contains(pair.getFieldName()))
                .forEach(pair -> wrapper.eq(true, pair.getFieldName(), pair.getFieldValue()));

        validFields.stream()
                .filter(pair -> !finalWhereFields.contains(pair.getFieldName()))
                .forEach(pair -> wrapper.set(true, pair.getFieldName(), pair.getFieldValue()));

        if (wrapper.getExpression().getNormal().isEmpty()) {
            throw new IllegalArgumentException("更新条件不能为空！请确保实体包含where字段（如id）且值非空");
        }
        return wrapper;
    }

    /**
     * 复用精确查询规则构造删除条件，并在条件为空时拒绝执行，防止调用方传空实体导致全表删除。
     * @param entity 删除参数实体（需包含主键字段，如id）
     * @param <T> 泛型类型
     * @return QueryWrapper
     */
    public static <T> QueryWrapper<T> buildDeleteWrapper(T entity) {
        QueryWrapper<T> wrapper = buildQueryWrapper(entity);
        if (wrapper.getExpression().getNormal().isEmpty()) {
            throw new IllegalArgumentException("删除条件不能为空！请确保实体包含非空的条件字段（如id）");
        }
        return wrapper;
    }

    /**
     * 从当前类向上递归收集所有声明字段，直到 Object 为止，使 DTO 继承的分页或公共字段也能参与条件提取。
     */
    private static <T> List<Field> getAllFields(Class<T> clazz, List<Field> fields) {
        fields.addAll(List.of(clazz.getDeclaredFields()));
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            getAllFields((Class<T>) superClass, fields);
        }
        return fields;
    }

    /**
     * 判断字段是否为不应进入 SQL 的 Java 序列化元数据，目前排除 serialVersionUID。
     */
    private static boolean isExcludeField(String fieldName) {
        return "serialVersionUID".equals(fieldName);
    }

    /**
     * 判断字段是否没有有效查询值：null 和空字符串被忽略，数字零和 false 等合法值必须保留。
     */
    private static boolean isNullOrEmptyValue(Object fieldValue) {
        if (fieldValue == null) {
            return true;
        }
        if (fieldValue instanceof String) {
            return StringUtils.isEmpty((String) fieldValue);
        }
        return false;
    }

    /**
     * 保存转换后的数据库列名及字段值，隔离反射字段表示与 Wrapper 条件生成逻辑。
     */
    private static class FieldValuePair<T> {
        private final String fieldName;
        private final Object fieldValue;

        /**
         * 创建字段值对时立即将 Java 驼峰字段名转换为数据库下划线列名。
         */
        public FieldValuePair(String fieldName, Object fieldValue) {
            this.fieldName = camelToUnderline(fieldName);
            this.fieldValue = fieldValue;
        }

        /**
         * 返回已转换的数据库列名。
         */
        public String getFieldName() {
            return fieldName;
        }

        /**
         * 返回反射读取的原始字段值。
         */
        public Object getFieldValue() {
            return fieldValue;
        }

        /**
         * 将非首位大写字母替换为下划线加小写形式，例如 enterpriseId 转为 enterprise_id；空值原样返回。
         */
        public static String camelToUnderline(String camelCaseStr) {
            if (camelCaseStr == null || camelCaseStr.isEmpty()) {
                return camelCaseStr;
            }

            String regex = "(?<!^)([A-Z])";
            String replacement = "_$1";

            String result = camelCaseStr.replaceAll(regex, replacement).toLowerCase();

            return result;
        }

    }


}
