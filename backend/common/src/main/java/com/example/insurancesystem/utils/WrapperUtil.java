package com.example.insurancesystem.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.apache.commons.lang3.StringUtils;


import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class WrapperUtil {
    /**
     * 反射获取实体类非空字段（过滤null/空串/特殊字段）
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
                // 排除特殊字段
                if (isExcludeField(field.getName())) {
                    continue;
                }

                field.setAccessible(true);
                Object fieldValue = field.get(entity);

                // 过滤null值（字符串额外过滤空串）
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

    private static <T> List<String> getAllBlurFields(List<List<String>> blurField) {
        if (blurField == null || blurField.isEmpty()) {
            return List.of();
        }
        // 扁平化嵌套列表，去重并返回所有模糊查询字段
        return blurField.stream()
                .filter(Objects::nonNull) // 过滤null的内层列表
                .flatMap(List::stream)    // 扁平化
                .filter(Objects::nonNull) // 过滤null的字段名
                .distinct()               // 去重
                .collect(Collectors.toList());
    }


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

    // ===================== 对外暴露的快捷方法（查询/更新/删除） =====================
    /**
     * 构建查询条件 QueryWrapper（默认eq条件）
     * @param entity 查询参数实体
     * @param <T> 泛型类型
     * @return QueryWrapper
     */
    public static <T> QueryWrapper<T> buildQueryWrapper(T entity) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        List<FieldValuePair<T>> validFields = getValidFields(entity);
        // 拼接eq查询条件
        validFields.forEach(pair -> wrapper.eq(true, pair.getFieldName(), pair.getFieldValue()));
        return wrapper;
    }

    /**
     * 构建带模糊查询的查询条件
     * @param entity 查询参数实体
     * @param blurField 使用and拼接的模糊查询字段
     * @return QueryWrapper
     * @param <T> 泛型类型
     */
    public static <T> QueryWrapper<T> buildQueryWrapperWithSingleBlurField(T entity, List<String> blurField) {
        return buildQueryWrapperWithMultiBlurFields(entity, Collections.singletonList(blurField));
    }

    /**
     *
     * @param entity 查询参数实体
     * @param blurFields 使用and拼接的模糊查询字段列表，列表内的条件使用or拼接
     * @return QueryWrapper
     * @param <T> 泛型类型
     */
    public static <T> QueryWrapper<T> buildQueryWrapperWithMultiBlurFields(T entity, List<List<String>> blurFields) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        List<FieldValuePair<T>> validFields = getValidFields(entity);

        List<List<FieldValuePair<T>>> blurValidFields = getBlurFieldValuePairByString(validFields, blurFields);

        // 提取所有需要模糊查询的字段，放入一个集合中（方便后续排除）
        List<String> allBlurFields = getAllBlurFields(blurFields);

        // 拼接eq条件，但排除模糊查询的字段
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

    // 重载方法：简化调用（默认参数
    /**
     * 简化更新条件构建：默认用id作为where条件，更新所有非空字段
     */
    public static <T> UpdateWrapper<T> buildUpdateWrapper(T entity) {
        return buildUpdateWrapper(entity, null);
    }

    /**
     * 构建更新条件 UpdateWrapper
     * @param entity 更新参数实体（需包含主键字段，如id，作为where条件）
     * @param whereFieldNames 指定作为where条件的字段（null则默认主键字段：id）
     * @param <T> 泛型类型
     * @return UpdateWrapper
     */
    public static <T> UpdateWrapper<T> buildUpdateWrapper(T entity, List<String> whereFieldNames) {
        UpdateWrapper<T> wrapper = new UpdateWrapper<>();
        List<FieldValuePair<T>> validFields = getValidFields(entity);

        // 1. 处理where条件（默认用id作为where条件）
        List<String> finalWhereFields = (whereFieldNames == null || whereFieldNames.isEmpty())
                ? List.of("id")
                : whereFieldNames;
        validFields.stream()
                .filter(pair -> finalWhereFields.contains(pair.getFieldName()))
                .forEach(pair -> wrapper.eq(true, pair.getFieldName(), pair.getFieldValue()));

        // 2. 处理set更新条件（所有非where字段）
        validFields.stream()
                .filter(pair -> !finalWhereFields.contains(pair.getFieldName())) // 排除where字段
                .forEach(pair -> wrapper.set(true, pair.getFieldName(), pair.getFieldValue()));

        // 校验：where条件不能为空，避免全表更新
        if (wrapper.getExpression().getNormal().isEmpty()) {
            throw new IllegalArgumentException("更新条件不能为空！请确保实体包含where字段（如id）且值非空");
        }
        return wrapper;
    }

    /**
     * 构建删除条件 QueryWrapper（默认eq条件，逻辑同查询）
     * @param entity 删除参数实体（需包含主键字段，如id）
     * @param <T> 泛型类型
     * @return QueryWrapper
     */
    public static <T> QueryWrapper<T> buildDeleteWrapper(T entity) {
        // 删除条件和查询条件逻辑一致，直接复用查询Wrapper构建逻辑
        QueryWrapper<T> wrapper = buildQueryWrapper(entity);
        // 校验：删除条件不能为空，避免全表删除
        if (wrapper.getExpression().getNormal().isEmpty()) {
            throw new IllegalArgumentException("删除条件不能为空！请确保实体包含非空的条件字段（如id）");
        }
        return wrapper;
    }

    /**
     * 递归获取类的所有字段（包括父类）
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
     * 判断是否为需要排除的特殊字段
     */
    private static boolean isExcludeField(String fieldName) {
        return "serialVersionUID".equals(fieldName);
    }

    /**
     * 判断字段值是否为null或空串
     */
    private static boolean isNullOrEmptyValue(Object fieldValue) {
        if (fieldValue == null) {
            return true;
        }
        if (fieldValue instanceof String) {
            return StringUtils.isEmpty((String) fieldValue);
        }
        // 可扩展：处理集合/数组等类型的空值判断
        return false;
    }

    private static class FieldValuePair<T> {
        private final String fieldName;
        private final Object fieldValue;

        public FieldValuePair(String fieldName, Object fieldValue) {
            this.fieldName = camelToUnderline(fieldName);
            this.fieldValue = fieldValue;
        }

        public String getFieldName() {
            return fieldName;
        }

        public Object getFieldValue() {
            return fieldValue;
        }

        public static String camelToUnderline(String camelCaseStr) {
            // 空值判断
            if (camelCaseStr == null || camelCaseStr.isEmpty()) {
                return camelCaseStr;
            }

            // 定义正则：匹配除首字符外的大写字母
            String regex = "(?<!^)([A-Z])";
            String replacement = "_$1";

            // 替换大写字母为_+对应小写字母
            String result = camelCaseStr.replaceAll(regex, replacement).toLowerCase();

            return result;
        }

    }


}
