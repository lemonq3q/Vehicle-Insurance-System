package com.example.insurancesystem.utils;

import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 系统编号插入重试工具。仅处理 MySQL 1062 且命中指定编号唯一约束的异常。
 */
public final class UniqueCodeRetryUtil {

    public static final String MERCHANT_CODE_CONSTRAINT = "uk_biz_merchant_enterprise_code";
    public static final String WORKORDER_CODE_CONSTRAINT = "uk_biz_workorder_enterprise_code";
    private static final int MAX_RETRY_COUNT = 10;
    private static final int MYSQL_DUPLICATE_KEY_ERROR_CODE = 1062;

    /**
     * 静态重试工具不保存状态，禁止实例化。
     */
    private UniqueCodeRetryUtil() {
    }

    /**
     * 使用系统通用业务编号生成器执行插入重试，适合商户和工单等统一编号格式。
     */
    public static int insertWithGeneratedCode(String constraintName,
                                              Consumer<String> codeSetter,
                                              IntSupplier insertAction) {
        return insertWithGeneratedCode(
                constraintName, SystemCommonUtil::buildCode, codeSetter, insertAction);
    }

    /**
     * 每次尝试先生成并写入新编号，再执行数据库插入。只有 MySQL 1062 且命中调用方指定唯一约束时才重试，
     * 其他完整性异常立即原样抛出；超过十次仍冲突则抛出最后一次编号冲突，避免无限循环掩盖故障。
     */
    public static int insertWithGeneratedCode(String constraintName,
                                              Supplier<String> codeGenerator,
                                              Consumer<String> codeSetter,
                                              IntSupplier insertAction) {
        DataIntegrityViolationException lastCodeConflict = null;
        for (int retryCount = 0; retryCount <= MAX_RETRY_COUNT; retryCount++) {
            codeSetter.accept(codeGenerator.get());
            try {
                return insertAction.getAsInt();
            } catch (DataIntegrityViolationException exception) {
                if (!isSpecifiedCodeConflict(exception, constraintName)) {
                    throw exception;
                }
                lastCodeConflict = exception;
            }
        }
        throw lastCodeConflict;
    }

    /**
     * 沿异常原因链查找 MySQL 重复键错误，并核对错误消息中的唯一约束名，确保重试只针对生成编号碰撞，
     * 不会把手机号、外键或其他唯一字段冲突误认为可恢复问题。
     */
    static boolean isSpecifiedCodeConflict(Throwable throwable, String constraintName) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException) {
                SQLException sqlException = (SQLException) current;
                if (sqlException.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR_CODE
                        && sqlException.getMessage() != null
                        && sqlException.getMessage().contains(constraintName)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
