package com.codegym.aiplanning.service.daily.ai;

/** Content-free diagnostics; retry text must never include provider or user content. */
public enum DailyPlanValidationReason {
    JSON_FORMAT("Return one complete JSON object with exactly the required fields."),
    ITEM_STRUCTURE("Include a nonempty summary, 1 to 50 valid items, and an adjustments array."),
    CURRICULUM_REFERENCE(
            "Use only supplied Learning Unit IDs; completed units may only be REVIEW."),
    DAILY_BUDGET("Reduce the sum of plannedMinutes to availableMinutes."),
    REVIEW_BUDGET(
            "Use at most one REVIEW task and no more than 30% of availableMinutes for review."),
    STEP_STRUCTURE(
            "Give every task 1 to 8 distinct steps, at least one required; step estimates must fit the task."),
    STEP_SCOPE(
            "Use a short exact phrase from the referenced Learning Unit title or description as scopeAnchor, also in the step title or guidance."),
    STEP_ANCHOR_MISSING(
            "Set a short, meaningful scopeAnchor from the referenced Learning Unit's anchorCandidates."),
    STEP_ANCHOR_OUTSIDE_UNIT(
            "Replace scopeAnchor with a phrase from the referenced Learning Unit, not its parent Topic."),
    STEP_ANCHOR_UNUSED(
            "Include scopeAnchor in the step title or guidance, or select another candidate already used there."),
    STEP_DECOMPOSITION(
            "Give each step a concrete action beyond repeating the task or Learning Unit title."),
    ADJUSTMENT(
            "Use valid adjustment actions, reasons, and only supplied unresolved task IDs."),
    OTHER("Follow all field, reference, step, and time constraints in the system instructions.");

    private final String retryInstruction;

    DailyPlanValidationReason(String retryInstruction) {
        this.retryInstruction = retryInstruction;
    }

    public String retryInstruction() {
        return retryInstruction;
    }

    public boolean isStepAnchorFailure() {
        return this == STEP_ANCHOR_MISSING
                || this == STEP_ANCHOR_OUTSIDE_UNIT
                || this == STEP_ANCHOR_UNUSED;
    }

    static DailyPlanValidationReason fromFixedMessage(String message) {
        if (message == null) {
            return OTHER;
        }
        if (message.contains("JSON")
                || message.contains("schema")
                || message.contains("fields do not match")) {
            return JSON_FORMAT;
        }
        if (message.contains("scope anchor") || message.contains("scopeAnchor")) {
            return STEP_SCOPE;
        }
        if (message.contains("repeat") || message.contains("rename")) {
            return STEP_DECOMPOSITION;
        }
        if (message.contains("Task Step") || message.contains("step.") || message.contains("step ")) {
            return STEP_STRUCTURE;
        }
        if (message.contains("30%") || message.contains("REVIEW item")) {
            return REVIEW_BUDGET;
        }
        if (message.contains("planned minutes exceed")) {
            return DAILY_BUDGET;
        }
        if (message.contains("Roadmap Item")
                || message.contains("active Roadmap")
                || message.contains("completed")) {
            return CURRICULUM_REFERENCE;
        }
        if (message.contains("adjustment") || message.contains("unresolved")) {
            return ADJUSTMENT;
        }
        if (message.contains("item.") || message.contains("items") || message.contains("summary")) {
            return ITEM_STRUCTURE;
        }
        return OTHER;
    }
}
