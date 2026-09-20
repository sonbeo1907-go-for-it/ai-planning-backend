package com.codegym.aiplanning.service.daily.ai;

import java.util.List;

public class InvalidAiDailyPlanResponseException extends RuntimeException {

    private final DailyPlanValidationReason reason;
    private final List<StepLocation> stepLocations;

    public InvalidAiDailyPlanResponseException(String message) {
        this(message, DailyPlanValidationReason.fromFixedMessage(message), List.of());
    }

    public InvalidAiDailyPlanResponseException(
            String message, DailyPlanValidationReason reason) {
        this(message, reason, List.of());
    }

    public InvalidAiDailyPlanResponseException(
            String message,
            DailyPlanValidationReason reason,
            List<StepLocation> stepLocations) {
        super(message);
        this.reason = reason;
        this.stepLocations = List.copyOf(stepLocations);
    }

    public DailyPlanValidationReason reason() {
        return reason;
    }

    public List<StepLocation> stepLocations() {
        return stepLocations;
    }

    public record StepLocation(int itemIndex, int stepIndex, DailyPlanValidationReason reason) {}
}
