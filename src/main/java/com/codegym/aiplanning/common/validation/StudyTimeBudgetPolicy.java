package com.codegym.aiplanning.common.validation;

import com.codegym.aiplanning.common.exception.BusinessException;
import com.codegym.aiplanning.common.exception.ErrorCode;

/** Shared validation policy for user-entered daily study-time budgets. */
public final class StudyTimeBudgetPolicy {

    public static final int MIN_MINUTES = 15;
    public static final int MAX_MINUTES = 480;
    public static final int INCREMENT_MINUTES = 15;
    public static final int SYSTEM_FALLBACK_MINUTES = 60;

    private StudyTimeBudgetPolicy() {}

    public static int requireValid(Integer minutes, String fieldLabel) {
        if (minutes == null
                || minutes < MIN_MINUTES
                || minutes > MAX_MINUTES
                || minutes % INCREMENT_MINUTES != 0) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    fieldLabel + " must be between 15 and 480 minutes in 15-minute increments.");
        }
        return minutes;
    }

    public static void validateIfPresent(Integer minutes, String fieldLabel) {
        if (minutes != null) {
            requireValid(minutes, fieldLabel);
        }
    }
}
