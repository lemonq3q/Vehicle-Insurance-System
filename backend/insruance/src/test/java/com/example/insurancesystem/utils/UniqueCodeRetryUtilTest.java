package com.example.insurancesystem.utils;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UniqueCodeRetryUtilTest {

    @Test
    void retriesOnlyWhenSpecifiedCodeConstraintConflicts() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<String> generatedCode = new AtomicReference<>();

        int result = UniqueCodeRetryUtil.insertWithGeneratedCode(
                UniqueCodeRetryUtil.WORKORDER_CODE_CONSTRAINT,
                generatedCode::set,
                () -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw duplicateKey(UniqueCodeRetryUtil.WORKORDER_CODE_CONSTRAINT);
                    }
                    return 1;
                });

        assertEquals(1, result);
        assertEquals(2, attempts.get());
        assertNotNull(generatedCode.get());
    }

    @Test
    void doesNotRetryOtherUniqueConstraintConflicts() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(DataIntegrityViolationException.class,
                () -> UniqueCodeRetryUtil.insertWithGeneratedCode(
                        UniqueCodeRetryUtil.WORKORDER_CODE_CONSTRAINT,
                        code -> { },
                        () -> {
                            attempts.incrementAndGet();
                            throw duplicateKey("uk_other_field");
                        }));

        assertEquals(1, attempts.get());
    }

    @Test
    void retriesAtMostTenTimesAfterInitialAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(DataIntegrityViolationException.class,
                () -> UniqueCodeRetryUtil.insertWithGeneratedCode(
                        UniqueCodeRetryUtil.MERCHANT_CODE_CONSTRAINT,
                        code -> { },
                        () -> {
                            attempts.incrementAndGet();
                            throw duplicateKey(UniqueCodeRetryUtil.MERCHANT_CODE_CONSTRAINT);
                        }));

        assertEquals(11, attempts.get());
    }

    private DataIntegrityViolationException duplicateKey(String constraintName) {
        SQLException cause = new SQLException(
                "Duplicate entry for key '" + constraintName + "'", "23000", 1062);
        return new DataIntegrityViolationException("duplicate", cause);
    }
}
