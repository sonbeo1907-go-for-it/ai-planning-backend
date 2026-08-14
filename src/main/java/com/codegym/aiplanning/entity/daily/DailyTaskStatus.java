package com.codegym.aiplanning.entity.daily;

public enum DailyTaskStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    PARTIALLY_COMPLETED,
    SKIPPED;

    public int completionPercentage() {
        return switch (this) {
            case COMPLETED -> 100;
            case PARTIALLY_COMPLETED -> 50;
            case NOT_STARTED, IN_PROGRESS, SKIPPED -> 0;
        };
    }
}
