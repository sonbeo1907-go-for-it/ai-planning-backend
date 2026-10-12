package com.codegym.aiplanning.service.billing;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;
import java.util.Locale;

public final class BillingValidation {
    public static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private BillingValidation() {}

    public static String category(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_-]{1,50}")) {
            throw invalid("Nhóm giá chỉ gồm chữ, số, dấu gạch ngang hoặc gạch dưới.");
        }
        return normalized;
    }

    public static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }

    public static void version(long actual, long expected) {
        if (actual != expected) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                    "Dữ liệu đã thay đổi. Hãy tải lại trước khi lưu.");
        }
    }

    public static long safeSum(long first, long second) {
        try {
            long result = Math.addExact(first, second);
            if (result < 0 || result > MAX_SAFE_INTEGER) {
                throw invalid("Số Credit nằm ngoài giới hạn an toàn.");
            }
            return result;
        } catch (ArithmeticException exception) {
            throw invalid("Số Credit nằm ngoài giới hạn an toàn.");
        }
    }
}
