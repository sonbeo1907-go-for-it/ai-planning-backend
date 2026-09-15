package com.codegym.aiplanning.service.daily.ai;

/**
 * Provider-facing action categories used to make generated Task Steps observable.
 * The value is validation metadata and is not stored as personal learning content.
 */
public enum AiTaskStepAction {
    WRITE,
    IMPLEMENT,
    COMPARE,
    EXPLAIN,
    SOLVE,
    RUN,
    READ,
    REVIEW,
    SUMMARIZE
}
