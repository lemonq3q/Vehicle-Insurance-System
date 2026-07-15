package com.example.insurancesystem.utils;

import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * 系统编号插入重试工具。仅处理 MySQL 1062 且命中指定编号唯一约束的异常。
 */
public final class UniqueCodeRetryUtil {

    public static final String MERCHANT_CODE_CONSTRAINT = "uk_biz_merchant_enterprise_code";
    public static final String WORKORDER_CODE_CONSTRAINT = "uk_biz_workorder_enterprise_code";
    private static final int MAX_RETRY_COUNT = 10;
    private static final int MYSQL_DUPLICATE_KEY_ERROR_CODE = 1062;

    private UniqueCodeRetryUtil() {
    }

    public static int insertWithGeneratedCode(String constraintName,
                                              Consumer<String> codeSetter,
                                              IntSupplier insertAction) {
        DataIntegrityViolationException lastCodeConflict = null;
        for (int retryCount = 0; retryCount <= MAX_RETRY_COUNT; retryCount++) {
            codeSetter.accept(SystemCommonUtil.buildCode());
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
